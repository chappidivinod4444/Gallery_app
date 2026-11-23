package app.gallery.edit

import android.graphics.ColorMatrix

/** Colour looks, each a 4x5 colour matrix (like a photo lab "preset"). */
object Filters {
    private fun m(vararg v: Float) = ColorMatrix(v)

    private fun sat(s: Float) = ColorMatrix().apply { setSaturation(s) }

    private fun scale(r: Float, g: Float, b: Float, add: Float = 0f) = m(
        r, 0f, 0f, 0f, add,
        0f, g, 0f, 0f, add,
        0f, 0f, b, 0f, add,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun contrast(c: Float): ColorMatrix {
        val t = (1f - c) * 128f
        return m(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
    }

    private fun chain(vararg ms: ColorMatrix) = ColorMatrix().apply { ms.forEach { postConcat(it) } }

    val all: List<Pair<String, ColorMatrix>> = listOf(
        "Original" to ColorMatrix(),
        "Vivid" to chain(sat(1.45f), contrast(1.12f)),
        "Bright" to chain(scale(1.08f, 1.08f, 1.08f, 10f), sat(1.1f)),
        "Warm" to chain(scale(1.10f, 1.02f, 0.88f), sat(1.08f)),
        "Cool" to chain(scale(0.92f, 1.0f, 1.12f), sat(1.05f)),
        "Golden" to chain(scale(1.15f, 1.05f, 0.80f, 6f), contrast(1.05f)),
        "Teal & Orange" to chain(
            m(
                1.15f, 0.05f, -0.10f, 0f, 0f,
                -0.05f, 1.05f, 0.05f, 0f, 0f,
                -0.10f, 0.10f, 1.15f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ), contrast(1.1f), sat(1.15f),
        ),
        "Film" to chain(contrast(0.92f), scale(1.04f, 1.0f, 0.94f, 12f), sat(0.9f)),
        "Matte" to chain(contrast(0.82f), scale(1f, 1f, 1f, 22f), sat(0.85f)),
        "Fade" to chain(contrast(0.75f), scale(1f, 1f, 1f, 30f), sat(0.7f)),
        "Pastel" to chain(sat(0.65f), scale(1.05f, 1.03f, 1.08f, 20f)),
        "Chrome" to chain(contrast(1.2f), sat(1.2f), scale(1.0f, 1.02f, 1.06f)),
        "Dramatic" to chain(contrast(1.4f), sat(0.75f), scale(0.95f, 0.95f, 0.95f, -6f)),
        "Lomo" to chain(contrast(1.3f), sat(1.3f), scale(1.05f, 1.08f, 0.92f)),
        "Rose" to chain(scale(1.10f, 0.95f, 1.02f, 6f), sat(1.05f)),
        "Ocean" to chain(scale(0.85f, 1.02f, 1.15f), contrast(1.06f)),
        "Cyber" to chain(
            m(
                1.1f, 0f, 0.15f, 0f, 0f,
                0f, 0.9f, 0.1f, 0f, 0f,
                0.15f, 0f, 1.2f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ), contrast(1.15f), sat(1.3f),
        ),
        "Sepia" to chain(
            sat(0f),
            m(
                1.07f, 0f, 0f, 0f, 20f,
                0f, 0.95f, 0f, 0f, 6f,
                0f, 0f, 0.78f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
        "Vintage" to chain(
            m(
                0.9f, 0.1f, 0.05f, 0f, 14f,
                0.05f, 0.85f, 0.05f, 0f, 10f,
                0.05f, 0.1f, 0.7f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f,
            ), contrast(0.9f),
        ),
        "Mono" to sat(0f),
        "Noir" to chain(sat(0f), contrast(1.45f)),
        "Silver" to chain(sat(0f), contrast(0.9f), scale(1f, 1f, 1.03f, 14f)),
    )

    fun get(name: String): ColorMatrix = all.firstOrNull { it.first == name }?.second ?: ColorMatrix()

    /** Blends between "no filter" and the filter by [t] (0..1). */
    fun blended(name: String, t: Float): ColorMatrix {
        val a = ColorMatrix().array
        val b = get(name).array
        return ColorMatrix(FloatArray(20) { a[it] + (b[it] - a[it]) * t })
    }
}
