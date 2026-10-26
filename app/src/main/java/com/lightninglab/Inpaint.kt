package com.lightninglab

/**
 * Object-removal fill (original code): "onion peeling". Unknown pixels are filled from the outside in; each pixel
 * takes the average of its already-known 8 neighbours. O(area). Produces a smooth fill - good for clean sky,
 * wires, dust spots, satellite/plane trails; it will NOT reconstruct texture or structure.
 */
object Inpaint {
    private fun avg(px: IntArray, unk: BooleanArray, w: Int, h: Int, i: Int): Int {
        val x = i % w; val y = i / w
        var r = 0; var g = 0; var b = 0; var c = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val xx = x + dx; val yy = y + dy
            if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
            val j = yy * w + xx
            if (unk[j]) continue
            val p = px[j]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; c++
        }
        if (c == 0) return 0
        return (0xFF shl 24) or ((r / c) shl 16) or ((g / c) shl 8) or (b / c)
    }

    /** px is modified in place; unk marks pixels to fill (cleared as they are filled). */
    fun fill(px: IntArray, w: Int, h: Int, unk: BooleanArray) {
        val n = w * h
        val queued = BooleanArray(n)
        var cur = IntArray(n); var nxt = IntArray(n); val col = IntArray(n)
        var curN = 0
        for (i in 0 until n) if (unk[i] && avg(px, unk, w, h, i) != 0) { cur[curN++] = i; queued[i] = true }
        while (curN > 0) {
            for (k in 0 until curN) col[k] = avg(px, unk, w, h, cur[k])
            for (k in 0 until curN) { px[cur[k]] = col[k]; unk[cur[k]] = false }
            var nxtN = 0
            for (k in 0 until curN) {
                val x = cur[k] % w; val y = cur[k] / w
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (unk[j] && !queued[j]) { queued[j] = true; nxt[nxtN++] = j }
                }
            }
            val t = cur; cur = nxt; nxt = t; curN = nxtN
        }
    }
}
