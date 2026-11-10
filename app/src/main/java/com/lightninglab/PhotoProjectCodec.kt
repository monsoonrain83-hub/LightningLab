package com.lightninglab

import org.json.JSONArray
import org.json.JSONObject

data class PhotoTextData(val id: Long, val name: String, val text: String, val x: Float, val y: Float, val size: Float, val color: Int, val opacity: Float, val visible: Boolean, val bold: Boolean, val z: Int, val rotation: Float = 0f)
data class PhotoShapeData(val id: Long, val name: String, val shape: String, val x: Float, val y: Float, val width: Float, val height: Float, val color: Int, val opacity: Float, val stroke: Float, val visible: Boolean, val z: Int, val rotation: Float = 0f)
data class PhotoImageData(val id: Long, val name: String, val file: String, val x: Float, val y: Float, val width: Float, val opacity: Float, val visible: Boolean, val z: Int, val rotation: Float = 0f)
data class PhotoMaskData(val points: List<Pair<Float, Float>>, val radius: Float, val softness: Float)
data class PhotoProjectData(
    val name: String, val source: String, val params: PhotoParams, val texts: List<PhotoTextData>,
    val shapes: List<PhotoShapeData>, val images: List<PhotoImageData>, val masks: List<PhotoMaskData>, val maskInverted: Boolean
)

/** Versioned, editable photo-project manifest. Image pixels are stored as sibling PNG assets. */
object PhotoProjectCodec {
    private const val VERSION = 1
    fun encode(p: PhotoProjectData): String {
        val q = p.params
        val o = JSONObject().put("v", VERSION).put("name", p.name).put("source", p.source).put("maskInverted", p.maskInverted)
        o.put("params", JSONObject().put("exposure", q.exposure.toDouble()).put("brightness", q.brightness.toDouble())
            .put("contrast", q.contrast.toDouble()).put("saturation", q.saturation.toDouble()).put("warmth", q.warmth.toDouble())
            .put("rotate", q.rotate).put("flipH", q.flipH).put("flipV", q.flipV).put("aspect", q.aspect.toDouble())
            .put("highlights", q.highlights.toDouble()).put("shadows", q.shadows.toDouble()).put("sharpness", q.sharpness.toDouble())
            .put("scurve", q.scurve.toDouble()).put("denoise", q.denoise.toDouble()).put("vibrance", q.vibrance.toDouble())
            .put("hsl", JSONArray().also { a -> q.hsl.forEach { a.put(it.toDouble()) } }))
        o.put("texts", JSONArray().also { a -> p.texts.forEach { t -> a.put(JSONObject().put("id", t.id).put("name", t.name).put("text", t.text)
            .put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("size", t.size.toDouble()).put("color", t.color).put("opacity", t.opacity.toDouble())
            .put("visible", t.visible).put("bold", t.bold).put("z", t.z).put("rotation", t.rotation.toDouble())) } })
        o.put("shapes", JSONArray().also { a -> p.shapes.forEach { t -> a.put(JSONObject().put("id", t.id).put("name", t.name).put("shape", t.shape)
            .put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("w", t.width.toDouble()).put("h", t.height.toDouble()).put("color", t.color)
            .put("opacity", t.opacity.toDouble()).put("stroke", t.stroke.toDouble()).put("visible", t.visible).put("z", t.z).put("rotation", t.rotation.toDouble())) } })
        o.put("images", JSONArray().also { a -> p.images.forEach { t -> a.put(JSONObject().put("id", t.id).put("name", t.name).put("file", t.file)
            .put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("w", t.width.toDouble()).put("opacity", t.opacity.toDouble()).put("visible", t.visible).put("z", t.z).put("rotation", t.rotation.toDouble())) } })
        o.put("masks", JSONArray().also { a -> p.masks.forEach { m -> a.put(JSONObject().put("radius", m.radius.toDouble()).put("softness", m.softness.toDouble())
            .put("points", JSONArray().also { pts -> m.points.forEach { pt -> pts.put(JSONArray().put(pt.first.toDouble()).put(pt.second.toDouble())) } })) } })
        return o.toString()
    }

    fun decode(raw: String): PhotoProjectData? = try {
        val o = JSONObject(raw); if (o.optInt("v", 0) != VERSION) throw IllegalArgumentException("unsupported photo project version")
        val q = o.getJSONObject("params")
        val hslArray = q.optJSONArray("hsl")
        val hsl = (0 until 21).map { i -> hslArray?.optDouble(i, 0.0)?.toFloat() ?: 0f }
        val params = PhotoParams(q.optDouble("exposure", 0.0).toFloat(), q.optDouble("brightness", 0.0).toFloat(),
            q.optDouble("contrast", 0.0).toFloat(), q.optDouble("saturation", 0.0).toFloat(), q.optDouble("warmth", 0.0).toFloat(),
            q.optInt("rotate", 0), q.optBoolean("flipH", false), q.optBoolean("flipV", false), q.optDouble("aspect", 0.0).toFloat(),
            q.optDouble("highlights", 0.0).toFloat(), q.optDouble("shadows", 0.0).toFloat(), q.optDouble("sharpness", 0.0).toFloat(),
            q.optDouble("scurve", 0.0).toFloat(), q.optDouble("denoise", 0.0).toFloat(), q.optDouble("vibrance", 0.0).toFloat(), hsl)
        fun array(name: String) = o.optJSONArray(name) ?: JSONArray()
        val texts = (0 until array("texts").length()).mapNotNull { i -> array("texts").optJSONObject(i)?.let { t -> PhotoTextData(
            t.optLong("id"), t.optString("name"), t.optString("text"), t.optDouble("x", .5).toFloat(), t.optDouble("y", .5).toFloat(),
            t.optDouble("size", .075).toFloat(), t.optInt("color", -1), t.optDouble("opacity", 1.0).toFloat(), t.optBoolean("visible", true), t.optBoolean("bold", true), t.optInt("z"), t.optDouble("rotation", 0.0).toFloat()) } }
        val shapes = (0 until array("shapes").length()).mapNotNull { i -> array("shapes").optJSONObject(i)?.let { t -> PhotoShapeData(
            t.optLong("id"), t.optString("name"), t.optString("shape", "RECTANGLE"), t.optDouble("x", .5).toFloat(), t.optDouble("y", .5).toFloat(),
            t.optDouble("w", .3).toFloat(), t.optDouble("h", .18).toFloat(), t.optInt("color", -1), t.optDouble("opacity", .65).toFloat(),
            t.optDouble("stroke", 0.0).toFloat(), t.optBoolean("visible", true), t.optInt("z"), t.optDouble("rotation", 0.0).toFloat()) } }
        val images = (0 until array("images").length()).mapNotNull { i -> array("images").optJSONObject(i)?.let { t -> PhotoImageData(
            t.optLong("id"), t.optString("name"), t.optString("file"), t.optDouble("x", .5).toFloat(), t.optDouble("y", .5).toFloat(),
            t.optDouble("w", .35).toFloat(), t.optDouble("opacity", 1.0).toFloat(), t.optBoolean("visible", true), t.optInt("z"), t.optDouble("rotation", 0.0).toFloat()) } }
        val masks = (0 until array("masks").length()).mapNotNull { i -> array("masks").optJSONObject(i)?.let { m ->
            val pts = m.optJSONArray("points") ?: JSONArray()
            PhotoMaskData((0 until pts.length()).mapNotNull { j -> pts.optJSONArray(j)?.let { Pair(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) } },
                m.optDouble("radius", .025).toFloat(), m.optDouble("softness", .35).toFloat()) } }
        PhotoProjectData(o.optString("name", "Photo project"), o.getString("source"), params, texts, shapes, images, masks, o.optBoolean("maskInverted", false))
    } catch (_: Exception) { null }
}
