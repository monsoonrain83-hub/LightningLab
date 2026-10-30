package com.lightninglab

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Built-in storm sound effects, synthesised in code (no audio files, no licensing issues). Deterministic: the same
 * effect is identical on every device. 44.1 kHz, mono, 16-bit PCM -> WAV.
 */
object SoundFx {
    const val RATE = 44100

    enum class Kind { RUMBLE, CRACK, CLAP, RAIN, WIND, STORM, ZAP, BOOM, WHOOSH, RISER }

    private fun n(sec: Float) = (sec * RATE).toInt()
    private fun lpCoef(fc: Float) = 1f - exp(-2f * PI.toFloat() * fc / RATE)

    private fun noise(len: Int, rnd: Random) = FloatArray(len) { rnd.nextFloat() * 2f - 1f }

    private fun lowpass(x: FloatArray, fc: Float): FloatArray {
        val a = lpCoef(fc); var y = 0f
        return FloatArray(x.size) { y += a * (x[it] - y); y }
    }

    private fun highpass(x: FloatArray, fc: Float): FloatArray {
        val l = lowpass(x, fc); return FloatArray(x.size) { x[it] - l[it] }
    }

    private fun env(t: Float, attack: Float, decay: Float) = (1f - exp(-t / max(1e-3f, attack))) * exp(-t / decay)

    /** Low rolling thunder body of `sec` seconds, unit-ish amplitude. */
    private fun rumble(sec: Float, rnd: Random, rolls: Int = 3): FloatArray {
        val len = n(sec)
        val b = lowpass(lowpass(noise(len, rnd), 95f), 140f)
        val starts = FloatArray(rolls) { 0.4f + rnd.nextFloat() * (sec * 0.5f) }
        val amps = FloatArray(rolls) { 0.4f + rnd.nextFloat() * 0.5f }
        return FloatArray(len) { i ->
            val t = i.toFloat() / RATE
            var e = env(t, 0.25f, sec * 0.4f)
            for (k in 0 until rolls) if (t > starts[k]) e += amps[k] * env(t - starts[k], 0.3f, 1.1f)
            b[i] * e * 9f + 0.3f * sin(2f * PI.toFloat() * 38f * t) * env(t, 0.3f, sec * 0.35f)
        }
    }

    private fun crackBurst(len: Int, rnd: Random): FloatArray {
        val hi = highpass(noise(len, rnd), 1500f)
        val mid = lowpass(noise(len, rnd), 3000f)
        return FloatArray(len) { i ->
            val t = i.toFloat() / RATE
            hi[i] * exp(-t / 0.05f) + 0.5f * mid[i] * exp(-t / 0.25f)
        }
    }

    private fun mixInto(dst: FloatArray, src: FloatArray, offset: Int, gain: Float) {
        for (i in src.indices) { val j = i + offset; if (j in dst.indices) dst[j] += src[i] * gain }
    }

    private fun rain(sec: Float, rnd: Random): FloatArray {
        val len = n(sec)
        val base = lowpass(highpass(noise(len, rnd), 600f), 7000f)
        val out = FloatArray(len) { base[it] * 0.45f * (1f + 0.15f * sin(2f * PI.toFloat() * 0.3f * it / RATE)) }
        val drops = (sec * 380).toInt()
        repeat(drops) {
            val at = rnd.nextInt(max(1, len - 400)); val amp = 0.2f + rnd.nextFloat() * 0.5f
            for (k in 0 until 140) out[at + k] += amp * (rnd.nextFloat() * 2f - 1f) * exp(-k / 25f)
        }
        return out
    }

    private fun wind(sec: Float, rnd: Random): FloatArray {
        val len = n(sec); val x = noise(len, rnd); var y = 0f
        return FloatArray(len) { i ->
            val t = i.toFloat() / RATE
            val fc = 450f + 350f * sin(2f * PI.toFloat() * 0.2f * t) + 120f * sin(2f * PI.toFloat() * 0.57f * t)
            y += lpCoef(max(120f, fc)) * (x[i] - y)
            y * (1.6f + 0.9f * sin(2f * PI.toFloat() * 0.13f * t + 1f)) * 2.2f
        }
    }

    fun render(kind: Kind): ShortArray {
        val rnd = Random(42L + kind.ordinal)
        val pcm: FloatArray = when (kind) {
            Kind.RUMBLE -> rumble(7f, rnd)
            Kind.CRACK -> {
                val out = FloatArray(n(3.5f))
                mixInto(out, crackBurst(n(1.2f), rnd), 0, 1f)
                mixInto(out, rumble(3.2f, rnd, 2), n(0.12f), 0.8f)
                out
            }
            Kind.CLAP -> {
                val out = FloatArray(n(4f))
                mixInto(out, crackBurst(n(1.0f), rnd), 0, 1f)
                mixInto(out, crackBurst(n(0.9f), rnd), n(0.18f), 0.7f)
                mixInto(out, crackBurst(n(0.9f), rnd), n(0.42f), 0.5f)
                mixInto(out, rumble(3.6f, rnd, 3), n(0.2f), 0.9f)
                out
            }
            Kind.RAIN -> rain(8f, rnd)
            Kind.WIND -> wind(8f, rnd)
            Kind.STORM -> {
                val out = FloatArray(n(10f))
                mixInto(out, rain(10f, rnd), 0, 0.7f)
                mixInto(out, wind(10f, rnd), 0, 0.5f)
                mixInto(out, rumble(5f, rnd, 2), n(1.5f), 0.7f)
                mixInto(out, rumble(4f, rnd, 2), n(6f), 0.6f)
                out
            }
            Kind.ZAP -> {
                val len = n(0.9f); val hi = highpass(noise(len, rnd), 2000f)
                var phase = 0f; var gate = 1f; var gateLeft = 0
                FloatArray(len) { i ->
                    val t = i.toFloat() / RATE
                    if (gateLeft <= 0) { gate = if (rnd.nextBoolean()) 1f else 0.1f; gateLeft = 80 + rnd.nextInt(400) }
                    gateLeft--
                    val f = 300f + 3700f * exp(-t / 0.12f)
                    phase += 2f * PI.toFloat() * f / RATE
                    hi[i] * gate * exp(-t / 0.35f) + 0.25f * sin(phase) * exp(-t / 0.2f)
                }
            }
            Kind.BOOM -> {
                val len = n(2.5f); val th = lowpass(noise(len, rnd), 400f); var phase = 0f
                FloatArray(len) { i ->
                    val t = i.toFloat() / RATE
                    phase += 2f * PI.toFloat() * (32f + 78f * exp(-t / 0.18f)) / RATE
                    sin(phase) * exp(-t / 0.9f) + 3.5f * th[i] * exp(-t / 0.12f)
                }
            }
            Kind.WHOOSH -> {
                val len = n(1.6f); val x = noise(len, rnd); var y1 = 0f; var y2 = 0f
                FloatArray(len) { i ->
                    val t = i.toFloat() / RATE; val fc = 400f + 2600f * (t / 1.6f)
                    y1 += lpCoef(fc) * (x[i] - y1); y2 += lpCoef(fc / 3f) * (x[i] - y2)
                    val s = sin(PI.toFloat() * t / 1.6f)
                    (y1 - y2) * 3f * s * s
                }
            }
            Kind.RISER -> {
                val len = n(3f); val x = noise(len, rnd); var y = 0f; var phase = 0f
                FloatArray(len) { i ->
                    val t = i.toFloat() / RATE; val k = t / 3f
                    y += lpCoef(300f + 5700f * k) * (x[i] - y)
                    phase += 2f * PI.toFloat() * (200f + 1000f * k) / RATE
                    ((x[i] - y) * 0.6f + 0.3f * sin(phase)) * k * k
                }
            }
        }
        var peak = 1e-6f
        for (v in pcm) peak = max(peak, abs(v))
        val g = 0.85f / peak
        val fade = n(0.03f)
        return ShortArray(pcm.size) { i ->
            var v = pcm[i] * g
            val left = pcm.size - 1 - i
            if (left < fade) v *= left.toFloat() / fade
            (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    fun writeWav(file: File, pcm: ShortArray) {
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + data); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data)
        for (s in pcm) b.putShort(s)
        FileOutputStream(file).use { it.write(b.array()) }
    }
}
