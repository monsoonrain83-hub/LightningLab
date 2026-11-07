package com.lightninglab.atmospheric.knowledge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only, pre-populated offline knowledge database.
 *
 * The database is copied from app/src/main/assets/knowledge on first use.
 * It intentionally does not create or silently mutate scientific records at runtime.
 */
class AtmosphericKnowledgeDatabase private constructor(context: Context) : AutoCloseable {
    private val database: SQLiteDatabase

    init {
        val appContext = context.applicationContext
        val target = appContext.getDatabasePath(DB_NAME)
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "$DB_NAME.tmp")
            appContext.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not install atmospheric knowledge database" }
        }
        database = SQLiteDatabase.openDatabase(
            target.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    fun search(query: String, limit: Int = 30): List<PhenomenonRecord> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val pattern = "%$term%"
        val sql = """
            SELECT code, name_en, name_fa, category, summary_fa, visual_cues_fa, caveats_fa
            FROM phenomena
            WHERE name_en LIKE ? OR name_fa LIKE ?
               OR code LIKE ? OR summary_fa LIKE ?
            ORDER BY CASE WHEN code = ? THEN 0 ELSE 1 END, name_fa
            LIMIT ?
        """.trimIndent()
        return database.rawQuery(
            sql,
            arrayOf(pattern, pattern, pattern, pattern, term, limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    PhenomenonRecord(
                        code = cursor.getString(0),
                        nameEnglish = cursor.getString(1),
                        namePersian = cursor.getString(2),
                        category = cursor.getString(3),
                        summaryPersian = cursor.getString(4),
                        visualCuesPersian = cursor.getString(5),
                        caveatsPersian = cursor.getString(6)
                    )
                )
            }
        }
    }

    fun getPhenomenon(code: String): PhenomenonRecord? {
        return database.rawQuery(
            """SELECT code,name_en,name_fa,category,summary_fa,visual_cues_fa,caveats_fa
               FROM phenomena WHERE code = ? COLLATE NOCASE LIMIT 1""",
            arrayOf(code.trim())
        ).use { c ->
            if (!c.moveToFirst()) null else PhenomenonRecord(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getString(5), c.getString(6)
            )
        }
    }

    fun getCloudProfile(code: String): CloudProfile? {
        val sql = """
            SELECT p.code,p.name_en,p.name_fa,g.abbreviation,g.family,
                   g.typical_base_fa,g.vertical_extent_fa,g.formation_fa,g.weather_fa
            FROM phenomena p JOIN cloud_genera g ON g.phenomenon_id=p.id
            WHERE g.abbreviation = ? COLLATE NOCASE
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(code.trim())).use { c ->
            if (!c.moveToFirst()) null else CloudProfile(
                code = c.getString(0),
                nameEnglish = c.getString(1),
                namePersian = c.getString(2),
                abbreviation = c.getString(3),
                family = c.getString(4),
                typicalBasePersian = c.getString(5),
                verticalExtentPersian = c.getString(6),
                formationPersian = c.getString(7),
                weatherPersian = c.getString(8)
            )
        }
    }

    fun getTaxonomyEntry(code: String, taxonomy: String = "species"): TaxonomyEntry? {
        val table = when (taxonomy) {
            "species" -> "cloud_species"
            "feature" -> "cloud_features"
            "accessory" -> "cloud_accessory_clouds"
            "variety" -> "cloud_varieties"
            else -> return null
        }
        return database.rawQuery(
            "SELECT code, name_en, name_fa, description_fa FROM $table WHERE lower(code)=? LIMIT 1",
            arrayOf(code.trim().lowercase())
        ).use { c ->
            if (c.moveToFirst()) TaxonomyEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3)) else null
        }
    }

    fun getSpeciesForGenus(code: String): List<TaxonomyEntry> =
        taxonomyQuery(
            "SELECT code,name_en,name_fa,description_fa FROM cloud_species WHERE (',' || genus_codes || ',') LIKE ? ORDER BY name_en",
            arrayOf("%,${code.trim()},%")
        )

    fun getVarieties(): List<TaxonomyEntry> =
        taxonomyQuery("SELECT code,name_en,name_fa,description_fa FROM cloud_varieties ORDER BY name_en", emptyArray())

    fun getFeatures(): List<TaxonomyEntry> =
        taxonomyQuery("SELECT code,name_en,name_fa,description_fa FROM cloud_features ORDER BY name_en", emptyArray())

    fun getAccessoryClouds(): List<TaxonomyEntry> =
        taxonomyQuery("SELECT code,name_en,name_fa,description_fa FROM cloud_accessory_clouds ORDER BY name_en", emptyArray())

    fun getFormationConditions(code: String): List<KnowledgeNote> =
        noteQuery(
            "SELECT condition_fa,mechanism_fa,confidence_note_fa FROM formation_conditions WHERE phenomenon_code = ? ORDER BY id",
            code
        )

    fun getAltitudeNotes(code: String): List<AltitudeNote> =
        database.rawQuery(
            """SELECT layer,range_note_fa,numeric_min_m,numeric_max_m,qualification_fa
               FROM altitude_profiles WHERE phenomenon_code = ? ORDER BY id""",
            arrayOf(code)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    AltitudeNote(
                        layer = c.getString(0),
                        rangePersian = c.getString(1),
                        minimumMeters = if (c.isNull(2)) null else c.getDouble(2),
                        maximumMeters = if (c.isNull(3)) null else c.getDouble(3),
                        qualificationPersian = c.getString(4)
                    )
                )
            }
        }

    fun getImpacts(code: String): List<ImpactNote> =
        database.rawQuery(
            """SELECT impact_type,description_fa,likelihood_note_fa
               FROM impacts WHERE phenomenon_code = ? ORDER BY id""",
            arrayOf(code)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(ImpactNote(c.getString(0), c.getString(1), c.getString(2)))
            }
        }

    fun getReferenceImages(code: String): List<ReferenceImageRecord> =
        database.rawQuery(
            """SELECT asset_path,thumbnail_path,caption_fa,license,attribution,verified,status
               FROM reference_images WHERE phenomenon_code = ? ORDER BY id""",
            arrayOf(code)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ReferenceImageRecord(
                        assetPath = c.getString(0),
                        thumbnailPath = c.getString(1),
                        captionPersian = c.getString(2),
                        license = c.getString(3),
                        attribution = c.getString(4),
                        verified = c.getInt(5) == 1,
                        status = c.getString(6)
                    )
                )
            }
        }

    private fun taxonomyQuery(sql: String, args: Array<String>): List<TaxonomyEntry> =
        database.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) add(TaxonomyEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
            }
        }

    private fun noteQuery(sql: String, code: String): List<KnowledgeNote> =
        database.rawQuery(sql, arrayOf(code)).use { c ->
            buildList {
                while (c.moveToNext()) add(KnowledgeNote(c.getString(0), c.getString(1), c.getString(2)))
            }
        }

    override fun close() = database.close()

    companion object {
        private const val DB_NAME = "AtmosphericKnowledge-v2.3.db"
        private const val ASSET_PATH = "knowledge/AtmosphericKnowledge.db"

        @Volatile private var instance: AtmosphericKnowledgeDatabase? = null

        fun getInstance(context: Context): AtmosphericKnowledgeDatabase =
            instance ?: synchronized(this) {
                instance ?: AtmosphericKnowledgeDatabase(context).also { instance = it }
            }
    }
}
