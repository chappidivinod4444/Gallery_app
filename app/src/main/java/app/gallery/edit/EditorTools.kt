package app.gallery.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoFilter
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tool(val label: String, val icon: ImageVector, val group: Int) {
    Looks("Looks", Icons.Rounded.PhotoFilter, 0),
    Tune("Tune image", Icons.Rounded.Tune, 0),
    Details("Details", Icons.Rounded.Details, 0),
    Curves("Curves", Icons.Rounded.Timeline, 0),
    Balance("White balance", Icons.Rounded.WbSunny, 0),
    Color("Colour (HSL)", Icons.Rounded.Palette, 0),
    Crop("Crop & rotate", Icons.Rounded.Crop, 1),
    Perspective("Perspective", Icons.Rounded.Transform, 1),
    Healing("Healing", Icons.Rounded.Healing, 2),
    Brush("Brush", Icons.Rounded.Brush, 2),
    Selective("Selective", Icons.Rounded.Adjust, 2),
    Effects("Effects", Icons.Rounded.AutoAwesome, 3),
    Blur("Blur", Icons.Rounded.BlurOn, 3),
    Text("Text", Icons.Rounded.TextFields, 4),
    Stickers("Stickers", Icons.Rounded.EmojiEmotions, 4),
    Draw("Draw", Icons.Rounded.Gesture, 4),
    Photo("Add photo", Icons.Rounded.AddPhotoAlternate, 4),
    Frame("Frame", Icons.Rounded.CropFree, 4),
}

enum class Focus { None, Lens, Tilt, Vignette }

/** One effect: its main strength slider, extra sliders, and whether it has a movable centre. */
class Fx(val name: String, val main: Param, val extras: List<Param> = emptyList(), val focus: Focus = Focus.None, val preview: Float = 0.8f) {
    val params get() = listOf(main) + extras
}

object Tools {
    val tune = with(Params) { listOf(brightness, contrast, saturation, ambiance, highlights, shadows, warmth, exposure, whites, blacks, vibrance, tint, fade) }
    val details = with(Params) { listOf(structure, sharpen, clarity, smooth) }
    val balance = with(Params) { listOf(temp, wbTint) }
    val perspective = with(Params) { listOf(pv, ph) }

    val effects = with(Params) {
        listOf(
            Fx("HDR Scape", hdr), Fx("Drama", drama), Fx("Glamour Glow", glamour), Fx("Tonal Contrast", tonal),
            Fx("Vintage", vintage), Fx("Grainy Film", grain), Fx("Retrolux", retrolux), Fx("Grunge", grunge),
            Fx("Noir", noir), Fx("Black & White", bw, listOf(bwFilter), preview = 1f), Fx("Glow", glow),
            Fx("Duotone", duotone, listOf(duoStyle), preview = 1f), Fx("Sketch", sketch, preview = 1f), Fx("Pop Art", posterize),
            Fx("Light Leak", leak, listOf(leakStyle)), Fx("Pixelate", pixelate, preview = 0.35f), Fx("Glitch", glitch),
            Fx("Vignette", vignette, listOf(vigSize), Focus.Vignette, preview = -0.8f),
        )
    }
    val blurs = with(Params) {
        listOf(
            Fx("Lens Blur", lensBlur, listOf(lensSize), Focus.Lens), Fx("Tilt-Shift", tilt, listOf(tiltSize), Focus.Tilt),
            Fx("Soft Blur", blur, preview = 0.5f), Fx("Motion", motion, preview = 0.6f),
        )
    }

    private fun keys(ps: List<Param>) = ps.map { it.key }
    private val fxKeys = effects.flatMap { keys(it.params) } + listOf(Params.vigX.key, Params.vigY.key)
    private val blurKeys = blurs.flatMap { keys(it.params) } + listOf(Params.lensX.key, Params.lensY.key, Params.tiltY.key)
    private val hslKeys = (0 until 8).flatMap { listOf(Params.hue(it).key, Params.sat(it).key, Params.lum(it).key) }

    fun changed(t: Tool, s: EditState): Boolean = when (t) {
        Tool.Looks -> s.filter != "Original"
        Tool.Tune -> keys(tune).any { it in s.v }
        Tool.Details -> keys(details).any { it in s.v }
        Tool.Curves -> !s.curves.isIdentity
        Tool.Balance -> keys(balance).any { it in s.v }
        Tool.Color -> hslKeys.any { it in s.v }
        Tool.Crop -> s.rotation != 0 || s.flipH || s.flipV || s.straighten != 0f || s.crop != Crop()
        Tool.Perspective -> keys(perspective).any { it in s.v }
        Tool.Healing -> s.heals.isNotEmpty()
        Tool.Brush -> s.masks.isNotEmpty()
        Tool.Selective -> s.points.isNotEmpty()
        Tool.Effects -> effects.any { s.has(it.main) }
        Tool.Blur -> blurs.any { s.has(it.main) }
        Tool.Text -> s.texts.isNotEmpty()
        Tool.Stickers -> s.stickers.isNotEmpty()
        Tool.Draw -> s.strokes.isNotEmpty()
        Tool.Photo -> s.photos.isNotEmpty()
        Tool.Frame -> s.frame != Frame.None || s.watermark.isNotBlank()
    }

    fun reset(t: Tool, s: EditState): EditState = when (t) {
        Tool.Looks -> s.copy(filter = "Original", filterStrength = 1f)
        Tool.Tune -> s.clear(keys(tune))
        Tool.Details -> s.clear(keys(details))
        Tool.Curves -> s.copy(curves = Curves())
        Tool.Balance -> s.clear(keys(balance))
        Tool.Color -> s.clear(hslKeys)
        Tool.Crop -> s.copy(rotation = 0, flipH = false, flipV = false, straighten = 0f, crop = Crop())
        Tool.Perspective -> s.clear(keys(perspective))
        Tool.Healing -> s.copy(heals = emptyList())
        Tool.Brush -> s.copy(masks = emptyList())
        Tool.Selective -> s.copy(points = emptyList())
        Tool.Effects -> s.clear(fxKeys)
        Tool.Blur -> s.clear(blurKeys)
        Tool.Text -> s.copy(texts = emptyList())
        Tool.Stickers -> s.copy(stickers = emptyList())
        Tool.Draw -> s.copy(strokes = emptyList())
        Tool.Photo -> s.copy(photos = emptyList())
        Tool.Frame -> s.copy(frame = Frame.None, watermark = "")
    }

    /** Short summary for the "Edits" list. */
    fun summary(t: Tool, s: EditState): String = when (t) {
        Tool.Looks -> "${s.filter} ${(s.filterStrength * 100).toInt()}%"
        Tool.Tune -> tune.filter { s.has(it) }.joinToString { "${it.label} ${it.show(s.f(it))}" }
        Tool.Details -> details.filter { s.has(it) }.joinToString { "${it.label} ${it.show(s.f(it))}" }
        Tool.Balance -> balance.filter { s.has(it) }.joinToString { "${it.label} ${it.show(s.f(it))}" }
        Tool.Perspective -> perspective.filter { s.has(it) }.joinToString { "${it.label} ${it.show(s.f(it))}" }
        Tool.Effects -> effects.filter { s.has(it.main) }.joinToString { it.name }
        Tool.Blur -> blurs.filter { s.has(it.main) }.joinToString { it.name }
        Tool.Healing -> "${s.heals.size} spot(s)"
        Tool.Brush -> "${s.masks.size} stroke(s)"
        Tool.Selective -> "${s.points.size} point(s)"
        Tool.Text -> "${s.texts.size} text(s)"
        Tool.Stickers -> "${s.stickers.size} sticker(s)"
        Tool.Draw -> "${s.strokes.size} stroke(s)"
        Tool.Photo -> "${s.photos.size} photo(s)"
        Tool.Frame -> s.frame.label
        Tool.Curves -> "Custom curve"
        Tool.Color -> "Colour ranges changed"
        Tool.Crop -> "Cropped / turned"
    }

    // Stand-in sliders that edit things outside the value map.
    val lookStrength = Param("_look", "Strength", 0f, 1f, 1f)
    val spBrightness = Param("_sb", "Brightness")
    val spContrast = Param("_sc", "Contrast")
    val spSaturation = Param("_ss", "Saturation")
    val spWarmth = Param("_sw", "Warmth")
    val spSize = Param("_sz", "Size", 0.04f, 0.6f, 0.18f)
    val selective = listOf(spBrightness, spContrast, spSaturation, spWarmth, spSize)

    val hslSwatches = listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF32D74B, 0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F).map { it.toInt() }

    val emojiSets: List<Pair<String, List<String>>> = listOf(
        "Smileys" to "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 🙄 😬 😴 🤯 😭 😡 🤔 🤫 🫠 🥹 🤗 😱 🥶 🤠 👻 💀 🤡 👽 🤖".split(" "),
        "Love" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💖 💗 💓 💞 💕 💘 💝 💯 💋 💌 🌹 💐 😻 🫶 🤟 💑".split(" "),
        "Hands" to "👍 👎 👏 🙌 🙏 ✌️ 🤞 🤘 👌 🤙 💪 👋 ✋ 👊 🫡 👀 👑 💎 🔥 ✨ ⭐ 🌟 💫 ⚡ 💥 🎉 🎊 🎈 🎁 🎂".split(" "),
        "Nature" to "🌸 🌺 🌻 🌼 🌷 🍀 🌿 🍁 🍂 🌴 🌵 🌈 ☀️ 🌤️ ⛅ 🌙 ⭐ ❄️ ☃️ 🌊 🔥 🌍 🐶 🐱 🐼 🦊 🐻 🐰 🦋 🐝 🐢 🦄 🐧 🐥".split(" "),
        "Food" to "☕ 🍵 🧋 🍹 🍺 🍷 🍕 🍔 🍟 🌭 🍿 🍩 🍪 🎂 🍰 🧁 🍫 🍦 🍓 🍉 🍌 🍎 🥭 🍍 🥑 🌶️ 🍜 🍣 🍛 🥘".split(" "),
        "Travel" to "✈️ 🚗 🏍️ 🚲 🚀 🛳️ 🏖️ 🏝️ 🏔️ ⛰️ 🏕️ 🗽 🗼 🕌 🛕 🏰 🎡 🎢 🌃 🌆 🌉 📍 🧳 🗺️ 📸 🎥 🎧 🎵 🎶 ⚽ 🏏 🏀 🎮 🏆".split(" "),
        "Symbols" to "✅ ❌ ⭕ ❗ ❓ 💤 💬 💭 🔔 🔒 🔑 ⏰ 📌 📎 ✏️ 📝 💡 🎯 ♻️ ⚠️ 🚫 ➡️ ⬅️ ⬆️ ⬇️ 🔝 🆕 🆒 🆗 💲 ©️ ®️ ™️".split(" "),
    )
}
