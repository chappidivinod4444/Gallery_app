package app.gallery.edit

/**
 * One adjustable value (a slider). Every slider in the editor is described here once, and its value lives in
 * [EditState.v] under [key]. A value that is not in the map is at its default ([def]).
 * If [choices] is set, the value is a whole number that picks one of them.
 */
data class Param(
    val key: String,
    val label: String,
    val min: Float = -1f,
    val max: Float = 1f,
    val def: Float = 0f,
    val choices: List<String>? = null,
    val fmt: ((Float) -> String)? = null,
) {
    val bipolar get() = min < 0f && max > 0f
    /** How the value is shown: -100..+100 like Snapseed, or the choice's name. */
    fun show(v: Float): String = when {
        fmt != null -> fmt.invoke(v)
        choices != null -> choices[v.toInt().coerceIn(0, choices.lastIndex)]
        else -> {
            val n = ((v - (if (bipolar) 0f else min)) / (if (bipolar) max else max - min) * 100f).let { Math.round(it) }
            if (bipolar && n > 0) "+$n" else "$n"
        }
    }
}

object Params {
    private val reg = HashMap<String, Param>()
    private fun p(key: String, label: String, min: Float = -1f, max: Float = 1f, def: Float = 0f, choices: List<String>? = null) =
        Param(key, label, min, max, def, choices).also { reg[key] = it }

    // Tune image
    val brightness = p("brightness", "Brightness")
    val contrast = p("contrast", "Contrast")
    val saturation = p("saturation", "Saturation")
    val ambiance = p("ambiance", "Ambiance")
    val highlights = p("highlights", "Highlights")
    val shadows = p("shadows", "Shadows")
    val warmth = p("warmth", "Warmth")
    val exposure = p("exposure", "Exposure")
    val whites = p("whites", "Whites")
    val blacks = p("blacks", "Blacks")
    val vibrance = p("vibrance", "Vibrance")
    val tint = p("tint", "Tint")
    val fade = p("fade", "Fade", 0f, 1f)

    // Details
    val structure = p("structure", "Structure")
    val sharpen = p("sharpen", "Sharpening", 0f, 1f)
    val clarity = p("clarity", "Clarity")
    val smooth = p("smooth", "Smooth skin & noise", 0f, 1f)

    // White balance
    val temp = p("temp", "Temperature")
    val wbTint = p("wbtint", "Tint")

    // Perspective
    val pv = p("pv", "Vertical tilt")
    val ph = p("ph", "Horizontal tilt")

    // Effects
    val hdr = p("hdr", "Strength", 0f, 1f)
    val drama = p("drama", "Strength", 0f, 1f)
    val glamour = p("glamour", "Glow", 0f, 1f)
    val tonal = p("tonal", "Strength", 0f, 1f)
    val vintage = p("vintage", "Strength", 0f, 1f)
    val grain = p("grain", "Grain", 0f, 1f)
    val retrolux = p("retrolux", "Strength", 0f, 1f)
    val grunge = p("grunge", "Strength", 0f, 1f)
    val noir = p("noir", "Strength", 0f, 1f)
    val bw = p("bw", "Strength", 0f, 1f)
    val bwFilter = p("bw.f", "Colour filter", 0f, 5f, 0f, listOf("Neutral", "Red", "Orange", "Yellow", "Green", "Blue"))
    val glow = p("glow", "Strength", 0f, 1f)
    val lensBlur = p("lens", "Blur strength", 0f, 1f)
    val lensSize = p("lens.s", "Focus size", 0.05f, 0.9f, 0.3f)
    val lensX = p("lens.x", "x", 0f, 1f, 0.5f)
    val lensY = p("lens.y", "y", 0f, 1f, 0.5f)
    val tilt = p("tilt", "Blur strength", 0f, 1f)
    val tiltSize = p("tilt.s", "Focus size", 0.05f, 0.9f, 0.25f)
    val tiltY = p("tilt.y", "y", 0f, 1f, 0.5f)
    val vignette = p("vig", "Outer brightness")
    val vigSize = p("vig.s", "Centre size", 0.1f, 1f, 0.5f)
    val vigX = p("vig.x", "x", 0f, 1f, 0.5f)
    val vigY = p("vig.y", "y", 0f, 1f, 0.5f)
    val sketch = p("sketch", "Strength", 0f, 1f)
    val posterize = p("poster", "Strength", 0f, 1f)
    val pixelate = p("pixel", "Block size", 0f, 1f)
    val glitch = p("glitch", "Strength", 0f, 1f)
    val leak = p("leak", "Strength", 0f, 1f)
    val leakStyle = p("leak.st", "Style", 0f, 3f, 0f, listOf("Sunset", "Rainbow", "Rose", "Gold"))
    val duotone = p("duo", "Strength", 0f, 1f)
    val duoStyle = p("duo.st", "Colours", 0f, 5f, 0f, listOf("Ocean", "Berry", "Lime", "Fire", "Ice", "Mint"))
    val blur = p("blur", "Strength", 0f, 1f)
    val motion = p("motion", "Strength", 0f, 1f)

    // Frame
    val frameSize = p("frame.s", "Border size", 0f, 1f, 0.5f)

    // HSL: 8 colour ranges × hue / saturation / luminance.
    val hslColors = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
    private val hues = List(8) { p("h$it", "Hue") }
    private val sats = List(8) { p("s$it", "Saturation") }
    private val lums = List(8) { p("l$it", "Luminance") }
    fun hue(i: Int) = hues[i]
    fun sat(i: Int) = sats[i]
    fun lum(i: Int) = lums[i]

    fun def(key: String) = reg[key]?.def ?: 0f
}

/** A point in 0..1 coordinates. */
data class P(val x: Float, val y: Float)

enum class Brush(val label: String) {
    Pen("Pen"), Marker("Marker"), Neon("Neon"), Spray("Spray"), Dashed("Dashed"),
    Line("Line"), Arrow("Arrow"), Rect("Box"), Circle("Circle"), Eraser("Eraser");
    val isShape get() = this == Line || this == Arrow || this == Rect || this == Circle
}

data class Stroke(val points: List<P>, val color: Int, val width: Float, val brush: Brush, val opacity: Float = 1f)

enum class Font(val label: String) {
    Bold("Bold"), Sans("Classic"), Light("Light"), Condensed("Narrow"), Serif("Serif"), Italic("Italic"),
    Mono("Mono"), Hand("Script"), Casual("Casual"), SmallCaps("Caps")
}

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
    val bgColor: Int = 0xCC000000.toInt(),
    val rotation: Float = 0f,
    val outline: Float = 0f,
    val outlineColor: Int = 0xFF000000.toInt(),
    val shadow: Boolean = true,
    val opacity: Float = 1f,
    /** 0 centre, 1 left, 2 right. */
    val align: Int = 0,
    val spacing: Float = 0f,
)

data class Sticker(
    val id: Long, val emoji: String, val x: Float, val y: Float,
    val size: Float = 0.16f, val rotation: Float = 0f, val opacity: Float = 1f, val flip: Boolean = false,
)

enum class Blend(val label: String) {
    Normal("Normal"), Screen("Screen"), Multiply("Multiply"), Overlay("Overlay"), SoftLight("Soft light"),
    Lighten("Lighten"), Darken("Darken"), Difference("Difference"), Color("Colour"), Luminosity("Luminosity"),
}

/** Another photo placed on top (PicsArt "Add photo" / Snapseed "Double exposure"). */
data class PhotoLayer(
    val id: Long, val uri: String, val aspect: Float, // height / width of that photo
    val x: Float = 0.5f, val y: Float = 0.5f,
    val scale: Float = 0.6f, // width as a fraction of the picture's width
    val rotation: Float = 0f, val opacity: Float = 1f, val blend: Blend = Blend.Normal, val flip: Boolean = false,
)

enum class Frame(val label: String) {
    None("None"), Thin("Thin"), Gallery("Gallery"), Polaroid("Polaroid"), Rounded("Rounded"),
    Shadow("Shadow"), Square("Square fit"), BlurFit("Blur fit"), Film("Film"),
}

/** Crop box in 0..1 of the turned, straightened picture. */
data class Crop(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val w get() = r - l
    val h get() = b - t
}

/** Tone curve: points from (0,0) to (1,1), x = input brightness, y = output. */
data class Curves(
    val rgb: List<P> = LINE, val r: List<P> = LINE, val g: List<P> = LINE, val b: List<P> = LINE,
) {
    val isIdentity get() = rgb == LINE && r == LINE && g == LINE && b == LINE
    fun channel(i: Int) = when (i) { 1 -> r; 2 -> g; 3 -> b; else -> rgb }
    fun with(i: Int, pts: List<P>) = when (i) { 1 -> copy(r = pts); 2 -> copy(g = pts); 3 -> copy(b = pts); else -> copy(rgb = pts) }
    companion object { val LINE = listOf(P(0f, 0f), P(1f, 1f)) }
}

/** Snapseed "Selective": a point that changes the area around it with similar colours. Position in frame space. */
data class CtrlPoint(
    val id: Long, val x: Float, val y: Float, val size: Float = 0.18f,
    val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f, val warmth: Float = 0f,
)

enum class Local(val label: String) { DodgeBurn("Dodge & Burn"), Exposure("Exposure"), Temperature("Temperature"), Saturation("Saturation"), Blur("Blur") }

/** Snapseed "Brush": painted local change. amount 0 = eraser. Points in frame space. */
data class MaskStroke(val kind: Local, val amount: Float, val points: List<P>, val width: Float)

/** Healing brush stroke. Points in frame space. */
data class HealSpot(val points: List<P>, val width: Float)

/** Everything about an edit. The original photo is never changed; saving makes a new copy. */
data class EditState(
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,
    val crop: Crop = Crop(),
    val filter: String = "Original",
    val filterStrength: Float = 1f,
    val v: Map<String, Float> = emptyMap(),
    val curves: Curves = Curves(),
    val points: List<CtrlPoint> = emptyList(),
    val masks: List<MaskStroke> = emptyList(),
    val heals: List<HealSpot> = emptyList(),
    val photos: List<PhotoLayer> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextLayer> = emptyList(),
    val stickers: List<Sticker> = emptyList(),
    val frame: Frame = Frame.None,
    val frameColor: Int = 0xFFFFFFFF.toInt(),
    val watermark: String = "",
) {
    fun f(p: Param): Float = v[p.key] ?: p.def
    fun f(key: String): Float = v[key] ?: Params.def(key)
    fun has(p: Param) = f(p) != p.def
    fun set(p: Param, value: Float): EditState {
        val c = value.coerceIn(p.min, p.max)
        return copy(v = if (kotlin.math.abs(c - p.def) < 1e-4f) v - p.key else v + (p.key to c))
    }
    fun setAll(vararg pairs: Pair<Param, Float>): EditState = pairs.fold(this) { s, (p, x) -> s.set(p, x) }
    fun clear(keys: Collection<String>) = copy(v = v - keys.toSet())

    /** Frame space (whole turned picture) → output space (after crop), and back. */
    fun toOut(p: P) = P((p.x - crop.l) / crop.w, (p.y - crop.t) / crop.h)
    fun toFrame(p: P) = P(crop.l + p.x * crop.w, crop.t + p.y * crop.h)
}
