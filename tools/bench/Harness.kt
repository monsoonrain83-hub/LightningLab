import com.lightninglab.*
import java.io.*
import java.nio.*
const val GX = 16; const val GY = 9
fun sampleFromLuma(t: Long, l: FloatArray, w: Int, h: Int): Sample {
    var sum = 0f; for (v in l) sum += v
    val mean = sum / l.size
    val cells = FloatArray(GX * GY); val cnt = IntArray(GX * GY)
    for (y in 0 until h) for (x in 0 until w) { val c = (y * GY / h) * GX + (x * GX / w); cells[c] += l[y * w + x]; cnt[c]++ }
    for (i in cells.indices) cells[i] = cells[i] / maxOf(1, cnt[i]).toFloat()
    val blur = ImgUtil.boxBlur(l, w, h, 3)
    var edges = 0; for (i in l.indices) if (l[i] - blur[i] > 18f) edges++
    return Sample(t, mean, cells, edges.toFloat() / l.size)
}
fun main(args: Array<String>) {
    val sens = args[1].toFloat()
    val bb = ByteBuffer.wrap(File(args[0]).readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    val n = bb.int; val w = bb.int; val h = bb.int
    val day = DayLine(w, h, sens = sens); val ms = ArrayList<Sample>()
    for (i in 0 until n) {
        val t = bb.long; val l = FloatArray(w * h) { bb.float }
        day.push(t, l); ms += sampleFromLuma(t, l, w, h)
    }
    day.finish()
    val t0 = System.nanoTime()
    val ev = FlashAnalyzer.analyze(ms, sens, 250L, day.stats)
    println("events=${ev.size} " + ev.joinToString(" ") { "${it.bestMs}ms(s=%.2f)".format(it.score) })
}
