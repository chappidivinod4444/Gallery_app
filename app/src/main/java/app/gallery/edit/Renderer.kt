package app.gallery.edit

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Turns the original picture plus an [EditState] into the finished picture. */
object Renderer {

    /** Size of the picture after 90° turns (before cropping). */
    fun rotatedSize(w: Int, h: Int, s: EditState): Pair<Int, Int> = if (s.rotation % 180 == 0) w to h else h to w

    /**
     * Source pixels → frame pixels (rw × rh): flip, turn, straighten, fix perspective, then zoom in just enough
     * that no empty corners show.
     */
    fun frameMatrix(sw: Int, sh: Int, s: EditState): Matrix {
        val (rw0, rh0) = rotatedSize(sw, sh, s)
        val rw = rw0.toFloat(); val rh = rh0.toFloat()
        val m = Matrix().apply {
            postTranslate(-sw / 2f, -sh / 2f)
            postScale(if (s.flipH) -1f else 1f, if (s.flipV) -1f else 1f)
            postRotate(s.rotation + s.straighten)
            postTranslate(rw / 2f, rh / 2f)
        }
        val pv = s.f(Params.pv); val ph = s.f(Params.ph)
        if (pv != 0f || ph != 0f) {
            val d = 0.25f
            val tx = max(0f, pv) * d * rw; val bx = max(0f, -pv) * d * rw
            val ly = max(0f, ph) * d * rh; val ry = max(0f, -ph) * d * rh
            val src = floatArrayOf(0f, 0f, rw, 0f, rw, rh, 0f, rh)
            val dst = floatArrayOf(-tx, -ly, rw + tx, -ry, rw + bx, rh + ry, -bx, rh + ly)
            val p = Matrix(); p.setPolyToPoly(src, 0, dst, 0, 4)
            m.postConcat(p)
        }
        val q = floatArrayOf(0f, 0f, sw.toFloat(), 0f, sw.toFloat(), sh.toFloat(), 0f, sh.toFloat())
        m.mapPoints(q)
        val k = coverScale(q, rw, rh)
        if (k != 1f) m.postScale(k, k, rw / 2f, rh / 2f)
        return m
    }

    /** Smallest zoom of the quad [q] (about the frame centre) that covers the whole rw × rh frame. */
    private fun coverScale(q: FloatArray, rw: Float, rh: Float): Float {
        val cx = rw / 2f; val cy = rh / 2f
        val corners = floatArrayOf(0f, 0f, rw, 0f, rw, rh, 0f, rh)
        fun covers(k: Float): Boolean {
            var sign = 0
            for (c in 0 until 4) {
                val px = corners[c * 2]; val py = corners[c * 2 + 1]
                for (e in 0 until 4) {
                    val ax = cx + (q[e * 2] - cx) * k; val ay = cy + (q[e * 2 + 1] - cy) * k
                    val n = (e + 1) % 4
                    val bx = cx + (q[n * 2] - cx) * k; val by = cy + (q[n * 2 + 1] - cy) * k
                    val cross = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
                    val sg = if (cross > 0.01f) 1 else if (cross < -0.01f) -1 else 0
                    if (sg != 0) { if (sign == 0) sign = sg else if (sg != sign) return false }
                }
            }
            return true
        }
        if (covers(1f)) return 1f
        var lo = 1f; var hi = 6f
        repeat(28) { val mid = (lo + hi) / 2f; if (covers(mid)) hi = mid else lo = mid }
        return hi
    }

    /**
     * Draws the finished picture. [photos] gives the bitmaps of added photo layers. Without [overlays] only the
     * picture itself is drawn; without [frame] the border is left off.
     */
    fun render(
        src: Bitmap, s: EditState, photos: (String) -> Bitmap? = { null },
        overlays: Boolean = true, frame: Boolean = true,
    ): Bitmap {
        val (rw, rh) = rotatedSize(src.width, src.height, s)
        val ow = (rw * s.crop.w).roundToInt().coerceAtLeast(1)
        val oh = (rh * s.crop.h).roundToInt().coerceAtLeast(1)
        val m = frameMatrix(src.width, src.height, s)
        m.postTranslate(-s.crop.l * rw, -s.crop.t * rh)

        // 1. Geometry and the simple colour changes in one drawing pass.
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix(s))
        canvas.drawBitmap(src, m, paint)

        // 2. Healing, then everything that works pixel by pixel.
        if (s.heals.isNotEmpty() || needsPixels(s)) {
            var px = IntArray(ow * oh)
            out.getPixels(px, 0, ow, 0, 0, ow, oh)
            if (s.heals.isNotEmpty()) Heal.apply(px, ow, oh, s)
            px = Pixels.process(px, ow, oh, s)
            out.setPixels(px, 0, ow, 0, 0, ow, oh)
        }

        if (overlays) {
            for (l in s.photos) photos(l.uri)?.let { drawPhoto(canvas, ow, oh, l, it) }
            drawOverlays(canvas, ow, oh, s)
            if (s.watermark.isNotBlank()) drawWatermark(canvas, ow, oh, s.watermark)
        }
        return if (frame) applyFrame(out, s) else out
    }

    private val colorOnly = setOf(
        Params.exposure.key, Params.brightness.key, Params.contrast.key, Params.saturation.key, Params.warmth.key,
        Params.tint.key, Params.temp.key, Params.wbTint.key, Params.pv.key, Params.ph.key, Params.frameSize.key,
    )

    private fun needsPixels(s: EditState) =
        s.v.keys.any { it !in colorOnly } || !s.curves.isIdentity || s.points.isNotEmpty() || s.masks.isNotEmpty()

    fun colorMatrix(s: EditState): ColorMatrix {
        val cm = ColorMatrix()
        val e = Math.pow(2.0, s.f(Params.exposure).toDouble()).toFloat()
        if (e != 1f) cm.postConcat(scale(e, e, e, 0f))
        s.f(Params.brightness).let { if (it != 0f) { cm.postConcat(scale(1f + 0.25f * it, 1f + 0.25f * it, 1f + 0.25f * it, it * 30f)) } }
        s.f(Params.contrast).let { if (it != 0f) cm.postConcat(contrast(1f + it * 0.55f)) }
        s.f(Params.saturation).let { if (it != 0f) cm.postConcat(ColorMatrix().apply { setSaturation(1f + it) }) }
        val warm = s.f(Params.warmth) + s.f(Params.temp)
        if (warm != 0f) cm.postConcat(scale(1f + 0.12f * warm, 1f + 0.01f * warm, 1f - 0.12f * warm, 0f))
        val tint = s.f(Params.tint) + s.f(Params.wbTint)
        if (tint != 0f) cm.postConcat(scale(1f + 0.05f * tint, 1f - 0.11f * tint, 1f + 0.05f * tint, 0f))
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

    // ---- photo layers

    fun blendMode(b: Blend): BlendMode = when (b) {
        Blend.Normal -> BlendMode.SRC_OVER; Blend.Screen -> BlendMode.SCREEN; Blend.Multiply -> BlendMode.MULTIPLY
        Blend.Overlay -> BlendMode.OVERLAY; Blend.SoftLight -> BlendMode.SOFT_LIGHT; Blend.Lighten -> BlendMode.LIGHTEN
        Blend.Darken -> BlendMode.DARKEN; Blend.Difference -> BlendMode.DIFFERENCE; Blend.Color -> BlendMode.COLOR
        Blend.Luminosity -> BlendMode.LUMINOSITY
    }

    fun drawPhoto(canvas: Canvas, w: Int, h: Int, l: PhotoLayer, bmp: Bitmap) {
        val k = l.scale * w / bmp.width
        val m = Matrix().apply {
            postTranslate(-bmp.width / 2f, -bmp.height / 2f)
            postScale(if (l.flip) -k else k, k)
            postRotate(l.rotation)
            postTranslate(l.x * w, l.y * h)
        }
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (l.opacity * 255).roundToInt().coerceIn(0, 255)
            blendMode = blendMode(l.blend)
        }
        canvas.drawBitmap(bmp, m, p)
    }

    // ---- overlays

    fun typeface(f: Font): Typeface = when (f) {
        Font.Bold -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        Font.Sans -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        Font.Light -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        Font.Condensed -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        Font.Serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        Font.Italic -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        Font.Mono -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        Font.Hand -> Typeface.create("cursive", Typeface.BOLD)
        Font.Casual -> Typeface.create("casual", Typeface.BOLD)
        Font.SmallCaps -> Typeface.create("sans-serif-smallcaps", Typeface.BOLD)
    }

    fun drawOverlays(canvas: Canvas, w: Int, h: Int, s: EditState) {
        val side = min(w, h).toFloat()
        for (st in s.strokes) drawStroke(canvas, w, h, side, st)
        val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        for (st in s.stickers) {
            ep.textSize = st.size * side
            ep.alpha = (st.opacity * 255).roundToInt()
            val fm = ep.fontMetrics
            canvas.save()
            canvas.rotate(st.rotation, st.x * w, st.y * h)
            if (st.flip) canvas.scale(-1f, 1f, st.x * w, st.y * h)
            canvas.drawText(st.emoji, st.x * w, st.y * h - (fm.ascent + fm.descent) / 2f, ep)
            canvas.restore()
        }
        for (t in s.texts) {
            canvas.save(); canvas.rotate(t.rotation, t.x * w, t.y * h)
            drawText(canvas, w, h, side, t)
            canvas.restore()
        }
    }

    private fun drawStroke(canvas: Canvas, w: Int, h: Int, side: Float, st: Stroke) {
        if (st.points.isEmpty() || st.brush == Brush.Eraser) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = if (st.brush == Brush.Marker) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeWidth = st.width * side
            color = st.color
            alpha = (st.opacity * 255).roundToInt()
        }
        val a = st.points.first(); val b = st.points.last()
        val ax = a.x * w; val ay = a.y * h; val bx = b.x * w; val by = b.y * h
        if (st.brush.isShape) {
            when (st.brush) {
                Brush.Line -> canvas.drawLine(ax, ay, bx, by, paint)
                Brush.Arrow -> {
                    canvas.drawLine(ax, ay, bx, by, paint)
                    val ang = atan2(by - ay, bx - ax)
                    val len = max(paint.strokeWidth * 3.5f, hypot(bx - ax, by - ay) * 0.18f)
                    for (d in floatArrayOf(2.6f, -2.6f)) canvas.drawLine(bx, by, bx + len * cos(ang + d), by + len * sin(ang + d), paint)
                }
                Brush.Rect -> canvas.drawRoundRect(RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)), paint.strokeWidth, paint.strokeWidth, paint)
                else -> canvas.drawOval(RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)), paint)
            }
            return
        }
        val path = Path()
        st.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x * w, p.y * h)
            else {
                val prev = st.points[i - 1]
                path.quadTo(prev.x * w, prev.y * h, (prev.x + p.x) / 2f * w, (prev.y + p.y) / 2f * h)
            }
        }
        if (st.points.size == 1) path.lineTo(st.points[0].x * w + 0.1f, st.points[0].y * h)
        when (st.brush) {
            Brush.Marker -> {
                paint.alpha = (110 * st.opacity).roundToInt()
                paint.strokeWidth = st.width * side * 2.2f
                canvas.drawPath(path, paint)
            }
            Brush.Neon -> {
                paint.maskFilter = BlurMaskFilter(st.width * side * 1.4f, BlurMaskFilter.Blur.NORMAL)
                paint.strokeWidth = st.width * side * 1.6f
                canvas.drawPath(path, paint)
                paint.maskFilter = null
                paint.color = Color.WHITE; paint.alpha = (255 * st.opacity).roundToInt()
                paint.strokeWidth = st.width * side * 0.4f
                canvas.drawPath(path, paint)
            }
            Brush.Dashed -> {
                paint.pathEffect = DashPathEffect(floatArrayOf(paint.strokeWidth * 2.5f, paint.strokeWidth * 2f), 0f)
                canvas.drawPath(path, paint)
            }
            Brush.Spray -> {
                paint.style = Paint.Style.FILL
                val rnd = java.util.Random(st.points.size * 31L + (st.points[0].x * 1000).toLong())
                val r = st.width * side * 2.2f
                val dot = max(1f, st.width * side * 0.12f)
                for (i in 1 until st.points.size) {
                    val p0 = st.points[i - 1]; val p1 = st.points[i]
                    val n = max(4, (hypot((p1.x - p0.x) * w, (p1.y - p0.y) * h) / r * 26).toInt())
                    repeat(n) {
                        val t = rnd.nextFloat()
                        val cx = (p0.x + (p1.x - p0.x) * t) * w; val cy = (p0.y + (p1.y - p0.y) * t) * h
                        val ang = rnd.nextFloat() * 6.283f; val d = r * kotlin.math.sqrt(rnd.nextFloat())
                        canvas.drawCircle(cx + d * cos(ang), cy + d * sin(ang), dot, paint)
                    }
                }
            }
            else -> canvas.drawPath(path, paint)
        }
    }

    private fun textPaint(t: TextLayer, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = typeface(t.font)
        letterSpacing = t.spacing
        textAlign = when (t.align) { 1 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
    }

    /** Width and height of a text layer as fractions of the picture's shorter side (for the selection box). */
    fun textBounds(t: TextLayer): Pair<Float, Float> {
        val p = textPaint(t, t.size * 1000f)
        val lines = t.text.split('\n')
        val fm = p.fontMetrics
        val w = (lines.maxOfOrNull { p.measureText(it) } ?: 0f) / 1000f
        val h = (fm.descent - fm.ascent) * lines.size / 1000f
        return (w + t.size * 0.7f) to (h + t.size * 0.5f)
    }

    private fun drawText(canvas: Canvas, w: Int, h: Int, side: Float, t: TextLayer) {
        val paint = textPaint(t, t.size * side)
        val alpha = (t.opacity * 255).roundToInt().coerceIn(0, 255)
        val lines = t.text.split('\n')
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        val total = lineH * lines.size
        val top = t.y * h - total / 2f
        val maxW = lines.maxOf { paint.measureText(it) }
        val cx = t.x * w
        val x = when (t.align) { 1 -> cx - maxW / 2f; 2 -> cx + maxW / 2f; else -> cx }
        canvas.saveLayerAlpha(null, alpha)
        if (t.background) {
            val pad = paint.textSize * 0.35f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.bgColor }
            canvas.drawRoundRect(RectF(cx - maxW / 2 - pad, top - pad * 0.6f, cx + maxW / 2 + pad, top + total + pad * 0.6f), pad, pad, bg)
        }
        if (t.outline > 0f) {
            val sp = textPaint(t, t.size * side).apply {
                style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
                strokeWidth = t.outline * paint.textSize * 0.25f
                color = t.outlineColor
                if (t.shadow && !t.background) setShadowLayer(textSize * 0.08f, 0f, textSize * 0.03f, 0x66000000)
            }
            lines.forEachIndexed { i, line -> canvas.drawText(line, x, top + i * lineH - fm.ascent, sp) }
        } else if (t.shadow && !t.background) paint.setShadowLayer(paint.textSize * 0.08f, 0f, paint.textSize * 0.03f, 0x66000000)
        paint.color = t.color
        lines.forEachIndexed { i, line -> canvas.drawText(line, x, top + i * lineH - fm.ascent, paint) }
        canvas.restore()
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

    // ---- frames

    fun applyFrame(img: Bitmap, s: EditState): Bitmap {
        val frame = s.frame
        if (frame == Frame.None) return img
        val side = min(img.width, img.height).toFloat()
        val k = s.f(Params.frameSize)
        val color = s.frameColor
        val fp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun pad(l: Float, t: Float, r: Float, b: Float, draw: (Canvas, RectF) -> Unit): Bitmap {
            val out = Bitmap.createBitmap((img.width + (l + r) * side).roundToInt(), (img.height + (t + b) * side).roundToInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(color)
            draw(c, RectF(l * side, t * side, l * side + img.width, t * side + img.height))
            return out
        }
        return when (frame) {
            Frame.Thin -> { val b = 0.008f + 0.05f * k; pad(b, b, b, b) { c, r -> c.drawBitmap(img, null, r, fp) } }
            Frame.Gallery -> { val b = 0.04f + 0.14f * k; pad(b, b, b, b) { c, r -> c.drawBitmap(img, null, r, fp) } }
            Frame.Polaroid -> { val b = 0.03f + 0.05f * k; pad(b, b, b, b * 4f) { c, r -> c.drawBitmap(img, null, r, fp) } }
            Frame.Rounded -> {
                val b = 0.02f + 0.06f * k
                pad(b, b, b, b) { c, r ->
                    c.save(); c.clipPath(Path().apply { addRoundRect(r, side * 0.06f, side * 0.06f, Path.Direction.CW) })
                    c.drawBitmap(img, null, r, fp); c.restore()
                }
            }
            Frame.Shadow -> {
                val b = 0.06f + 0.1f * k
                pad(b, b, b, b) { c, r ->
                    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x66000000; maskFilter = BlurMaskFilter(side * 0.03f, BlurMaskFilter.Blur.NORMAL) }
                    c.drawRect(RectF(r.left, r.top + side * 0.015f, r.right, r.bottom + side * 0.025f), sh)
                    c.drawBitmap(img, null, r, fp)
                }
            }
            Frame.Square, Frame.BlurFit -> {
                val big = max(img.width, img.height).toFloat()
                val margin = big * 0.12f * k
                val size = (big + 2 * margin).roundToInt()
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                if (frame == Frame.BlurFit) {
                    // Blurred, slightly darker copy of the photo behind it.
                    val tiny = Bitmap.createScaledBitmap(img, max(1, img.width / 24), max(1, img.height / 24), true)
                    val px = IntArray(tiny.width * tiny.height); tiny.getPixels(px, 0, tiny.width, 0, 0, tiny.width, tiny.height)
                    val bl = Pixels.box(px, tiny.width, tiny.height, 2, 3)
                    tiny.setPixels(bl, 0, tiny.width, 0, 0, tiny.width, tiny.height)
                    val cover = max(size.toFloat() / tiny.width, size.toFloat() / tiny.height)
                    val bw = tiny.width * cover; val bh = tiny.height * cover
                    c.drawBitmap(tiny, null, RectF((size - bw) / 2, (size - bh) / 2, (size + bw) / 2, (size + bh) / 2),
                        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(scale(0.8f, 0.8f, 0.8f, 0f)) })
                } else c.drawColor(color)
                val l = (size - img.width) / 2f; val t = (size - img.height) / 2f
                c.drawBitmap(img, null, RectF(l, t, l + img.width, t + img.height), fp)
                out
            }
            Frame.Film -> {
                val b = 0.13f + 0.05f * k
                val out = Bitmap.createBitmap((img.width + 0.04f * side).roundToInt(), (img.height + 2 * b * side).roundToInt(), Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(0xFF111111.toInt())
                val r = RectF(0.02f * side, b * side, 0.02f * side + img.width, b * side + img.height)
                c.drawBitmap(img, null, r, fp)
                val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFEDEDED.toInt() }
                val hw = side * 0.045f; val hh = side * 0.06f
                var x = hw * 0.6f
                while (x < out.width - hw) {
                    for (yc in floatArrayOf(b * side / 2f, out.height - b * side / 2f)) c.drawRoundRect(RectF(x, yc - hh / 2, x + hw, yc + hh / 2), hw * 0.25f, hw * 0.25f, hole)
                    x += hw * 2f
                }
                out
            }
            Frame.None -> img
        }
    }

    /** One-tap fix for the Tune tool. */
    fun autoEnhance(bmp: Bitmap): List<Pair<Param, Float>> {
        val small = Bitmap.createScaledBitmap(bmp, 64, (64f * bmp.height / bmp.width).roundToInt().coerceAtLeast(1), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        return Pixels.auto(px)
    }

    /** Colour under a point of a picture (averaged over a small square). */
    fun sample(bmp: Bitmap, x: Float, y: Float): Int {
        val cx = (x * bmp.width).roundToInt().coerceIn(0, bmp.width - 1); val cy = (y * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
        var r = 0; var g = 0; var b = 0; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val c = bmp.getPixel((cx + dx).coerceIn(0, bmp.width - 1), (cy + dy).coerceIn(0, bmp.height - 1))
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        return Color.rgb(r / n, g / n, b / n)
    }
}
