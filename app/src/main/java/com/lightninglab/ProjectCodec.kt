package com.lightninglab

import org.json.JSONArray
import org.json.JSONObject

data class SegData(val startMs: Long, val endMs: Long, val clip: String?)

/** Editor state saved with a video project. File names are relative to the project folder. */
data class ProjectData(
    val name: String, val video: String, val videoDur: Long, val segs: List<SegData>,
    val music: String?, val voice: String?, val mute: Boolean,
    val filter: Int, val speed: Float, val brightness: Float, val contrast: Float,
    val rotate: Int, val flipH: Boolean, val flipV: Boolean, val crop: Float,
    val text: String, val textColor: Int, val textSize: Int, val textPos: Int, val textBg: Boolean,
    val sticker: String?, val stickerSize: Int, val stickerPos: Int,
    val volOrig: Float = 1f, val volMusic: Float = 1f, val volVoice: Float = 1f
)

object ProjectCodec {
    const val VERSION = 1

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
        return o.toString()
    }

    /** Returns null for corrupt data or a newer, unknown format. */
    fun decode(s: String): ProjectData? = try {
        val o = JSONObject(s)
        if (o.optInt("v", 0) != VERSION) null else {
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
                o.optDouble("vo", 1.0).toFloat(), o.optDouble("vm", 1.0).toFloat(), o.optDouble("vv", 1.0).toFloat())
        }
    } catch (e: Exception) { null }
}
