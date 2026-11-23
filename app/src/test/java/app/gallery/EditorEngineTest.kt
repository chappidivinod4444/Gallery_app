package app.gallery

import app.gallery.edit.Curves
import app.gallery.edit.CtrlPoint
import app.gallery.edit.EditState
import app.gallery.edit.Heal
import app.gallery.edit.HealSpot
import app.gallery.edit.Local
import app.gallery.edit.MaskStroke
import app.gallery.edit.P
import app.gallery.edit.Params
import app.gallery.edit.Pixels
import app.gallery.edit.Spline
import app.gallery.edit.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EditorEngineTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun ch(c: Int) = c and 0xFF

    @Test fun straightCurveChangesNothing() {
        val lut = Spline.lut(Curves.LINE)
        for (i in 0..255) assertEquals(i, lut[i])
    }

    @Test fun curveIsSmoothAndNeverGoesBackwards() {
        for ((_, c) in Spline.presets) for (pts in listOf(c.rgb, c.r, c.g, c.b)) {
            val lut = Spline.lut(pts)
            for (i in 1..255) assertTrue("curve went down at $i", lut[i] >= lut[i - 1])
        }
    }

    @Test fun blurKeepsAFlatPictureFlat() {
        val w = 120; val h = 80
        val px = IntArray(w * h) { grey(140) }
        for (r in listOf(1f, 3f, 9f, 30f)) {
            val b = Pixels.blur(px, w, h, r)
            assertTrue(b.all { abs(ch(it) - 140) <= 1 })
        }
    }

    @Test fun paintedStrokeCoversItsPath() {
        val w = 100; val h = 100
        val m = Pixels.paintMask(w, h, listOf(listOf(P(0.2f, 0.5f), P(0.8f, 0.5f)) to (6f to 1f)))
        assertTrue(m[50 * w + 50] > 0.95f)
        assertTrue(m[50 * w + 20] > 0.95f)
        assertEquals(0f, m[10 * w + 50], 0f)
        // Eraser (level 0) clears it again.
        val e = Pixels.paintMask(w, h, listOf(
            listOf(P(0.2f, 0.5f), P(0.8f, 0.5f)) to (6f to 1f),
            listOf(P(0.2f, 0.5f), P(0.8f, 0.5f)) to (8f to 0f),
        ))
        assertTrue(e[50 * w + 50] < 0.05f)
    }

    @Test fun healingRemovesASpot() {
        val w = 160; val h = 160
        // Soft gradient background with a black spot in the middle.
        val px = IntArray(w * h) { i -> grey(80 + (i % w) / 4) }
        for (y in 75..85) for (x in 75..85) px[y * w + x] = grey(0)
        val s = EditState(heals = listOf(HealSpot(listOf(P(0.5f, 0.5f)), 0.06f)))
        Heal.apply(px, w, h, s)
        for (y in 75..85) for (x in 75..85) {
            val expected = 80 + x / 4
            assertTrue("pixel $x,$y = ${ch(px[y * w + x])}, wanted ~$expected", abs(ch(px[y * w + x]) - expected) < 12)
        }
    }

    @Test fun everyToolRunsOnAnOddSizedPicture() {
        val w = 37; val h = 23
        val px = IntArray(w * h) { i -> (0xFF shl 24) or ((i * 7) % 256 shl 16) or ((i * 13) % 256 shl 8) or (i * 3) % 256 }
        var s = EditState()
        for (p in Tools.tune + Tools.details) s = s.set(p, if (p.bipolar) 0.6f else 0.7f)
        for (f in Tools.effects + Tools.blurs) { s = s.set(f.main, if (f.main.bipolar) -0.6f else 0.7f); f.extras.forEach { s = s.set(it, it.max) } }
        for (i in 0 until 8) s = s.setAll(Params.hue(i) to 0.5f, Params.sat(i) to -0.5f, Params.lum(i) to 0.3f)
        s = s.copy(
            curves = Spline.presets[7].second,
            points = listOf(CtrlPoint(1, 0.5f, 0.5f, brightness = 0.5f, contrast = 0.3f, saturation = -0.4f, warmth = 0.2f)),
            masks = Local.entries.map { MaskStroke(it, 0.8f, listOf(P(0.1f, 0.1f), P(0.9f, 0.9f)), 0.1f) },
        )
        val out = Pixels.process(px.copyOf(), w, h, s)
        assertEquals(w * h, out.size)
        assertTrue(out.all { (it ushr 24) == 0xFF })
    }

    @Test fun nothingChangedMeansSamePicture() {
        val w = 20; val h = 10
        val px = IntArray(w * h) { grey(it % 256) }
        val out = Pixels.process(px.copyOf(), w, h, EditState())
        assertTrue(px.contentEquals(out))
    }

    @Test fun slidersRememberOnlyChangedValues() {
        val s = EditState().set(Params.brightness, 0.3f).set(Params.brightness, 0f)
        assertTrue(s.v.isEmpty())
        assertEquals(0.5f, s.f(Params.lensX), 0f)
        assertEquals("+30", Params.brightness.show(0.3f))
    }
}
