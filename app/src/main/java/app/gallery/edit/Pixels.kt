package app.gallery.edit

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Packed ARGB helpers.
@Suppress("NOTHING_TO_INLINE") internal inline fun cr(c: Int) = ((c shr 16) and 0xFF) * (1f / 255f)
@Suppress("NOTHING_TO_INLINE") internal inline fun cg(c: Int) = ((c shr 8) and 0xFF) * (1f / 255f)
@Suppress("NOTHING_TO_INLINE") internal inline fun cb(c: Int) = (c and 0xFF) * (1f / 255f)
@Suppress("NOTHING_TO_INLINE") internal inline fun lum(r: Float, g: Float, b: Float) = 0.299f * r + 0.587f * g + 0.114f * b
@Suppress("NOTHING_TO_INLINE") internal inline fun lumOf(c: Int) = lum(cr(c), cg(c), cb(c))
@Suppress("NOTHING_TO_INLINE") internal inline fun ch(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
@Suppress("NOTHING_TO_INLINE") internal inline fun pack(a: Int, r: Float, g: Float, b: Float) = (a and -0x1000000) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
@Suppress("NOTHING_TO_INLINE") internal inline fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
@Suppress("NOTHING_TO_INLINE") internal inline fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Repeatable "random" number 0..1 for a pixel position (same picture always gets the same grain). */
internal fun hash(x: Int, y: Int, seed: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + seed * 2147483647
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

/** All per-pixel work of the editor. Works on plain int arrays so it runs (and is tested) without a phone. */
object Pixels {

    // ------------------------------------------------------------------ blur

    /** Soft blur of any size, fast: shrinks the picture, blurs it, and grows it back smoothly. */
    fun blur(src: IntArray, w: Int, h: Int, radius: Float): IntArray {
        if (radius < 0.75f) return src.copyOf()
        val f = max(1, (radius / 3f).toInt())
        val r = max(1, (radius / f).roundToInt())
        if (f == 1) return box(src, w, h, r, 3)
        val sw = max(1, w / f); val sh = max(1, h / f)
        val small = downsample(src, w, h, sw, sh)
        val b = box(small, sw, sh, r, 3)
        return upsample(b, sw, sh, w, h)
    }

    fun downsample(src: IntArray, w: Int, h: Int, sw: Int, sh: Int): IntArray {
        val out = IntArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = y * h / sh; val y1 = max(y0 + 1, (y + 1) * h / sh)
            for (x in 0 until sw) {
                val x0 = x * w / sw; val x1 = max(x0 + 1, (x + 1) * w / sw)
                var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
                for (yy in y0 until y1) {
                    val row = yy * w
                    for (xx in x0 until x1) {
                        val c = src[row + xx]
                        a += (c ushr 24); r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                    }
                }
                out[y * sw + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    /** Smooth (bilinear) enlargement. */
    fun upsample(src: IntArray, sw: Int, sh: Int, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        val kx = sw.toFloat() / w; val ky = sh.toFloat() / h
        for (y in 0 until h) {
            val fy = ((y + 0.5f) * ky - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) * kx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                val a = src[y0 * sw + x0]; val b = src[y0 * sw + x1]; val c = src[y1 * sw + x0]; val d = src[y1 * sw + x1]
                fun lerp(sh0: Int): Int {
                    val top = ((a shr sh0) and 0xFF) * (1 - tx) + ((b shr sh0) and 0xFF) * tx
                    val bot = ((c shr sh0) and 0xFF) * (1 - tx) + ((d shr sh0) and 0xFF) * tx
                    return (top * (1 - ty) + bot * ty + 0.5f).toInt().coerceIn(0, 255)
                }
                out[y * w + x] = (lerp(24) shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
            }
        }
        return out
    }

    /** Box blur run [passes] times (3 passes look like a smooth Gaussian). */
    fun box(src: IntArray, w: Int, h: Int, r: Int, passes: Int, horizontalOnly: Boolean = false): IntArray {
        var a = src.copyOf()
        var b = IntArray(src.size)
        repeat(passes) {
            boxH(a, b, w, h, r)
            if (horizontalOnly) { val t = a; a = b; b = t } else boxV(b, a, w, h, r)
        }
        return a
    }

    private fun boxH(src: IntArray, out: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) { val c = src[row + k.coerceIn(0, w - 1)]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            for (x in 0 until w) {
                out[row + x] = (src[row + x] and -0x1000000) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[row + min(x + r + 1, w - 1)]; val sub = src[row + max(x - r, 0)]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    private fun boxV(src: IntArray, out: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) { val c = src[k.coerceIn(0, h - 1) * w + x]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            for (y in 0 until h) {
                out[y * w + x] = (src[y * w + x] and -0x1000000) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[min(y + r + 1, h - 1) * w + x]; val sub = src[max(y - r, 0) * w + x]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    // ------------------------------------------------------------------ main

    /** Everything after the geometry and the simple colour matrix. Changes [px] in place and returns it. */
    fun process(px: IntArray, w: Int, h: Int, s: EditState): IntArray {
        val side = min(w, h).toFloat()
        smoothing(px, w, h, s.f(Params.smooth), side)
        tone(px, w, h, s, side)
        details(px, w, h, s, side)
        if (s.points.isNotEmpty()) selective(px, w, h, s, side)
        if (s.masks.isNotEmpty()) brush(px, w, h, s, side)
        return effects(px, w, h, s, side)
    }

    // ------------------------------------------------------------------ smoothing (skin / noise)

    private fun smoothing(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        if (k <= 0f) return
        val b = blur(px, w, h, max(1.5f, side / 260f))
        val t = 0.012f + 0.03f * k
        for (i in px.indices) {
            val c = px[i]; val d = b[i]
            val r = cr(c); val g = cg(c); val bl = cb(c)
            val dr = cr(d); val dg = cg(d); val db = cb(d)
            val diff = abs(r - dr) + abs(g - dg) + abs(bl - db)
            // Smooth flat areas (skin, sky, noise) but keep edges sharp.
            val wgt = k * exp(-(diff * diff) / t)
            px[i] = pack(c, mix(r, dr, wgt), mix(g, dg, wgt), mix(bl, db, wgt))
        }
    }

    // ------------------------------------------------------------------ tone and colour

    private fun tone(px: IntArray, w: Int, h: Int, s: EditState, side: Float) {
        val hl = s.f(Params.highlights); val sh = s.f(Params.shadows); val amb = s.f(Params.ambiance)
        val wh = s.f(Params.whites); val bk = s.f(Params.blacks); val fade = s.f(Params.fade)
        val vib = s.f(Params.vibrance)
        val hsl = (0 until 8).any { s.has(Params.hue(it)) || s.has(Params.sat(it)) || s.has(Params.lum(it)) }
        val lut = wh != 0f || bk != 0f || fade != 0f || !s.curves.isIdentity
        val local = hl != 0f || sh != 0f || amb != 0f
        if (!local && !lut && vib == 0f && !hsl) return

        val base = if (local) blur(px, w, h, side / 28f) else null
        var lr: IntArray? = null; var lg: IntArray? = null; var lb: IntArray? = null
        if (lut) {
            val c0 = Spline.lut(s.curves.rgb); val c1 = Spline.lut(s.curves.r); val c2 = Spline.lut(s.curves.g); val c3 = Spline.lut(s.curves.b)
            lr = IntArray(256); lg = IntArray(256); lb = IntArray(256)
            for (i in 0..255) {
                val x = i / 255f
                var y = x
                y += 0.28f * wh * smooth((x - 0.35f) / 0.65f) * x
                y += 0.22f * bk * (1f - x).pow(3)
                y = y * (1f - 0.2f * fade) + 0.13f * fade
                val j = c0[ch(y)]
                lr[i] = c1[j]; lg[i] = c2[j]; lb[i] = c3[j]
            }
        }
        val hs = FloatArray(8) { s.f(Params.hue(it)) }; val ss = FloatArray(8) { s.f(Params.sat(it)) }; val ls = FloatArray(8) { s.f(Params.lum(it)) }
        val satBoost = 1f + 0.35f * amb
        for (i in px.indices) {
            val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            if (base != null) {
                val bl = lumOf(base[i])
                val ws = (1f - bl) * (1f - bl); val wl = bl * bl
                val l = lum(r, g, b)
                var d = sh * 0.42f * ws + hl * 0.40f * wl + amb * 0.30f * (ws - 0.7f * wl)
                if (d != 0f) {
                    if (l > 0.02f) {
                        val k = ((l + d) / l).coerceIn(0.2f, 4f)
                        r *= k; g *= k; b *= k
                    } else { r += d; g += d; b += d }
                }
                if (amb != 0f) {
                    val l2 = lum(r, g, b)
                    r = l2 + (r - l2) * satBoost; g = l2 + (g - l2) * satBoost; b = l2 + (b - l2) * satBoost
                }
            }
            if (lr != null) {
                r = lr[ch(r)] / 255f; g = lg!![ch(g)] / 255f; b = lb!![ch(b)] / 255f
            }
            if (vib != 0f) {
                val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                // More boost for dull colours, less for skin-like oranges.
                val skin = if (r > g && g > b) 0.5f else 1f
                val amt = 1f + vib * (1f - (mx - mn)) * 0.9f * skin
                val l = lum(r, g, b)
                r = l + (r - l) * amt; g = l + (g - l) * amt; b = l + (b - l) * amt
            }
            if (hsl) {
                val rr = r.coerceIn(0f, 1f); val gg = g.coerceIn(0f, 1f); val bb = b.coerceIn(0f, 1f)
                val mx = max(rr, max(gg, bb)); val mn = min(rr, min(gg, bb))
                val dl = mx - mn
                if (dl > 0.01f) {
                    var hh = when (mx) {
                        rr -> 60f * (((gg - bb) / dl) % 6f)
                        gg -> 60f * ((bb - rr) / dl + 2f)
                        else -> 60f * ((rr - gg) / dl + 4f)
                    }
                    if (hh < 0f) hh += 360f
                    val ll = (mx + mn) / 2f
                    var sat = dl / (1f - abs(2f * ll - 1f)).coerceAtLeast(1e-4f)
                    // Which two colour ranges this hue sits between.
                    val (i0, i1, t) = band(hh)
                    val w0 = 1f - t; val w1 = t
                    val dh = (hs[i0] * w0 + hs[i1] * w1) * 30f
                    val dsat = ss[i0] * w0 + ss[i1] * w1
                    val dlum = (ls[i0] * w0 + ls[i1] * w1) * 0.3f * sat.coerceAtMost(1f)
                    hh = (hh + dh + 360f) % 360f
                    sat = (sat * (1f + dsat)).coerceIn(0f, 1f)
                    val nl = (ll + dlum).coerceIn(0f, 1f)
                    val cc = (1f - abs(2f * nl - 1f)) * sat
                    val xx = cc * (1f - abs((hh / 60f) % 2f - 1f))
                    val m = nl - cc / 2f
                    val (r1, g1, b1) = when ((hh / 60f).toInt()) {
                        0 -> Triple(cc, xx, 0f); 1 -> Triple(xx, cc, 0f); 2 -> Triple(0f, cc, xx)
                        3 -> Triple(0f, xx, cc); 4 -> Triple(xx, 0f, cc); else -> Triple(cc, 0f, xx)
                    }
                    r = r1 + m; g = g1 + m; b = b1 + m
                }
            }
            px[i] = pack(c, r, g, b)
        }
    }

    private val bandHue = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f, 360f)

    private data class Band(val a: Int, val b: Int, val t: Float)

    private fun band(h: Float): Band {
        for (k in 0 until 8) {
            if (h < bandHue[k + 1]) {
                val t = (h - bandHue[k]) / (bandHue[k + 1] - bandHue[k])
                return Band(k, (k + 1) % 8, smooth(t))
            }
        }
        return Band(0, 1, 0f)
    }

    // ------------------------------------------------------------------ details

    private fun details(px: IntArray, w: Int, h: Int, s: EditState, side: Float) {
        val st = s.f(Params.structure); val cl = s.f(Params.clarity); val sp = s.f(Params.sharpen)
        if (st != 0f) {
            val fine = blur(px, w, h, max(1f, side / 170f))
            localContrast(px, fine, st * 1.7f, mids = false)
        }
        if (cl != 0f) {
            val med = blur(px, w, h, side / 35f)
            localContrast(px, med, cl * 0.9f, mids = true)
        }
        if (sp > 0f) {
            val one = box(px, w, h, 1, 1)
            localContrast(px, one, sp * 1.6f, mids = false)
        }
    }

    private fun localContrast(px: IntArray, base: IntArray, k: Float, mids: Boolean) {
        for (i in px.indices) {
            val c = px[i]; val b = base[i]
            var r = cr(c); var g = cg(c); var bl = cb(c)
            val kk = if (mids) { val l = lum(r, g, bl); k * (1f - (2f * l - 1f).let { it * it }) } else k
            r += kk * (r - cr(b)); g += kk * (g - cg(b)); bl += kk * (bl - cb(b))
            px[i] = pack(c, r, g, bl)
        }
    }

    // ------------------------------------------------------------------ selective (control points)

    private fun selective(px: IntArray, w: Int, h: Int, s: EditState, side: Float) {
        data class Pt(val x: Float, val y: Float, val rad: Float, val r: Float, val g: Float, val b: Float, val p: CtrlPoint)
        val pts = s.points.map { cp ->
            val o = s.toOut(P(cp.x, cp.y))
            val x = o.x * w; val y = o.y * h
            var r = 0f; var g = 0f; var b = 0f; var n = 0
            for (dy in -3..3) for (dx in -3..3) {
                val xx = (x + dx).toInt().coerceIn(0, w - 1); val yy = (y + dy).toInt().coerceIn(0, h - 1)
                val c = px[yy * w + xx]; r += cr(c); g += cg(c); b += cb(c); n++
            }
            Pt(x, y, cp.size * side / s.crop.w.coerceAtLeast(0.05f), r / n, g / n, b / n, cp)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            var changed = false
            for (p in pts) {
                val dx = x - p.x; val dy = y - p.y
                val d2 = (dx * dx + dy * dy) / (p.rad * p.rad)
                if (d2 > 3f) continue
                val dc = (r - p.r) * (r - p.r) + (g - p.g) * (g - p.g) + (b - p.b) * (b - p.b)
                val wgt = exp(-d2 * 1.4f) * (0.25f + 0.75f * exp(-dc * 9f))
                if (wgt < 0.004f) continue
                changed = true
                val cp = p.p
                if (cp.brightness != 0f) { val d = cp.brightness * 0.45f * wgt; r += d; g += d; b += d }
                if (cp.contrast != 0f) { val k = 1f + cp.contrast * 0.8f * wgt; r = 0.5f + (r - 0.5f) * k; g = 0.5f + (g - 0.5f) * k; b = 0.5f + (b - 0.5f) * k }
                if (cp.saturation != 0f) { val l = lum(r, g, b); val k = 1f + cp.saturation * wgt; r = l + (r - l) * k; g = l + (g - l) * k; b = l + (b - l) * k }
                if (cp.warmth != 0f) { val d = cp.warmth * 0.12f * wgt; r += d; b -= d }
            }
            if (changed) px[i] = pack(c, r, g, b)
        }
    }

    // ------------------------------------------------------------------ brush (local paint)

    /** Paints soft strokes into a map: each pixel gets the level of the last stroke over it. */
    fun paintMask(w: Int, h: Int, strokes: List<Pair<List<P>, Pair<Float, Float>>>): FloatArray {
        val mask = FloatArray(w * h)
        for ((pts, wa) in strokes) {
            val (radius, amount) = wa
            if (pts.isEmpty() || radius < 0.5f) continue
            val xs = pts.map { it.x * w }; val ys = pts.map { it.y * h }
            val x0 = max(0, (xs.min() - radius).toInt()); val x1 = min(w - 1, (xs.max() + radius).toInt() + 1)
            val y0 = max(0, (ys.min() - radius).toInt()); val y1 = min(h - 1, (ys.max() + radius).toInt() + 1)
            if (x0 > x1 || y0 > y1) continue
            val bw = x1 - x0 + 1
            val cov = FloatArray(bw * (y1 - y0 + 1))
            val step = max(1f, radius * 0.25f)
            fun stamp(cx: Float, cy: Float) {
                val sx0 = max(x0, (cx - radius).toInt()); val sx1 = min(x1, (cx + radius).toInt() + 1)
                val sy0 = max(y0, (cy - radius).toInt()); val sy1 = min(y1, (cy + radius).toInt() + 1)
                for (yy in sy0..sy1) for (xx in sx0..sx1) {
                    val d = sqrt((xx - cx) * (xx - cx) + (yy - cy) * (yy - cy)) / radius
                    if (d >= 1f) continue
                    val f = 1f - smooth((d - 0.45f) / 0.55f)
                    val j = (yy - y0) * bw + (xx - x0)
                    if (f > cov[j]) cov[j] = f
                }
            }
            stamp(xs[0], ys[0])
            for (k in 1 until xs.size) {
                val dx = xs[k] - xs[k - 1]; val dy = ys[k] - ys[k - 1]
                val n = max(1, (sqrt(dx * dx + dy * dy) / step).toInt())
                for (t in 1..n) stamp(xs[k - 1] + dx * t / n, ys[k - 1] + dy * t / n)
            }
            for (yy in y0..y1) for (xx in x0..x1) {
                val f = cov[(yy - y0) * bw + (xx - x0)]
                if (f > 0f) { val i = yy * w + xx; mask[i] += (amount - mask[i]) * f }
            }
        }
        return mask
    }

    private fun brush(px: IntArray, w: Int, h: Int, s: EditState, side: Float) {
        val scale = side / s.crop.w.coerceAtLeast(0.05f)
        for (kind in Local.entries) {
            val list = s.masks.filter { it.kind == kind }
            if (list.isEmpty() || list.all { it.amount == 0f }) continue
            val m = paintMask(w, h, list.map { ms -> ms.points.map { s.toOut(it) } to (ms.width * scale to ms.amount) })
            val soft = if (kind == Local.Blur) blur(px, w, h, side / 45f) else null
            for (i in px.indices) {
                val a = m[i]
                if (a == 0f) continue
                val c = px[i]
                var r = cr(c); var g = cg(c); var b = cb(c)
                when (kind) {
                    Local.DodgeBurn -> {
                        if (a > 0f) { val e = 1f + a; r = 1f - (1f - r.coerceIn(0f, 1f)).pow(e); g = 1f - (1f - g.coerceIn(0f, 1f)).pow(e); b = 1f - (1f - b.coerceIn(0f, 1f)).pow(e) }
                        else { val e = 1f - a; r = r.coerceIn(0f, 1f).pow(e); g = g.coerceIn(0f, 1f).pow(e); b = b.coerceIn(0f, 1f).pow(e) }
                    }
                    Local.Exposure -> { val k = 2f.pow(a * 1.3f); r *= k; g *= k; b *= k }
                    Local.Temperature -> { r += 0.16f * a; g += 0.03f * a; b -= 0.16f * a }
                    Local.Saturation -> { val l = lum(r, g, b); val k = 1f + a; r = l + (r - l) * k; g = l + (g - l) * k; b = l + (b - l) * k }
                    Local.Blur -> { val d = soft!![i]; val t = abs(a); r = mix(r, cr(d), t); g = mix(g, cg(d), t); b = mix(b, cb(d), t) }
                }
                px[i] = pack(c, r, g, b)
            }
        }
    }

    // ------------------------------------------------------------------ effects

    private fun effects(input: IntArray, w: Int, h: Int, s: EditState, side: Float): IntArray {
        var px = input
        val p = Params
        run { val k = s.f(p.hdr); if (k > 0f) { hdr(px, w, h, k, side) } }
        run { val k = s.f(p.tonal); if (k > 0f) { val m = blur(px, w, h, side / 55f); tonalPass(px, m, k) } }
        run { val k = s.f(p.drama); if (k > 0f) { drama(px, w, h, k, side) } }
        run { val k = s.f(p.glamour); if (k > 0f) { glamour(px, w, h, k, side) } }
        run { val k = s.f(p.glow); if (k > 0f) { glow(px, w, h, k, side) } }
        run { val k = s.f(p.vintage); if (k > 0f) { vintage(px, w, h, k) } }
        run { val k = s.f(p.retrolux); if (k > 0f) { retrolux(px, w, h, k, side) } }
        run { val k = s.f(p.grunge); if (k > 0f) { grunge(px, w, h, k) } }
        run { val k = s.f(p.bw); if (k > 0f) { bw(px, k, s.f(p.bwFilter).toInt()) } }
        run { val k = s.f(p.noir); if (k > 0f) { noir(px, w, h, k) } }
        run { val k = s.f(p.duotone); if (k > 0f) { duotone(px, k, s.f(p.duoStyle).toInt()) } }
        run { val k = s.f(p.sketch); if (k > 0f) { sketch(px, w, h, k, side) } }
        run { val k = s.f(p.posterize); if (k > 0f) { posterize(px, k) } }
        run { val k = s.f(p.leak); if (k > 0f) { leak(px, w, h, k, s.f(p.leakStyle).toInt()) } }
        run { val k = s.f(p.lensBlur); if (k > 0f) { focusBlur(px, w, h, k, side, s, tilt = false) } }
        run { val k = s.f(p.tilt); if (k > 0f) { focusBlur(px, w, h, k, side, s, tilt = true) } }
        run { val k = s.f(p.blur); if (k > 0f) { px = blur(px, w, h, k * side / 35f) } }
        run { val k = s.f(p.motion); if (k > 0f) { px = box(px, w, h, max(1, (k * side / 30f).roundToInt()), 3, horizontalOnly = true) } }
        run { val k = s.f(p.pixelate); if (k > 0f) { pixelate(px, w, h, k, side) } }
        run { val k = s.f(p.glitch); if (k > 0f) { px = glitch(px, w, h, k, side) } }
        run { val k = s.f(p.vignette); if (k != 0f) { vignette(px, w, h, k, s.f(p.vigSize), s.f(p.vigX), s.f(p.vigY)) } }
        run { val k = s.f(p.grain); if (k > 0f) { grain(px, w, h, k, side) } }
        return px
    }

    private fun hdr(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val base = blur(px, w, h, side / 18f)
        val comp = 1f - 0.55f * k; val boost = 1f + 2.2f * k; val sat = 1f + 0.35f * k
        for (i in px.indices) {
            val c = px[i]; val b = base[i]
            val br = cr(b); val bg = cg(b); val bb = cb(b)
            var r = 0.5f + (br - 0.5f) * comp + (cr(c) - br) * boost
            var g = 0.5f + (bg - 0.5f) * comp + (cg(c) - bg) * boost
            var bl = 0.5f + (bb - 0.5f) * comp + (cb(c) - bb) * boost
            val l = lum(r, g, bl)
            r = l + (r - l) * sat; g = l + (g - l) * sat; bl = l + (bl - l) * sat
            px[i] = pack(c, mix(cr(c), r, k), mix(cg(c), g, k), mix(cb(c), bl, k))
        }
    }

    private fun tonalPass(px: IntArray, m: IntArray, k: Float) {
        for (i in px.indices) {
            val c = px[i]; val b = m[i]
            var r = cr(c); var g = cg(c); var bl = cb(c)
            val l = lum(r, g, bl)
            val kk = k * 1.6f * (0.45f + 0.55f * 4f * l * (1f - l))
            r += kk * (r - cr(b)); g += kk * (g - cg(b)); bl += kk * (bl - cb(b))
            px[i] = pack(c, r, g, bl)
        }
    }

    private fun drama(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val wide = blur(px, w, h, side / 32f)
        val kk = k * 1.9f; val ds = 1f - 0.4f * k
        for (i in px.indices) {
            val c = px[i]; val b = wide[i]
            var r = cr(c); var g = cg(c); var bl = cb(c)
            r += kk * (r - cr(b)); g += kk * (g - cg(b)); bl += kk * (bl - cb(b))
            val l = lum(r, g, bl)
            r = l + (r - l) * ds; g = l + (g - l) * ds; bl = l + (bl - l) * ds
            px[i] = pack(c, r, g, bl)
        }
    }

    private fun glamour(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val soft = blur(px, w, h, side / 40f)
        val a = 0.6f * k
        for (i in px.indices) {
            val c = px[i]; val b = soft[i]
            var r = 1f - (1f - cr(c)) * (1f - a * cr(b)); var g = 1f - (1f - cg(c)) * (1f - a * cg(b)); var bl = 1f - (1f - cb(c)) * (1f - a * cb(b))
            r += 0.04f * k; bl -= 0.03f * k
            val l = lum(r, g, bl); val sd = 1f - 0.18f * k
            r = l + (r - l) * sd; g = l + (g - l) * sd; bl = l + (bl - l) * sd
            px[i] = pack(c, r, g, bl)
        }
    }

    private fun glow(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val wide = blur(px, w, h, side / 30f)
        val a = 0.75f * k
        for (i in px.indices) {
            val c = px[i]; val b = wide[i]
            px[i] = pack(c, 1f - (1f - cr(c)) * (1f - a * cr(b)), 1f - (1f - cg(c)) * (1f - a * cg(b)), 1f - (1f - cb(c)) * (1f - a * cb(b)))
        }
    }

    private fun vintage(px: IntArray, w: Int, h: Int, k: Float) {
        val cx = w / 2f; val cy = h / 2f; val md = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            val r = cr(c); val g = cg(c); val b = cb(c)
            var sr = 0.393f * r + 0.769f * g + 0.189f * b
            var sg = 0.349f * r + 0.686f * g + 0.168f * b
            var sb = 0.272f * r + 0.534f * g + 0.131f * b
            sr = sr * 0.85f + 0.08f; sg = sg * 0.85f + 0.07f; sb = sb * 0.8f + 0.09f
            val t = 0.6f * k
            var nr = mix(r, sr, t); var ng = mix(g, sg, t); var nb = mix(b, sb, t)
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / md
            val v = 1f - 0.45f * k * smooth((d - 0.4f) / 0.6f)
            nr *= v; ng *= v; nb *= v
            px[i] = pack(c, nr, ng, nb)
        }
    }

    private fun retrolux(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val rnd = java.util.Random(11)
        val lines = List(7) { Triple(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.6f, rnd.nextFloat() * h * 0.7f + h * 0.2f) }
        val lw = max(0.7f, side / 900f)
        val diag = sqrt((w * w + h * h).toFloat())
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            val l = lum(r, g, b); val ds = 1f - 0.3f * k
            r = l + (r - l) * ds; g = l + (g - l) * ds; b = l + (b - l) * ds
            r = r * (1f - 0.12f * k) + 0.07f * k; g = g * (1f - 0.12f * k) + 0.05f * k; b = b * (1f - 0.15f * k) + 0.04f * k
            // Warm light leak from the left.
            val t = (1f - sqrt(x * x + (y - h * 0.3f) * (y - h * 0.3f)) / (0.85f * diag)).coerceAtLeast(0f)
            val a = 0.7f * k * t * t
            r = 1f - (1f - r) * (1f - a); g = 1f - (1f - g) * (1f - a * 0.45f); b = 1f - (1f - b) * (1f - a * 0.15f)
            // Old film scratches and dust.
            for ((lx, ly0, len) in lines) if (abs(x - lx - (y - ly0) * 0.02f) < lw && y > ly0 && y < ly0 + len) { r += 0.22f * k; g += 0.2f * k; b += 0.18f * k }
            if (hash(x / max(1, (side / 500f).toInt()), y / max(1, (side / 500f).toInt()), 3) < 0.0008f * k) { r *= 0.4f; g *= 0.4f; b *= 0.4f }
            px[i] = pack(c, r, g, b)
        }
    }

    private fun noise2(x: Float, y: Float, seed: Int): Float {
        val x0 = x.toInt(); val y0 = y.toInt(); val tx = smooth(x - x0); val ty = smooth(y - y0)
        val a = hash(x0, y0, seed); val b = hash(x0 + 1, y0, seed); val c = hash(x0, y0 + 1, seed); val d = hash(x0 + 1, y0 + 1, seed)
        return mix(mix(a, b, tx), mix(c, d, tx), ty)
    }

    private fun grunge(px: IntArray, w: Int, h: Int, k: Float) {
        val sx = 18f / w; val sy = 18f * (h.toFloat() / w) / h
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            val n = 0.6f * noise2(x * sx, y * sy, 5) + 0.4f * noise2(x * sx * 5f, y * sy * 5f, 9)
            val tex = 0.65f + 0.7f * n
            val l = lum(r, g, b); val ds = 1f - 0.5f * k
            r = l + (r - l) * ds; g = l + (g - l) * ds; b = l + (b - l) * ds
            r = 0.5f + (r - 0.5f) * (1f + 0.35f * k); g = 0.5f + (g - 0.5f) * (1f + 0.35f * k); b = 0.5f + (b - 0.5f) * (1f + 0.35f * k)
            val t = mix(1f, tex, k)
            r *= t * (1f + 0.05f * k); g *= t * (1f + 0.04f * k); b *= t * (1f - 0.08f * k)
            px[i] = pack(c, mix(cr(c), r, k), mix(cg(c), g, k), mix(cb(c), b, k))
        }
    }

    private val bwWeights = arrayOf(
        floatArrayOf(0.299f, 0.587f, 0.114f), floatArrayOf(0.70f, 0.25f, 0.05f), floatArrayOf(0.55f, 0.38f, 0.07f),
        floatArrayOf(0.42f, 0.50f, 0.08f), floatArrayOf(0.20f, 0.70f, 0.10f), floatArrayOf(0.12f, 0.30f, 0.58f),
    )

    private fun bw(px: IntArray, k: Float, f: Int) {
        val wt = bwWeights[f.coerceIn(0, bwWeights.lastIndex)]
        for (i in px.indices) {
            val c = px[i]; val r = cr(c); val g = cg(c); val b = cb(c)
            val l = r * wt[0] + g * wt[1] + b * wt[2]
            px[i] = pack(c, mix(r, l, k), mix(g, l, k), mix(b, l, k))
        }
    }

    private fun noir(px: IntArray, w: Int, h: Int, k: Float) {
        val cx = w / 2f; val cy = h / 2f; val md = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            val r = cr(c); val g = cg(c); val b = cb(c)
            var l = lum(r, g, b).coerceIn(0f, 1f)
            l = mix(l, l * l * (3f - 2f * l), 0.9f)
            l = mix(l, l * l * (3f - 2f * l), 0.6f)
            l = l * 0.92f + 0.05f
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / md
            l *= 1f - 0.55f * smooth((d - 0.3f) / 0.7f)
            l += (hash(x, y, 21) - 0.5f) * 0.06f
            px[i] = pack(c, mix(r, l, k), mix(g, l, k), mix(b, l, k))
        }
    }

    private val duos = arrayOf(
        intArrayOf(0x102A6B, 0x40E0D0), intArrayOf(0x2D054A, 0xFF6FB5), intArrayOf(0x0F3D2E, 0xD4FF3A),
        intArrayOf(0x400010, 0xFFC13B), intArrayOf(0x1B263B, 0xE0F4FF), intArrayOf(0x241445, 0x7CFFCB),
    )

    private fun duotone(px: IntArray, k: Float, style: Int) {
        val (a, b) = duos[style.coerceIn(0, duos.lastIndex)].let { it[0] to it[1] }
        val ar = cr(a); val ag = cg(a); val ab = cb(a); val br = cr(b); val bg = cg(b); val bb = cb(b)
        for (i in px.indices) {
            val c = px[i]; val r = cr(c); val g = cg(c); val bl = cb(c)
            val l = smooth(lum(r, g, bl))
            px[i] = pack(c, mix(r, mix(ar, br, l), k), mix(g, mix(ag, bg, l), k), mix(bl, mix(ab, bb, l), k))
        }
    }

    private fun sketch(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val gray = IntArray(px.size) { val l = ch(lumOf(px[it])); (0xFF shl 24) or (l shl 16) or (l shl 8) or l }
        val bl = blur(gray, w, h, max(1.5f, side / 140f))
        for (i in px.indices) {
            val c = px[i]
            val l = (gray[i] and 0xFF) / 255f; val lb = (bl[i] and 0xFF) / 255f
            var s = (l / lb.coerceAtLeast(0.02f)).coerceIn(0f, 1f)
            s = s.pow(2.2f)
            px[i] = pack(c, mix(cr(c), s, k), mix(cg(c), s, k), mix(cb(c), s, k))
        }
    }

    private fun posterize(px: IntArray, k: Float) {
        val n = (16f - 13f * k).roundToInt().coerceAtLeast(2) - 1
        val sat = 1f + 0.5f * k
        for (i in px.indices) {
            val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            val l = lum(r, g, b); r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat
            r = (r.coerceIn(0f, 1f) * n).roundToInt() / n.toFloat(); g = (g.coerceIn(0f, 1f) * n).roundToInt() / n.toFloat(); b = (b.coerceIn(0f, 1f) * n).roundToInt() / n.toFloat()
            px[i] = pack(c, r, g, b)
        }
    }

    private fun leak(px: IntArray, w: Int, h: Int, k: Float, style: Int) {
        val diag = sqrt((w * w + h * h).toFloat())
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            val fx = x.toFloat() / w; val fy = y.toFloat() / h
            var lr: Float; var lg: Float; var lb: Float; val t: Float
            when (style) {
                1 -> { // rainbow band across the top
                    val hh = fx * 6f
                    lr = (abs(hh - 3f) - 1f).coerceIn(0f, 1f); lg = (2f - abs(hh - 2f)).coerceIn(0f, 1f); lb = (2f - abs(hh - 4f)).coerceIn(0f, 1f)
                    t = (1f - fy / 0.55f).coerceAtLeast(0f).let { it * it }
                }
                2 -> { lr = 1f; lg = 0.35f; lb = 0.6f; t = (1f - fx / 0.7f).coerceAtLeast(0f).let { it * it } }
                3 -> { lr = 1f; lg = 0.8f; lb = 0.3f; t = (1f - fy / 0.6f).coerceAtLeast(0f).let { it * it * (0.6f + 0.4f * fx) } }
                else -> {
                    lr = 1f; lg = 0.42f; lb = 0.12f
                    val d = sqrt((w - x).toFloat() * (w - x) + (y.toFloat() * y)) / (0.9f * diag)
                    t = (1f - d).coerceAtLeast(0f).let { it * it }
                }
            }
            val a = 0.9f * k * t
            px[i] = pack(c, 1f - (1f - cr(c)) * (1f - a * lr), 1f - (1f - cg(c)) * (1f - a * lg), 1f - (1f - cb(c)) * (1f - a * lb))
        }
    }

    private fun focusBlur(px: IntArray, w: Int, h: Int, k: Float, side: Float, s: EditState, tilt: Boolean) {
        val soft = blur(px, w, h, side / 26f * (0.5f + k))
        val cx = s.f(Params.lensX) * w; val cy = (if (tilt) s.f(Params.tiltY) else s.f(Params.lensY)) * h
        val size = if (tilt) s.f(Params.tiltSize) else s.f(Params.lensSize)
        for (y in 0 until h) for (x in 0 until w) {
            val d = if (tilt) abs(y - cy) / h else sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / side
            val t = smooth((d - size * 0.5f) / (size * 0.9f + 0.08f)) * min(1f, k * 1.4f)
            if (t <= 0.001f) continue
            val i = y * w + x; val c = px[i]; val b = soft[i]
            var br = cr(b); var bg = cg(b); var bb = cb(b)
            // A little extra brightness in bright spots looks like real lens "bokeh".
            val l = lum(br, bg, bb)
            if (l > 0.7f) { val e = 1f + (l - 0.7f) * 1.2f * k; br *= e; bg *= e; bb *= e }
            px[i] = pack(c, mix(cr(c), br, t), mix(cg(c), bg, t), mix(cb(c), bb, t))
        }
    }

    private fun pixelate(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val bs = max(2, (k * side / 18f).roundToInt())
        var y0 = 0
        while (y0 < h) {
            var x0 = 0
            val y1 = min(h, y0 + bs)
            while (x0 < w) {
                val x1 = min(w, x0 + bs)
                var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (y in y0 until y1) for (x in x0 until x1) { val c = px[y * w + x]; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++ }
                val col = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
                for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = col
                x0 = x1
            }
            y0 = y1
        }
    }

    private fun glitch(src: IntArray, w: Int, h: Int, k: Float, side: Float): IntArray {
        val out = IntArray(src.size)
        val dx = (k * side * 0.014f).roundToInt()
        val bands = 28
        for (y in 0 until h) {
            val band = y * bands / h
            val rnd = hash(band, 7, 13)
            val off = if (rnd < 0.35f * k) ((hash(band, 3, 17) - 0.5f) * k * side * 0.12f).roundToInt() else 0
            val dark = if (y % 3 == 0) 1f - 0.12f * k else 1f
            for (x in 0 until w) {
                val rx = (x + dx + off).coerceIn(0, w - 1); val gx = (x + off).coerceIn(0, w - 1); val bx = (x - dx + off).coerceIn(0, w - 1)
                val row = y * w
                out[row + x] = pack(src[row + x], cr(src[row + rx]) * dark, cg(src[row + gx]) * dark, cb(src[row + bx]) * dark)
            }
        }
        return out
    }

    private fun vignette(px: IntArray, w: Int, h: Int, k: Float, size: Float, vx: Float, vy: Float) {
        val cx = vx * w; val cy = vy * h
        val md = sqrt((w * w + h * h).toFloat()) / 2f
        val inner = size * 0.75f
        for (y in 0 until h) for (x in 0 until w) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / md
            val t = smooth((d - inner) / (1.15f - inner).coerceAtLeast(0.1f))
            if (t <= 0f) continue
            val i = y * w + x; val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            if (k < 0f) { val f = 1f + k * 0.9f * t; r *= f; g *= f; b *= f }
            else { val f = k * 0.9f * t; r += (1f - r) * f; g += (1f - g) * f; b += (1f - b) * f }
            px[i] = pack(c, r, g, b)
        }
    }

    private fun grain(px: IntArray, w: Int, h: Int, k: Float, side: Float) {
        val cell = max(1, (side / 1100f).roundToInt())
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            var r = cr(c); var g = cg(c); var b = cb(c)
            val l = lum(r, g, b)
            val n = (hash(x / cell, y / cell, 1) + hash(x / cell + 1, y / cell, 1) - 1f) * k * 0.2f * (0.4f + 2.4f * l * (1f - l))
            r += n; g += n; b += n
            px[i] = pack(c, r, g, b)
        }
    }

    // ------------------------------------------------------------------ auto

    /** One-tap fix: looks at how bright and punchy the picture is and nudges it towards balanced. */
    fun auto(px: IntArray): List<Pair<Param, Float>> {
        var sum = 0f; var sumSq = 0f; var sat = 0f
        var rs = 0f; var bs = 0f
        for (c in px) {
            val r = cr(c); val g = cg(c); val b = cb(c)
            val l = lum(r, g, b)
            sum += l; sumSq += l * l
            sat += max(r, max(g, b)) - min(r, min(g, b))
            rs += r; bs += b
        }
        val n = px.size.toFloat().coerceAtLeast(1f)
        val mean = sum / n
        val sd = sqrt((sumSq / n - mean * mean).coerceAtLeast(0f))
        val avgSat = sat / n
        val cast = (rs - bs) / n
        return listOf(
            Params.brightness to ((0.47f - mean) * 1.1f).coerceIn(-0.4f, 0.4f),
            Params.contrast to ((0.22f - sd) * 2f).coerceIn(-0.15f, 0.35f),
            Params.shadows to if (mean < 0.4f) 0.35f else 0.15f,
            Params.highlights to if (mean > 0.6f) -0.35f else -0.15f,
            Params.vibrance to ((0.32f - avgSat) * 2f).coerceIn(0f, 0.45f),
            Params.warmth to (-cast * 1.2f).coerceIn(-0.25f, 0.25f),
            Params.structure to 0.12f,
        )
    }
}
