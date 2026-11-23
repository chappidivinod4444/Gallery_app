package app.gallery.edit

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Smooth curve through points that never overshoots (monotone cubic, Fritsch–Carlson). */
object Spline {

    /** 256-entry lookup table, values 0..255. */
    fun lut(points: List<P>): IntArray {
        val pts = points.sortedBy { it.x }.distinctBy { (it.x * 1000).roundToInt() }
        val out = IntArray(256)
        if (pts.size < 2) { for (i in 0..255) out[i] = i; return out }
        val n = pts.size
        val xs = FloatArray(n) { pts[it].x }
        val ys = FloatArray(n) { pts[it].y }
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-5f) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }
        for (i in 0..255) {
            val x = i / 255f
            val y = when {
                x <= xs[0] -> ys[0]
                x >= xs[n - 1] -> ys[n - 1]
                else -> {
                    var k = 0
                    while (k < n - 2 && x > xs[k + 1]) k++
                    val h = xs[k + 1] - xs[k]
                    val t = (x - xs[k]) / h
                    val t2 = t * t; val t3 = t2 * t
                    (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
                }
            }
            out[i] = (y.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        return out
    }

    /** Ready-made curves (Snapseed's curve presets). */
    val presets: List<Pair<String, Curves>> = listOf(
        "Linear" to Curves(),
        "Contrast" to Curves(rgb = listOf(P(0f, 0f), P(0.25f, 0.18f), P(0.75f, 0.84f), P(1f, 1f))),
        "Strong" to Curves(rgb = listOf(P(0f, 0f), P(0.25f, 0.12f), P(0.75f, 0.9f), P(1f, 1f))),
        "Brighten" to Curves(rgb = listOf(P(0f, 0f), P(0.45f, 0.58f), P(1f, 1f))),
        "Darken" to Curves(rgb = listOf(P(0f, 0f), P(0.55f, 0.42f), P(1f, 1f))),
        "Matte" to Curves(rgb = listOf(P(0f, 0.12f), P(0.3f, 0.3f), P(0.7f, 0.74f), P(1f, 0.94f))),
        "Faded" to Curves(rgb = listOf(P(0f, 0.2f), P(0.5f, 0.52f), P(1f, 0.88f))),
        "Cross" to Curves(
            r = listOf(P(0f, 0f), P(0.3f, 0.22f), P(0.7f, 0.82f), P(1f, 1f)),
            g = listOf(P(0f, 0f), P(0.3f, 0.26f), P(0.7f, 0.78f), P(1f, 1f)),
            b = listOf(P(0f, 0.12f), P(0.5f, 0.5f), P(1f, 0.86f)),
        ),
        "Teal & orange" to Curves(
            r = listOf(P(0f, 0f), P(0.5f, 0.54f), P(1f, 1f)),
            b = listOf(P(0f, 0.08f), P(0.5f, 0.48f), P(1f, 0.92f)),
        ),
        "Cool" to Curves(b = listOf(P(0f, 0.05f), P(0.5f, 0.56f), P(1f, 1f)), r = listOf(P(0f, 0f), P(0.5f, 0.46f), P(1f, 1f))),
        "Warm" to Curves(r = listOf(P(0f, 0.03f), P(0.5f, 0.56f), P(1f, 1f)), b = listOf(P(0f, 0f), P(0.5f, 0.44f), P(1f, 0.95f))),
    )
}
