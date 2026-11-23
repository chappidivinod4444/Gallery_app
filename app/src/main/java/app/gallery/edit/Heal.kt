package app.gallery.edit

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Healing brush (like Snapseed "Healing"): the painted area is replaced with the best matching patch from
 * nearby, then its colours are bent smoothly so the patch blends in without a visible edge.
 */
object Heal {

    /** [inner] is how strongly each pixel was painted (0..1); [outer] is the same stroke, a bit wider. */
    fun fill(px: IntArray, w: Int, h: Int, inner: FloatArray, outer: FloatArray) {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (outer[y * w + x] > 0.02f) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        // 0 = untouched, 1 = edge ring (known), 2 = painted (to fill)
        val kind = ByteArray(bw * bh)
        var painted = 0
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = (y + y0) * w + (x + x0)
            kind[y * bw + x] = when {
                inner[i] > 0.02f -> { painted++; 2 }
                outer[i] > 0.02f -> 1
                else -> 0
            }
        }
        if (painted == 0) return

        // 1. Find the best patch nearby: compare the ring around the spot with rings around candidate places.
        val size = max(bw, bh).toFloat()
        var best: Pair<Int, Int>? = null
        var bestScore = Float.MAX_VALUE
        val step = max(1, (bw * bh / 4000))
        for (dist in floatArrayOf(0.9f, 1.25f, 1.7f, 2.4f)) for (a in 0 until 24) {
            val ang = a * Math.PI / 12.0
            val dx = (cos(ang) * dist * size).toInt(); val dy = (sin(ang) * dist * size).toInt()
            if (x0 + dx < 0 || y0 + dy < 0 || x1 + dx >= w || y1 + dy >= h) continue
            var score = 0f; var n = 0; var overlap = 0
            var k = 0
            for (y in 0 until bh) for (x in 0 until bw) {
                val t = kind[y * bw + x]
                if (t.toInt() == 0) continue
                if (k++ % step != 0) continue
                val di = (y + y0) * w + (x + x0); val si = (y + y0 + dy) * w + (x + x0 + dx)
                if (inner[si] > 0.02f) overlap++
                if (t.toInt() == 1) {
                    val c = px[di]; val s = px[si]
                    val er = cr(c) - cr(s); val eg = cg(c) - cg(s); val eb = cb(c) - cb(s)
                    score += er * er + eg * eg + eb * eb; n++
                }
            }
            if (n == 0) continue
            val total = score / n + overlap * 0.05f + dist * 0.004f
            if (total < bestScore) { bestScore = total; best = dx to dy }
        }
        val (ox, oy) = best ?: return

        // 2. Difference between the spot's surroundings and the patch's surroundings, spread smoothly inwards.
        val dr = FloatArray(bw * bh); val dg = FloatArray(bw * bh); val db = FloatArray(bw * bh)
        var mr = 0f; var mg = 0f; var mb = 0f; var mn = 0
        for (y in 0 until bh) for (x in 0 until bw) {
            val j = y * bw + x
            if (kind[j].toInt() != 1) continue
            val di = (y + y0) * w + (x + x0); val si = (y + y0 + oy) * w + (x + x0 + ox)
            dr[j] = cr(px[di]) - cr(px[si]); dg[j] = cg(px[di]) - cg(px[si]); db[j] = cb(px[di]) - cb(px[si])
            mr += dr[j]; mg += dg[j]; mb += db[j]; mn++
        }
        if (mn > 0) { mr /= mn; mg /= mn; mb /= mn }
        for (j in kind.indices) if (kind[j].toInt() == 2) { dr[j] = mr; dg[j] = mg; db[j] = mb }
        val iterations = min(220, 40 + max(bw, bh))
        repeat(iterations) {
            for (y in 0 until bh) for (x in 0 until bw) {
                val j = y * bw + x
                if (kind[j].toInt() != 2) continue
                var sr = 0f; var sg = 0f; var sb = 0f; var n = 0
                if (x > 0 && kind[j - 1].toInt() != 0) { sr += dr[j - 1]; sg += dg[j - 1]; sb += db[j - 1]; n++ }
                if (x < bw - 1 && kind[j + 1].toInt() != 0) { sr += dr[j + 1]; sg += dg[j + 1]; sb += db[j + 1]; n++ }
                if (y > 0 && kind[j - bw].toInt() != 0) { sr += dr[j - bw]; sg += dg[j - bw]; sb += db[j - bw]; n++ }
                if (y < bh - 1 && kind[j + bw].toInt() != 0) { sr += dr[j + bw]; sg += dg[j + bw]; sb += db[j + bw]; n++ }
                if (n == 0) continue
                val o = 1.85f
                dr[j] += o * (sr / n - dr[j]); dg[j] += o * (sg / n - dg[j]); db[j] += o * (sb / n - db[j])
            }
        }

        // 3. Put the corrected patch in, softly at the edges.
        for (y in 0 until bh) for (x in 0 until bw) {
            val j = y * bw + x
            if (kind[j].toInt() != 2) continue
            val di = (y + y0) * w + (x + x0); val si = (y + y0 + oy) * w + (x + x0 + ox)
            val t = smooth(inner[di] * 3f)
            val s = px[si]; val c = px[di]
            px[di] = pack(c, mix(cr(c), cr(s) + dr[j], t), mix(cg(c), cg(s) + dg[j], t), mix(cb(c), cb(s) + db[j], t))
        }
    }

    /** Heals every spot of [s] on an output picture (positions are converted from frame space). */
    fun apply(px: IntArray, w: Int, h: Int, s: EditState) {
        val side = min(w, h) / s.crop.w.coerceAtLeast(0.05f)
        for (spot in s.heals) {
            val pts = spot.points.map { s.toOut(it) }
            val r = spot.width * side
            val inner = Pixels.paintMask(w, h, listOf(pts to (r to 1f)))
            val outer = Pixels.paintMask(w, h, listOf(pts to (r * 1.7f + 3f to 1f)))
            fill(px, w, h, inner, outer)
        }
    }
}
