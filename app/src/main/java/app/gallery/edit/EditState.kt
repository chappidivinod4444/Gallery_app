package app.gallery.edit

/** Light, colour and detail sliders. All 0 = unchanged. Ranges are -1..1 unless noted. */
data class Adjust(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,       // 0..1
    val vignette: Float = 0f,   // 0..1
    val sharpen: Float = 0f,    // 0..1
    val blur: Float = 0f,       // 0..1
    val grain: Float = 0f,      // 0..1
) {
    val needsPixels get() = highlights != 0f || shadows != 0f || vibrance != 0f || vignette != 0f || sharpen != 0f || blur != 0f || grain != 0f
}

/** A point in 0..1 coordinates of the finished (cropped) picture. */
data class P(val x: Float, val y: Float)

enum class Brush { Pen, Marker, Neon, Eraser }

data class Stroke(val points: List<P>, val color: Int, val width: Float, val brush: Brush)

enum class Font { Sans, Bold, Serif, Mono, Hand }

data class TextLayer(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    /** Text height as a fraction of the picture's shorter side. */
    val size: Float = 0.07f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val font: Font = Font.Bold,
    val background: Boolean = false,
)

data class Sticker(val id: Long, val emoji: String, val x: Float, val y: Float, val size: Float = 0.16f)

enum class Frame { None, ThinWhite, ThickWhite, Black, Polaroid, Rounded }

/** Crop box in 0..1 of the rotated, straightened picture. */
data class Crop(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val w get() = r - l
    val h get() = b - t
}

/** Everything about an edit. The original photo is never changed; saving makes a new copy. */
data class EditState(
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,
    val crop: Crop = Crop(),
    val filter: String = "Original",
    val filterStrength: Float = 1f,
    val adjust: Adjust = Adjust(),
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val frame: Frame = Frame.None,
    val watermark: String = "",
)
