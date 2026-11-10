package com.lightninglab

import org.json.JSONArray
import org.json.JSONObject

data class SegData(val startMs: Long, val endMs: Long, val clip: String?)
data class EffectClipData(val id: Long, val filterIndex: Int, val startMs: Long, val endMs: Long)
data class OverlayKeyframeData(val timeMs: Long, val alpha: Float, val position: Int)
data class OverlayClipData(val id: Long, val kind: String, val content: String, val startMs: Long, val endMs: Long,
    val color: Int, val size: Int, val position: Int, val background: Boolean, val keyframes: List<OverlayKeyframeData> = emptyList(), val assetFile: String? = null)

/** Editor state saved with a video project. File names are relative to the project folder. */
data class ProjectData(
    val name: String, val video: String, val videoDur: Long, val segs: List<SegData>,
    val music: String?, val voice: String?, val mute: Boolean,
    val filter: Int, val speed: Float, val brightness: Float, val contrast: Float,
    val rotate: Int, val flipH: Boolean, val flipV: Boolean, val crop: Float,
    val text: String, val textColor: Int, val textSize: Int, val textPos: Int, val textBg: Boolean,
    val sticker: String?, val stickerSize: Int, val stickerPos: Int,
    val volOrig: Float = 1f, val volMusic: Float = 1f, val volVoice: Float = 1f,
    val overlayClips: List<OverlayClipData> = emptyList(),
    val effectClips: List<EffectClipData> = emptyList(),
    val markers: List<Long> = emptyList(),
    val fadeInMusicMs: Long = 0L, val fadeOutMusicMs: Long = 0L,
    val fadeInVoiceMs: Long = 0L, val fadeOutVoiceMs: Long = 0L
)

private fun Int?.orZero(): Int = this ?: 0

object ProjectCodec {
    const val VERSION = 5

    fun encode(p: ProjectData): String {
        val o = JSONObject()
        o.put("v", VERSION).put("name", p.name).put("video", p.video).put("dur", p.videoDur)
        o.put("segs", JSONArray().also { a ->
            p.segs.forEach { s -> a.put(JSONObject().put("s", s.startMs).put("e", s.endMs).put("c", s.clip ?: "")) }
        })
        o.put("music", p.music ?: "").put("voice", p.voice ?: "").put("mute", p.mute)
        o.put("filter", p.filter).put("speed", p.speed.toDouble()).put("bri", p.brightness.toDouble())
        o.put("con", p.contrast.toDouble()).put("rot", p.rotate).put("fh", p.flipH).put("fv", p.flipV)
        o.put("crop", p.crop.toDouble())
        o.put("text", p.text).put("tc", p.textColor).put("ts", p.textSize).put("tp", p.textPos).put("tb", p.textBg)
        o.put("stk", p.sticker ?: "").put("ss", p.stickerSize).put("sp", p.stickerPos)
        o.put("vo", p.volOrig.toDouble()).put("vm", p.volMusic.toDouble()).put("vv", p.volVoice.toDouble())
        o.put("fim", p.fadeInMusicMs.coerceIn(0L, 5_000L)).put("fom", p.fadeOutMusicMs.coerceIn(0L, 5_000L))
        o.put("fiv", p.fadeInVoiceMs.coerceIn(0L, 5_000L)).put("fov", p.fadeOutVoiceMs.coerceIn(0L, 5_000L))
        o.put("effectClips", JSONArray().also { a -> p.effectClips.forEach { e ->
            a.put(JSONObject().put("id", e.id).put("filter", e.filterIndex).put("s", e.startMs).put("e", e.endMs))
        } })
        o.put("markers", JSONArray().also { a -> p.markers.distinct().sorted().forEach { a.put(it) } })
        o.put("overlayClips", JSONArray().also { a -> p.overlayClips.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("kind", c.kind).put("content", c.content)
                .put("s", c.startMs).put("e", c.endMs).put("color", c.color).put("size", c.size)
                .put("pos", c.position).put("bg", c.background).put("asset", c.assetFile ?: "").put("keyframes", JSONArray().also { k -> c.keyframes.forEach { f -> k.put(JSONObject().put("t", f.timeMs).put("a", f.alpha.toDouble()).put("p", f.position)) } }))
        } })
        return o.toString()
    }

    /** Returns null for corrupt data or a newer, unknown format. */
    fun decode(s: String): ProjectData? = try {
        val o = JSONObject(s)
        if (o.optInt("v", 0) !in 1..VERSION) null else {
            val a = o.getJSONArray("segs")
            val segs = (0 until a.length()).map { i ->
                val j = a.getJSONObject(i)
                SegData(j.getLong("s"), j.getLong("e"), j.optString("c", "").ifEmpty { null })
            }
            ProjectData(
                o.getString("name"), o.getString("video"), o.optLong("dur", 0L), segs,
                o.optString("music", "").ifEmpty { null }, o.optString("voice", "").ifEmpty { null }, o.optBoolean("mute", false),
                o.optInt("filter", 0), o.optDouble("speed", 1.0).toFloat(), o.optDouble("bri", 0.0).toFloat(),
                o.optDouble("con", 0.0).toFloat(), o.optInt("rot", 0), o.optBoolean("fh", false), o.optBoolean("fv", false),
                o.optDouble("crop", 0.0).toFloat(), o.optString("text", ""), o.optInt("tc", -1), o.optInt("ts", 1),
                o.optInt("tp", 1), o.optBoolean("tb", false), o.optString("stk", "").ifEmpty { null },
                o.optInt("ss", 1), o.optInt("sp", 1),
                o.optDouble("vo", 1.0).toFloat(), o.optDouble("vm", 1.0).toFloat(), o.optDouble("vv", 1.0).toFloat(),
                (0 until o.optJSONArray("overlayClips")?.length().orZero()).mapNotNull { i ->
                    val c = o.optJSONArray("overlayClips")?.optJSONObject(i) ?: return@mapNotNull null
                    val a = c.optLong("s", 0L); val b = c.optLong("e", 1L)
                    if (b <= a) null else OverlayClipData(c.optLong("id", i.toLong()+1), c.optString("kind", "TEXT"),
                        c.optString("content", ""), a, b, c.optInt("color", -1), c.optInt("size", 1), c.optInt("pos", 1), c.optBoolean("bg", false),
                        (0 until (c.optJSONArray("keyframes")?.length() ?: 0)).mapNotNull { j ->
                            val k = c.optJSONArray("keyframes")?.optJSONObject(j) ?: return@mapNotNull null
                            OverlayKeyframeData(k.optLong("t", a).coerceIn(a, b), k.optDouble("a", 1.0).toFloat().coerceIn(0f, 1f), k.optInt("p", c.optInt("pos", 1)).coerceIn(0, 2))
                        }.sortedBy { it.timeMs }, c.optString("asset", "").ifEmpty { null })
                },
                (0 until (o.optJSONArray("effectClips")?.length().orZero())).mapNotNull { i ->
                    val e = o.optJSONArray("effectClips")?.optJSONObject(i) ?: return@mapNotNull null
                    val start = e.optLong("s", 0L); val end = e.optLong("e", 0L)
                    if (end <= start) null else EffectClipData(e.optLong("id", i.toLong() + 1), e.optInt("filter", 0).coerceIn(0, 5), start, end)
                },
                (0 until (o.optJSONArray("markers")?.length().orZero())).mapNotNull { i ->
                    o.optJSONArray("markers")?.optLong(i)?.takeIf { it >= 0L }
                }.distinct().sorted(),
                o.optLong("fim", 0L).coerceIn(0L, 5_000L), o.optLong("fom", 0L).coerceIn(0L, 5_000L),
                o.optLong("fiv", 0L).coerceIn(0L, 5_000L), o.optLong("fov", 0L).coerceIn(0L, 5_000L))
        }
    } catch (e: Exception) { null }
}
