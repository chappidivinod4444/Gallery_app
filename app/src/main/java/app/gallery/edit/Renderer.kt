package app.gallery.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Turns the original picture plus an [EditState] into the finished picture. */
object Renderer {

    /** Size of the picture after 90° turns (before cropping). */
    fun rotatedSize(w: Int, h: Int, s: EditState): Pair<Int, Int> = if (s.rotation % 180 == 0) w to h else h to w

    /** How much to enlarge a straightened picture so no empty corners show. */
    fun straightenScale(rw: Int, rh: Int, degrees: Float): Float {
        val a = Math.toRadians(abs(degrees).toDouble())
        val c = cos(a); val sn = sin(a)
        return max((rw * c + rh * sn) / rw, (rw * sn + rh * c) / rh).toFloat()
    }

    fun render(src: Bitmap, s: EditState, withOverlays: Boolean = true): Bitmap {
        val (rw, rh) = rotatedSize(src.width, src.height, s)
        val ow = (rw * s.crop.w).roundToInt().coerceAtLeast(1)
        val oh = (rh * s.crop.h).roundToInt().coerceAtLeast(1)

        // 1. Turn, flip, straighten, crop — and colour — in one drawing pass.
        val m = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postScale(if (s.flipH) -1f else 1f, if (s.flipV) -1f else 1f)
            postRotate(s.rotation.toFloat())
            if (s.straighten != 0f) {
                postRotate(s.straighten)
                val k = straightenScale(rw, rh, s.straighten)
                postScale(k, k)
            }
            postTranslate(rw / 2f, rh / 2f)
            postTranslate(-s.crop.l * rw, -s.crop.t * rh)
        }
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix(s))
        canvas.drawBitmap(src, m, paint)

        // 2. Effects that need each pixel: light/shadow recovery, vibrance, sharpen, blur, vignette, grain.
        if (s.adjust.needsPixels) pixelPass(out, s.adjust)

        if (!withOverlays) return out

        // 3. Drawings, text and stickers on top.
        drawOverlays(canvas, ow, oh, s)
        if (s.watermark.isNotBlank()) drawWatermark(canvas, ow, oh, s.watermark)

        // 4. Frame around the picture.
        return applyFrame(out, s.frame)
    }

    fun colorMatrix(s: EditState): ColorMatrix {
        val a = s.adjust
        val cm = ColorMatrix()
        val e = Math.pow(2.0, a.exposure.toDouble()).toFloat()
        cm.postConcat(scale(e, e, e, 0f))
        if (a.brightness != 0f) cm.postConcat(scale(1f, 1f, 1f, a.brightness * 60f))
        if (a.contrast != 0f) cm.postConcat(contrast(1f + a.contrast * 0.6f))
        if (a.saturation != 0f) cm.postConcat(ColorMatrix().apply { setSaturation(1f + a.saturation) })
        if (a.warmth != 0f) cm.postConcat(scale(1f + 0.12f * a.warmth, 1f, 1f - 0.12f * a.warmth, 0f))
        if (a.tint != 0f) cm.postConcat(scale(1f + 0.04f * a.tint, 1f - 0.10f * a.tint, 1f + 0.04f * a.tint, 0f))
        if (a.fade != 0f) {
            cm.postConcat(contrast(1f - 0.25f * a.fade))
            cm.postConcat(scale(1f, 1f, 1f, 30f * a.fade))
        }
        if (s.filter != "Original") cm.postConcat(Filters.blended(s.filter, s.filterStrength))
        return cm
    }

    private fun scale(r: Float, g: Float, b: Float, add: Float) = ColorMatrix(
        floatArrayOf(r, 0f, 0f, 0f, add, 0f, g, 0f, 0f, add, 0f, 0f, b, 0f, add, 0f, 0f, 0f, 1f, 0f)
    )

    private fun contrast(c: Float): ColorMatrix {
        val t = (1f - c) * 128f
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    private fun pixelPass(bmp: Bitmap, a: Adjust) {
        val w = bmp.width
        val h = bmp.height
        var px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        if (a.blur > 0f) {
            val r = (a.blur * min(w, h) / 45f).roundToInt().coerceAtLeast(1)
            repeat(3) { px = boxBlur(px, w, h, r) }
        }
        if (a.sharpen > 0f) px = sharpen(px, w, h, a.sharpen * 1.4f)
        val side = min(w, h)
        // Blurred copies used by the effects below (only made when needed).
        val fine = if (a.structure > 0f) blurred(px, w, h, (side / 110f).roundToInt().coerceAtLeast(1)) else null
        val wide = if (a.drama > 0f || a.glow > 0f) blurred(px, w, h, (side / 35f).roundToInt().coerceAtLeast(2)) else null
        val soft = if (a.lensBlur > 0f || a.tiltShift > 0f) blurred(px, w, h, (side / 28f).roundToInt().coerceAtLeast(2)) else null
        if (fine != null || wide != null || soft != null) {
            val cxx = w / 2f; val cyy = h / 2f
            val md = sqrt(cxx * cxx + cyy * cyy)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                var r = ((c shr 16) and 0xFF) / 255f; var g = ((c shr 8) and 0xFF) / 255f; var b = (c and 0xFF) / 255f
                if (fine != null) {
                    val f = fine[i]; val k = a.structure * 1.6f
                    r += k * (r - ((f shr 16) and 0xFF) / 255f); g += k * (g - ((f shr 8) and 0xFF) / 255f); b += k * (b - (f and 0xFF) / 255f)
                }
                if (wide != null) {
                    val wr = ((wide[i] shr 16) and 0xFF) / 255f; val wg = ((wide[i] shr 8) and 0xFF) / 255f; val wb = (wide[i] and 0xFF) / 255f
                    if (a.drama > 0f) {
                        val k = a.drama * 1.8f
                        r += k * (r - wr); g += k * (g - wg); b += k * (b - wb)
                        val l = 0.299f * r + 0.587f * g + 0.114f * b
                        val ds = 1f - 0.35f * a.drama
                        r = l + (r - l) * ds; g = l + (g - l) * ds; b = l + (b - l) * ds
                    }
                    if (a.glow > 0f) {
                        val k = a.glow * 0.75f
                        r = 1f - (1f - r) * (1f - k * wr); g = 1f - (1f - g) * (1f - k * wg); b = 1f - (1f - b) * (1f - k * wb)
                    }
                }
                if (soft != null) {
                    var t = 0f
                    if (a.lensBlur > 0f) {
                        val dx = x - cxx; val dy = y - cyy
                        val d = sqrt(dx * dx + dy * dy) / md
                        val e = ((d - 0.22f) / 0.5f).coerceIn(0f, 1f)
                        t = max(t, a.lensBlur * e * e * (3 - 2 * e))
                    }
                    if (a.tiltShift > 0f) {
                        val d = abs(y - cyy) / cyy
                        val e = ((d - 0.25f) / 0.5f).coerceIn(0f, 1f)
                        t = max(t, a.tiltShift * e * e * (3 - 2 * e))
                    }
                    if (t > 0f) {
                        val s0 = soft[i]
                        r += (((s0 shr 16) and 0xFF) / 255f - r) * t
                        g += (((s0 shr 8) and 0xFF) / 255f - g) * t
                        b += ((s0 and 0xFF) / 255f - b) * t
                    }
                }
                px[i] = (c and -0x1000000) or
                    ((r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
                    ((g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
                    (b.coerceIn(0f, 1f) * 255f).roundToInt()
            }
        }
        val rnd = java.util.Random(7)
        val cx = w / 2f; val cy = h / 2f
        val maxD = sqrt(cx * cx + cy * cy)
        val doTone = a.highlights != 0f || a.shadows != 0f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                var r = ((c shr 16) and 0xFF) / 255f
                var g = ((c shr 8) and 0xFF) / 255f
                var b = (c and 0xFF) / 255f
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                if (doTone) {
                    val ws = (1f - l) * (1f - l)
                    val wh = l * l
                    val d = a.shadows * 0.35f * ws + a.highlights * 0.35f * wh
                    r += d; g += d; b += d
                }
                if (a.vibrance != 0f) {
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val amt = a.vibrance * (1f - (mx - mn)) * 0.8f
                    val ll = 0.299f * r + 0.587f * g + 0.114f * b
                    r = ll + (r - ll) * (1f + amt); g = ll + (g - ll) * (1f + amt); b = ll + (b - ll) * (1f + amt)
                }
                if (a.vignette > 0f) {
                    val dx = x - cx; val dy = y - cy
                    val d = sqrt(dx * dx + dy * dy) / maxD
                    val t = ((d - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val f = 1f - a.vignette * 0.85f * (t * t * (3 - 2 * t))
                    r *= f; g *= f; b *= f
                }
                if (a.grain > 0f) {
                    val n = (rnd.nextFloat() - 0.5f) * a.grain * 0.22f
                    r += n; g += n; b += n
                }
                px[i] = (c and -0x1000000) or
                    ((r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
                    ((g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
                    (b.coerceIn(0f, 1f) * 255f).roundToInt()
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun blurred(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var out = boxBlur(src, w, h, r)
        out = boxBlur(out, w, h, r)
        return boxBlur(out, w, h, r)
    }

    /** Fast blur: averages each pixel with its neighbours (run a few times for a smooth result). */
    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        val div = 2 * r + 1
        for (y in 0 until h) {
            var sr = 0; var sg = 0; var sb = 0
            val row = y * w
            for (k in -r..r) {
                val c = src[row + k.coerceIn(0, w - 1)]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (src[row + x] and -0x1000000) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = src[row + (x - r).coerceAtLeast(0)]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = tmp[k.coerceIn(0, h - 1) * w + x]
                sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = (tmp[y * w + x] and -0x1000000) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
        return out
    }

    private fun sharpen(src: IntArray, w: Int, h: Int, k: Float): IntArray {
        val out = src.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val c = src[i]; val n = src[i - w]; val s = src[i + w]; val e = src[i + 1]; val wv = src[i - 1]
                fun ch(sh: Int): Int {
                    val v = ((c shr sh) and 0xFF) * (1f + 4f * k) - k * (((n shr sh) and 0xFF) + ((s shr sh) and 0xFF) + ((e shr sh) and 0xFF) + ((wv shr sh) and 0xFF))
                    return v.roundToInt().coerceIn(0, 255)
                }
                out[i] = (c and -0x1000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        return out
    }

    // ---- overlays

    fun typeface(f: Font): Typeface = when (f) {
        Font.Sans -> Typeface.DEFAULT
        Font.Bold -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        Font.Serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        Font.Mono -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        Font.Hand -> Typeface.create("cursive", Typeface.BOLD)
    }

    fun drawOverlays(canvas: Canvas, w: Int, h: Int, s: EditState) {
        val side = min(w, h).toFloat()
        for (st in s.strokes) drawStroke(canvas, w, h, side, st)
        for (t in s.texts) {
            canvas.save(); canvas.rotate(t.rotation, t.x * w, t.y * h)
            drawText(canvas, w, h, side, t)
            canvas.restore()
        }
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        for (st in s.stickers) {
            ep.textSize = st.size * side
            val fm = ep.fontMetrics
            canvas.save(); canvas.rotate(st.rotation, st.x * w, st.y * h)
            canvas.drawText(st.emoji, st.x * w, st.y * h - (fm.ascent + fm.descent) / 2f, ep)
            canvas.restore()
        }
    }

    private fun drawStroke(canvas: Canvas, w: Int, h: Int, side: Float, st: Stroke) {
        if (st.points.isEmpty() || st.brush == Brush.Eraser) return
        val path = Path()
        st.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x * w, p.y * h)
            else {
                val prev = st.points[i - 1]
                path.quadTo(prev.x * w, prev.y * h, (prev.x + p.x) / 2f * w, (prev.y + p.y) / 2f * h)
            }
        }
        if (st.points.size == 1) path.lineTo(st.points[0].x * w + 0.1f, st.points[0].y * h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = if (st.brush == Brush.Marker) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeWidth = st.width * side
            color = st.color
        }
        when (st.brush) {
            Brush.Marker -> {
                paint.alpha = 110
                paint.strokeWidth = st.width * side * 2.2f
                canvas.drawPath(path, paint)
            }
            Brush.Neon -> {
                paint.setShadowLayer(st.width * side * 1.6f, 0f, 0f, st.color)
                canvas.drawPath(path, paint)
                paint.clearShadowLayer()
                paint.color = Color.WHITE
                paint.strokeWidth = st.width * side * 0.35f
                canvas.drawPath(path, paint)
            }
            else -> canvas.drawPath(path, paint)
        }
    }

    /** Width and height of a text layer as fractions of the picture's shorter side (for the selection box). */
    fun textBounds(t: TextLayer): Pair<Float, Float> {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = t.size * 1000f; typeface = typeface(t.font) }
        val lines = t.text.split('\n')
        val fm = p.fontMetrics
        val w = (lines.maxOfOrNull { p.measureText(it) } ?: 0f) / 1000f
        val h = (fm.descent - fm.ascent) * lines.size / 1000f
        return (w + t.size * 0.6f) to (h + t.size * 0.4f)
    }

    private fun drawText(canvas: Canvas, w: Int, h: Int, side: Float, t: TextLayer) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t.size * side
            color = t.color
            typeface = typeface(t.font)
            textAlign = Paint.Align.CENTER
            if (!t.background) setShadowLayer(textSize * 0.08f, 0f, textSize * 0.03f, 0x66000000)
        }
        val lines = t.text.split('\n')
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val total = lineH * lines.size
        val top = t.y * h - total / 2f
        if (t.background) {
            val maxW = lines.maxOf { paint.measureText(it) }
            val pad = paint.textSize * 0.35f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (Color.luminance(t.color) > 0.5f) 0xB3000000.toInt() else 0xE6FFFFFF.toInt()
            }
            canvas.drawRoundRect(
                RectF(t.x * w - maxW / 2 - pad, top - pad * 0.6f, t.x * w + maxW / 2 + pad, top + total + pad * 0.6f),
                pad, pad, bg,
            )
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, t.x * w, top + i * lineH - fm.ascent, paint) }
    }

    private fun drawWatermark(canvas: Canvas, w: Int, h: Int, text: String) {
        val side = min(w, h).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = side * 0.035f
            color = 0xD9FFFFFF.toInt()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(textSize * 0.15f, 0f, 0f, 0x99000000.toInt())
        }
        canvas.drawText(text, w - side * 0.03f, h - side * 0.03f, p)
    }

    fun applyFrame(img: Bitmap, frame: Frame): Bitmap {
        if (frame == Frame.None) return img
        val side = min(img.width, img.height).toFloat()
        val (l, t, r, b, color) = when (frame) {
            Frame.ThinWhite -> Quint(0.03f, 0.03f, 0.03f, 0.03f, Color.WHITE)
            Frame.ThickWhite -> Quint(0.08f, 0.08f, 0.08f, 0.08f, Color.WHITE)
            Frame.Black -> Quint(0.06f, 0.06f, 0.06f, 0.06f, Color.BLACK)
            Frame.Polaroid -> Quint(0.05f, 0.05f, 0.05f, 0.20f, 0xFFFAFAF7.toInt())
            Frame.Rounded -> Quint(0.04f, 0.04f, 0.04f, 0.04f, Color.WHITE)
            Frame.None -> Quint(0f, 0f, 0f, 0f, 0)
        }
        val out = Bitmap.createBitmap(
            (img.width + (l + r) * side).roundToInt(), (img.height + (t + b) * side).roundToInt(), Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(out)
        c.drawColor(color)
        val dst = RectF(l * side, t * side, l * side + img.width, t * side + img.height)
        if (frame == Frame.Rounded) {
            val clip = Path().apply { addRoundRect(dst, side * 0.06f, side * 0.06f, Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(img, null, dst, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
        } else {
            c.drawBitmap(img, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private data class Quint(val a: Float, val b: Float, val c: Float, val d: Float, val e: Int)

    /** One-tap fix: looks at how bright and punchy the picture is and nudges it towards balanced. */
    fun autoEnhance(bmp: Bitmap): Adjust {
        val small = Bitmap.createScaledBitmap(bmp, 64, (64f * bmp.height / bmp.width).roundToInt().coerceAtLeast(1), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        var sum = 0f; var sumSq = 0f; var sat = 0f
        for (c in px) {
            val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            sum += l; sumSq += l * l
            sat += max(r, max(g, b)) - min(r, min(g, b))
        }
        val n = px.size.toFloat()
        val mean = sum / n
        val sd = sqrt((sumSq / n - mean * mean).coerceAtLeast(0f))
        val avgSat = sat / n
        return Adjust(
            exposure = ((0.48f - mean) * 1.4f).coerceIn(-0.6f, 0.6f),
            contrast = ((0.22f - sd) * 2.2f).coerceIn(-0.2f, 0.45f),
            shadows = if (mean < 0.4f) 0.35f else 0.12f,
            highlights = if (mean > 0.6f) -0.35f else -0.12f,
            vibrance = ((0.30f - avgSat) * 2f).coerceIn(0f, 0.5f),
            sharpen = 0.15f,
        )
    }
}
