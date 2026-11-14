package com.lightninglab.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.lightninglab.VideoDraft
import com.lightninglab.VideoDraftStore
import java.io.File

/**
 * Existing video autosave storage behind a real repository boundary.
 * Not a no-op: read/save/delete/overlay delegate to the already shipped
 * AtomicFile-backed VideoDraftStore implementation.
 *
 * Future A03 transaction and migration tests can substitute an implementation
 * without rewriting MainVM's UI interactions.
 */
internal interface ProjectRepository {
    fun read(): VideoDraft?
    fun save(draft: VideoDraft, bitmaps: Map<Long, Bitmap>)
    fun overlay(id: Long, assetFile: String?): File?
    fun delete()
}

internal class LocalProjectRepository(context: Context) : ProjectRepository {
    private val draftStore = VideoDraftStore(context)

    override fun read(): VideoDraft? = draftStore.read()

    override fun save(draft: VideoDraft, bitmaps: Map<Long, Bitmap>) =
        draftStore.save(draft, bitmaps)

    override fun overlay(id: Long, assetFile: String?): File? =
        draftStore.overlay(id, assetFile)

    override fun delete() = draftStore.delete()
}
