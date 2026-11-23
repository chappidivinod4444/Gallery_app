package app.gallery.edit

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gallery.data.Actions
import app.gallery.data.Media
import app.gallery.ui.Caption
import app.gallery.ui.Nav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool(val label: String, val icon: ImageVector) {
    Crop("Crop", Icons.Rounded.Crop),
    Adjust("Adjust", Icons.Rounded.Tune),
    Filters("Filters", Icons.Rounded.FilterVintage),
    Effects("Effects", Icons.Rounded.AutoAwesome),
    Draw("Draw", Icons.Rounded.Brush),
    Text("Text", Icons.Rounded.TextFields),
    Stickers("Stickers", Icons.Rounded.EmojiEmotions),
    Frame("Frame", Icons.Rounded.CropSquare),
}

private val palette = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE,
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D55, 0xFFA2845E,
).map { it.toInt() }

private val emojis = listOf(
    "😀", "😂", "🥹", "😍", "🥰", "😎", "🤩", "😜", "🤔", "😴", "😭", "😡", "🥳", "🤯", "👻", "💀",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💖", "✨", "⭐", "🌟", "🔥", "💯", "🎉", "🎂", "🎁",
    "🌸", "🌹", "🌻", "🍀", "🌈", "☀️", "🌙", "❄️", "☕", "🍕", "🍔", "🍦", "🐶", "🐱", "🦋", "🐼",
    "👍", "👏", "🙌", "🙏", "✌️", "🤘", "👑", "💎", "🎵", "📸", "✈️", "🏖️", "⚽", "🏏", "🚗", "📍",
)

private val adjustTools: List<Triple<String, ClosedFloatingPointRange<Float>, (Adjust) -> Float>> = listOf(
    Triple("Exposure", -1f..1f, { it.exposure }),
    Triple("Brightness", -1f..1f, { it.brightness }),
    Triple("Contrast", -1f..1f, { it.contrast }),
    Triple("Highlights", -1f..1f, { it.highlights }),
    Triple("Shadows", -1f..1f, { it.shadows }),
    Triple("Saturation", -1f..1f, { it.saturation }),
    Triple("Vibrance", -1f..1f, { it.vibrance }),
    Triple("Warmth", -1f..1f, { it.warmth }),
    Triple("Tint", -1f..1f, { it.tint }),
    Triple("Fade", 0f..1f, { it.fade }),
    Triple("Vignette", 0f..1f, { it.vignette }),
    Triple("Sharpen", 0f..1f, { it.sharpen }),
    Triple("Blur", 0f..1f, { it.blur }),
    Triple("Grain", 0f..1f, { it.grain }),
)

private val effectTools: List<Triple<String, ClosedFloatingPointRange<Float>, (Adjust) -> Float>> = listOf(
    Triple("Structure", 0f..1f, { it.structure }),
    Triple("Drama", 0f..1f, { it.drama }),
    Triple("Glow", 0f..1f, { it.glow }),
    Triple("Lens blur", 0f..1f, { it.lensBlur }),
    Triple("Tilt-shift", 0f..1f, { it.tiltShift }),
    Triple("Vignette", 0f..1f, { it.vignette }),
    Triple("Grain", 0f..1f, { it.grain }),
    Triple("Fade", 0f..1f, { it.fade }),
)

private fun Adjust.with(name: String, v: Float): Adjust = when (name) {
    "Structure" -> copy(structure = v); "Drama" -> copy(drama = v); "Glow" -> copy(glow = v)
    "Lens blur" -> copy(lensBlur = v); "Tilt-shift" -> copy(tiltShift = v)
    "Exposure" -> copy(exposure = v); "Brightness" -> copy(brightness = v); "Contrast" -> copy(contrast = v)
    "Highlights" -> copy(highlights = v); "Shadows" -> copy(shadows = v); "Saturation" -> copy(saturation = v)
    "Vibrance" -> copy(vibrance = v); "Warmth" -> copy(warmth = v); "Tint" -> copy(tint = v)
    "Fade" -> copy(fade = v); "Vignette" -> copy(vignette = v); "Sharpen" -> copy(sharpen = v)
    "Blur" -> copy(blur = v); "Grain" -> copy(grain = v); else -> this
}

/** Loads a picture at most [maxSide] pixels on its longest side, already turned the right way up. */
fun loadBitmap(c: android.content.Context, uri: android.net.Uri, maxSide: Int): Bitmap {
    val src = ImageDecoder.createSource(c.contentResolver, uri)
    return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val w = info.size.width; val h = info.size.height
        val k = maxSide.toFloat() / max(w, h)
        if (k < 1f) decoder.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun EditorScreen(media: Media) {
    // The editor is always dark, like pro photo editors, so colours are judged correctly.
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB8C4FF), surface = Color(0xFF0E0F13), background = Color(0xFF0E0F13))) {
        EditorContent(media)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun EditorContent(media: Media) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderer = remember { Dispatchers.Default.limitedParallelism(1) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(EditState()) }
    val undo = remember { mutableStateListOf<EditState>() }
    val redo = remember { mutableStateListOf<EditState>() }
    var tool by remember { mutableStateOf(Tool.Adjust) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf<Long?>(null) }
    var selectedSticker by remember { mutableStateOf<Long?>(null) }
    var editingText by remember { mutableStateOf<TextLayer?>(null) }
    var brush by remember { mutableStateOf(Brush.Pen) }
    var brushColor by remember { mutableStateOf(palette[2]) }
    var brushSize by remember { mutableStateOf(0.012f) }
    var aspect by remember { mutableStateOf<Float?>(null) } // width / height in pixels, null = free
    val live = remember { mutableStateListOf<P>() } // stroke being drawn

    fun commit(new: EditState) {
        if (new == state) return
        undo.add(state); if (undo.size > 60) undo.removeAt(0)
        redo.clear()
        state = new
    }

    LaunchedEffect(media.uri) {
        try {
            source = withContext(Dispatchers.IO) { loadBitmap(ctx, media.uri, 1600) }
        } catch (t: Throwable) {
            loadError = "Can't edit this picture"
        }
    }

    // Live preview: re-drawn whenever something changes (only the latest change is drawn).
    val geometryView = tool == Tool.Crop
    LaunchedEffect(source, state, tool, comparing) {
        val src = source ?: return@LaunchedEffect
        delay(12)
        val s = when {
            comparing -> EditState(rotation = state.rotation, flipH = state.flipH, flipV = state.flipV, straighten = state.straighten, crop = state.crop)
            geometryView -> state.copy(crop = Crop(), strokes = emptyList(), texts = emptyList(), stickers = emptyList(), frame = Frame.None, watermark = "")
            tool == Tool.Frame -> state
            else -> state.copy(frame = Frame.None)
        }
        rendered = withContext(renderer) { Renderer.render(src, s) }
    }

    BackHandler { if (undo.isNotEmpty()) confirmExit = true else Nav.back() }

    app.gallery.ui.Immersive(false)
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0F13)).safeDrawingPadding()) {
        // Top bar
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (undo.isNotEmpty()) confirmExit = true else Nav.back() }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = undo.isNotEmpty(), onClick = { redo.add(state); state = undo.removeAt(undo.lastIndex) }) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = if (undo.isNotEmpty()) Color.White else Color(0x55FFFFFF))
            }
            IconButton(enabled = redo.isNotEmpty(), onClick = { undo.add(state); state = redo.removeAt(redo.lastIndex) }) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", tint = if (redo.isNotEmpty()) Color.White else Color(0x55FFFFFF))
            }
            // Hold to see the original.
            Box(
                Modifier.size(48.dp).clip(CircleShape).pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        comparing = true
                        do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                        comparing = false
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Compare, "Hold to compare", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = source != null && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val uri = withContext(Dispatchers.Default) {
                            runCatching {
                                val full = loadBitmap(ctx, media.uri, 4096)
                                val out = Renderer.render(full, state)
                                Actions.saveBitmap(ctx, out, media.name, if (media.id > 0) media.date else null)
                            }.getOrNull()
                        }
                        saving = false
                        if (uri != null) {
                            Toast.makeText(ctx, "Saved a copy to Pictures/Edited", Toast.LENGTH_SHORT).show()
                            Nav.back()
                        } else Toast.makeText(ctx, "Couldn't save", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save copy")
            }
            Spacer(Modifier.width(8.dp))
        }

        // Picture: pinch with two fingers to zoom in (up to 8×) and move around; tap "Fit" to go back.
        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var areaSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        fun clampPan(p: Offset, z: Float): Offset {
            val mx = areaSize.width * (z - 1f) / 2f; val my = areaSize.height * (z - 1f) / 2f
            return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
        }
        val panWithOneFinger = tool == Tool.Adjust || tool == Tool.Filters || tool == Tool.Effects || tool == Tool.Frame
        // With a text or sticker picked, pinching resizes it instead of zooming the picture.
        val layerPicked = (tool == Tool.Text && selectedText != null) || (tool == Tool.Stickers && selectedSticker != null)
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .onSizeChanged { areaSize = it }
                .pointerInput(panWithOneFinger, layerPicked) {
                    if (layerPicked) return@pointerInput
                    awaitEachGesture {
                        var multi = false
                        while (true) {
                            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val n = ev.changes.count { it.pressed }
                            if (n == 0) break
                            if (n >= 2) {
                                multi = true
                                zoom = (zoom * ev.calculateZoom()).coerceIn(1f, 8f)
                                pan = clampPan(pan + ev.calculatePan(), zoom)
                                ev.changes.forEach { it.consume() }
                            } else if (multi) {
                                ev.changes.forEach { it.consume() }
                            } else if (zoom > 1.01f && panWithOneFinger) {
                                pan = clampPan(pan + ev.calculatePan(), zoom)
                                ev.changes.forEach { it.consume() }
                            }
                        }
                        if (zoom < 1.03f) { zoom = 1f; pan = Offset.Zero }
                    }
                }
                .pointerInput(panWithOneFinger) {
                    if (panWithOneFinger) detectTapGestures(onDoubleTap = { o ->
                        if (zoom > 1.01f) { zoom = 1f; pan = Offset.Zero }
                        else { zoom = 2.5f; pan = clampPan(Offset((areaSize.width / 2f - o.x) * 1.5f, (areaSize.height / 2f - o.y) * 1.5f), 2.5f) }
                    })
                },
        ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
            contentAlignment = Alignment.Center,
        ) {
            val img = rendered
            if (loadError != null) Text(loadError!!, color = Color.White)
            else if (img == null) CircularProgressIndicator()
            else {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val k = min(boxW / img.width, boxH / img.height)
                val dw = img.width * k; val dh = img.height * k
                val left = (boxW - dw) / 2f; val top = (boxH - dh) / 2f
                Image(img.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

                fun norm(o: Offset) = P(((o.x - left) / dw).coerceIn(0f, 1f), ((o.y - top) / dh).coerceIn(0f, 1f))

                when (tool) {
                    Tool.Crop -> if (!comparing) CropOverlay(state.crop, left, top, dw, dh, aspect?.let { a ->
                        val src = source!!
                        val (rw, rh) = Renderer.rotatedSize(src.width, src.height, state)
                        a * rh / rw
                    }, onChange = { state = state.copy(crop = it) }, onEnd = { before -> if (before != state.crop) { undo.add(state.copy(crop = before)); redo.clear() } })

                    Tool.Draw -> Canvas(
                        Modifier.fillMaxSize().pointerInput(brush, brushColor, brushSize) {
                            detectDragGestures(
                                onDragStart = { o -> live.clear(); live.add(norm(o)) },
                                onDrag = { ch, _ -> live.add(norm(ch.position)) },
                                onDragEnd = {
                                    val pts = live.toList(); live.clear()
                                    if (brush == Brush.Eraser) {
                                        val r = 0.04f
                                        commit(state.copy(strokes = state.strokes.filter { st -> st.points.none { p -> pts.any { q -> hypot(p.x - q.x, p.y - q.y) < r } } }))
                                    } else if (pts.isNotEmpty()) {
                                        commit(state.copy(strokes = state.strokes + Stroke(pts, brushColor, brushSize, brush)))
                                    }
                                },
                            )
                        }
                    ) {
                        if (live.size > 1) {
                            val path = Path().apply {
                                live.forEachIndexed { i, p -> val x = left + p.x * dw; val y = top + p.y * dh; if (i == 0) moveTo(x, y) else lineTo(x, y) }
                            }
                            val side = min(dw, dh)
                            val c = if (brush == Brush.Eraser) Color(0x66FFFFFF) else Color(brushColor).copy(alpha = if (brush == Brush.Marker) 0.45f else 1f)
                            drawPath(path, c, style = DrawStroke(width = brushSize * side * (if (brush == Brush.Marker) 2.2f else if (brush == Brush.Eraser) 3f else 1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    Tool.Text, Tool.Stickers -> Box(
                        Modifier.fillMaxSize()
                            // Touch a text or sticker to pick it; drag to move, pinch to resize, twist to rotate.
                            .pointerInput(tool, left, top, dw, dh) {
                                awaitEachGesture {
                                    val first = awaitFirstDown()
                                    val p0 = norm(first.position)
                                    val side = min(dw, dh)
                                    if (tool == Tool.Text) {
                                        val hit = state.texts.lastOrNull { t ->
                                            val (bw, bh) = Renderer.textBounds(t)
                                            abs(p0.x - t.x) * dw < bw * side / 2 + 24 && abs(p0.y - t.y) * dh < bh * side / 2 + 24
                                        }
                                        selectedText = hit?.id
                                    } else {
                                        val hit = state.stickers.lastOrNull { st -> hypot((p0.x - st.x) * dw, (p0.y - st.y) * dh) < st.size * side * 0.7f + 24 }
                                        selectedSticker = hit?.id
                                    }
                                    val id = if (tool == Tool.Text) selectedText else selectedSticker
                                    if (id == null) return@awaitEachGesture
                                    first.consume()
                                    val before = state
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.none { it.pressed }) break
                                        val z = ev.calculateZoom(); val r = ev.calculateRotation(); val pan = ev.calculatePan()
                                        val dx = pan.x / dw; val dy = pan.y / dh
                                        state = if (tool == Tool.Text) state.copy(texts = state.texts.map {
                                            if (it.id == id) it.copy(
                                                x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f),
                                                size = (it.size * z).coerceIn(0.015f, 0.5f), rotation = it.rotation + r,
                                            ) else it
                                        }) else state.copy(stickers = state.stickers.map {
                                            if (it.id == id) it.copy(
                                                x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f),
                                                size = (it.size * z).coerceIn(0.03f, 1.2f), rotation = it.rotation + r,
                                            ) else it
                                        })
                                        ev.changes.forEach { it.consume() }
                                    }
                                    if (before != state) { undo.add(before); redo.clear() }
                                }
                            }
                    ) {
                        // Box around the selected text or sticker (turns with it).
                        val side = min(dw, dh)
                        val sel: Triple<P, androidx.compose.ui.geometry.Size, Float>? = if (tool == Tool.Text) {
                            state.texts.firstOrNull { it.id == selectedText }?.let { t ->
                                val (bw, bh) = Renderer.textBounds(t)
                                Triple(P(t.x, t.y), androidx.compose.ui.geometry.Size(bw * side, bh * side), t.rotation)
                            }
                        } else state.stickers.firstOrNull { it.id == selectedSticker }?.let { st ->
                            Triple(P(st.x, st.y), androidx.compose.ui.geometry.Size(st.size * side * 1.25f, st.size * side * 1.25f), st.rotation)
                        }
                        if (sel != null) {
                            val (c, sz, rot) = sel
                            Box(
                                Modifier.offset { IntOffset((left + c.x * dw - sz.width / 2).roundToInt(), (top + c.y * dh - sz.height / 2).roundToInt()) }
                                    .size(with(density) { sz.width.toDp() }, with(density) { sz.height.toDp() })
                                    .graphicsLayer { rotationZ = rot }
                                    .border(1.5.dp, Color(0xE6FFFFFF), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        if (zoom > 1.01f) Text(
            "Fit • ${(zoom * 100).roundToInt()}%", color = Color.White, style = Caption,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99000000))
                .clickable { zoom = 1f; pan = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        }

        // Tool panel
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            when (tool) {
                Tool.Crop -> CropPanel(
                    state, aspect,
                    onAspect = { a ->
                        aspect = a
                        val src = source ?: return@CropPanel
                        val (rw, rh) = Renderer.rotatedSize(src.width, src.height, state)
                        commit(state.copy(crop = fitAspect(a?.let { it * rh / rw })))
                    },
                    onRotate = { commit(state.copy(rotation = (state.rotation + 270) % 360, crop = Crop())) ; aspect = null },
                    onFlip = { commit(state.copy(flipH = !state.flipH)) },
                    onFlipV = { commit(state.copy(flipV = !state.flipV)) },
                    onStraighten = { state = state.copy(straighten = it) },
                    onStraightenEnd = { before -> if (before != state.straighten) { undo.add(state.copy(straighten = before)); redo.clear() } },
                    onReset = { commit(state.copy(rotation = 0, flipH = false, flipV = false, straighten = 0f, crop = Crop())); aspect = null },
                )
                Tool.Adjust -> AdjustPanel(
                    state.adjust,
                    onAuto = { source?.let { commit(state.copy(adjust = Renderer.autoEnhance(it))) } },
                    onChange = { state = state.copy(adjust = it) },
                    onCommit = { before -> if (before != state.adjust) { undo.add(state.copy(adjust = before)); redo.clear() } },
                )
                Tool.Effects -> AdjustPanel(
                    state.adjust, onAuto = null,
                    onChange = { state = state.copy(adjust = it) },
                    onCommit = { before -> if (before != state.adjust) { undo.add(state.copy(adjust = before)); redo.clear() } },
                    tools = effectTools,
                )
                Tool.Filters -> FiltersPanel(
                    source, state.filter, state.filterStrength,
                    onPick = { commit(state.copy(filter = it, filterStrength = 1f)) },
                    onStrength = { state = state.copy(filterStrength = it) },
                    onStrengthEnd = { before -> if (before != state.filterStrength) { undo.add(state.copy(filterStrength = before)); redo.clear() } },
                )
                Tool.Draw -> DrawPanel(
                    brush, brushColor, brushSize,
                    onBrush = { brush = it }, onColor = { brushColor = it }, onSize = { brushSize = it },
                    onClear = { commit(state.copy(strokes = emptyList())) },
                )
                Tool.Text -> TextPanel(
                    state.texts.firstOrNull { it.id == selectedText },
                    onAdd = { editingText = TextLayer(System.nanoTime(), "", 0.5f, 0.5f) },
                    onEdit = { editingText = it },
                    onChange = { t -> state = state.copy(texts = state.texts.map { if (it.id == t.id) t else it }) },
                    onCommit = { before -> if (before != state.texts) { undo.add(state.copy(texts = before)); redo.clear() } },
                    textsNow = { state.texts },
                    onDelete = { t -> commit(state.copy(texts = state.texts.filter { it.id != t.id })); selectedText = null },
                )
                Tool.Stickers -> StickerPanel(
                    state.stickers.firstOrNull { it.id == selectedSticker },
                    onAdd = { e ->
                        val s = Sticker(System.nanoTime(), e, 0.5f, 0.5f)
                        commit(state.copy(stickers = state.stickers + s)); selectedSticker = s.id
                    },
                    onSize = { st, v -> state = state.copy(stickers = state.stickers.map { if (it.id == st.id) it.copy(size = v) else it }) },
                    onDelete = { st -> commit(state.copy(stickers = state.stickers.filter { it.id != st.id })); selectedSticker = null },
                )
                Tool.Frame -> FramePanel(
                    state.frame, state.watermark,
                    onFrame = { commit(state.copy(frame = it)) },
                    onWatermark = { state = state.copy(watermark = it) },
                )
            }
        }

        // Tool tabs
        LazyRow(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(Tool.entries) { t ->
                val on = t == tool
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Color(0x26B8C4FF) else Color.Transparent)
                        .clickable { tool = t }.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, tint = if (on) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF))
                    Text(t.label, style = Caption, color = if (on) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }

    editingText?.let { t ->
        TextDialog(t, onDismiss = { editingText = null }) { text ->
            editingText = null
            if (text.isBlank()) return@TextDialog
            val exists = state.texts.any { it.id == t.id }
            commit(state.copy(texts = if (exists) state.texts.map { if (it.id == t.id) it.copy(text = text) else it } else state.texts + t.copy(text = text)))
            selectedText = t.id
        }
    }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Discard your edits?") },
        confirmButton = { TextButton(onClick = { confirmExit = false; Nav.back() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep editing") } },
    )
}

/** Largest centred crop box with the given shape ([r] = width/height in 0..1 units, null = whole picture). */
private fun fitAspect(r: Float?): Crop {
    if (r == null) return Crop()
    return if (r >= 1f) { val h = 1f / r; Crop(0f, (1f - h) / 2f, 1f, (1f + h) / 2f) }
    else { val w = r; Crop((1f - w) / 2f, 0f, (1f + w) / 2f, 1f) }
}

// ---------------------------------------------------------------- crop

@Composable
private fun CropOverlay(
    crop: Crop, left: Float, top: Float, w: Float, h: Float, ratio: Float?,
    onChange: (Crop) -> Unit, onEnd: (Crop) -> Unit,
) {
    var start by remember { mutableStateOf(crop) }
    var grab by remember { mutableStateOf("") }
    var cur by remember { mutableStateOf(crop) }
    cur = crop
    Canvas(
        Modifier.fillMaxSize().pointerInput(left, top, w, h, ratio) {
            val handle = 36.dp.toPx()
            detectDragGestures(
                onDragStart = { o ->
                    start = cur
                    val l = left + cur.l * w; val r = left + cur.r * w; val t = top + cur.t * h; val b = top + cur.b * h
                    val nearL = abs(o.x - l) < handle; val nearR = abs(o.x - r) < handle
                    val nearT = abs(o.y - t) < handle; val nearB = abs(o.y - b) < handle
                    grab = buildString {
                        if (nearT) append('t'); if (nearB) append('b'); if (nearL) append('l'); if (nearR) append('r')
                        if (isEmpty() && o.x in l..r && o.y in t..b) append('m')
                    }
                },
                onDrag = { ch, d ->
                    ch.consume()
                    val dx = d.x / w; val dy = d.y / h
                    var c = cur
                    val minS = 0.08f
                    if (grab == "m") {
                        val nl = (c.l + dx).coerceIn(0f, 1f - c.w); val nt = (c.t + dy).coerceIn(0f, 1f - c.h)
                        c = Crop(nl, nt, nl + c.w, nt + c.h)
                    } else {
                        if ('l' in grab) c = c.copy(l = (c.l + dx).coerceIn(0f, c.r - minS))
                        if ('r' in grab) c = c.copy(r = (c.r + dx).coerceIn(c.l + minS, 1f))
                        if ('t' in grab) c = c.copy(t = (c.t + dy).coerceIn(0f, c.b - minS))
                        if ('b' in grab) c = c.copy(b = (c.b + dy).coerceIn(c.t + minS, 1f))
                        if (ratio != null) {
                            // Keep the chosen shape: height follows width.
                            val newH = (c.w / ratio).coerceAtMost(1f)
                            c = if ('t' in grab) c.copy(t = (c.b - newH).coerceAtLeast(0f)) else c.copy(b = (c.t + newH).coerceAtMost(1f))
                            val fixedW = c.h * ratio
                            c = if ('l' in grab) c.copy(l = c.r - fixedW) else c.copy(r = c.l + fixedW)
                        }
                    }
                    cur = c
                    onChange(c)
                },
                onDragEnd = { onEnd(start) },
            )
        }
    ) {
        val l = left + crop.l * w; val r = left + crop.r * w; val t = top + crop.t * h; val b = top + crop.b * h
        val dim = Color(0x99000000)
        drawRect(dim, Offset(left, top), Size(w, t - top))
        drawRect(dim, Offset(left, b), Size(w, top + h - b))
        drawRect(dim, Offset(left, t), Size(l - left, b - t))
        drawRect(dim, Offset(r, t), Size(left + w - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = DrawStroke(2.dp.toPx()))
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f; val y = t + (b - t) * i / 3f
            drawLine(Color(0x66FFFFFF), Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color(0x66FFFFFF), Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
        val hl = 18.dp.toPx(); val sw = 4.dp.toPx()
        for ((cx, cy, sx, sy) in listOf(Quad(l, t, 1f, 1f), Quad(r, t, -1f, 1f), Quad(l, b, 1f, -1f), Quad(r, b, -1f, -1f))) {
            drawLine(Color.White, Offset(cx, cy), Offset(cx + hl * sx, cy), sw)
            drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + hl * sy), sw)
        }
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

@Composable
private fun CropPanel(
    s: EditState, aspect: Float?,
    onAspect: (Float?) -> Unit, onRotate: () -> Unit, onFlip: () -> Unit, onFlipV: () -> Unit,
    onStraighten: (Float) -> Unit, onStraightenEnd: (Float) -> Unit, onReset: () -> Unit,
) {
    var before by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Straighten", style = Caption, color = Color(0xCCFFFFFF), modifier = Modifier.width(76.dp))
            Slider(
                value = s.straighten, valueRange = -45f..45f,
                onValueChange = { if (!dragging) { dragging = true; before = s.straighten }; onStraighten(it) },
                onValueChangeFinished = { dragging = false; onStraightenEnd(before) },
                modifier = Modifier.weight(1f),
            )
            Text("${s.straighten.roundToInt()}°", color = Color.White, style = Caption, modifier = Modifier.width(36.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            item { PanelIcon(Icons.AutoMirrored.Rounded.RotateLeft, "Rotate", onRotate) }
            item { PanelIcon(Icons.Rounded.Flip, "Flip", onFlip) }
            item { PanelIcon(Icons.Rounded.Flip, "Flip ↕", onFlipV, rotate = 90f) }
            val ratios = listOf<Pair<String, Float?>>("Free" to null, "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "3:2" to 1.5f)
            items(ratios) { (label, r) -> FilterChip(selected = aspect == r, onClick = { onAspect(r) }, label = { Text(label) }) }
            item { TextButton(onClick = onReset) { Text("Reset") } }
        }
    }
}

@Composable
private fun PanelIcon(icon: ImageVector, label: String, onClick: () -> Unit, rotate: Float = 0f) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.graphicsLayer { rotationZ = rotate })
        Text(label, style = Caption, color = Color(0xCCFFFFFF))
    }
}

// ---------------------------------------------------------------- adjust

@Composable
private fun AdjustPanel(
    a: Adjust, onAuto: (() -> Unit)?, onChange: (Adjust) -> Unit, onCommit: (Adjust) -> Unit,
    tools: List<Triple<String, ClosedFloatingPointRange<Float>, (Adjust) -> Float>> = adjustTools,
) {
    var current by remember(tools) { mutableStateOf(tools.first().first) }
    var before by remember { mutableStateOf(a) }
    var dragging by remember { mutableStateOf(false) }
    val tool = tools.firstOrNull { it.first == current } ?: tools.first()
    val value = tool.third(a)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(current, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                (value * 100).roundToInt().let { if (it > 0 && tool.second.start < 0f) "+$it" else "$it" },
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { onCommit(a); onChange(a.with(current, 0f)) }) { Text("Reset") }
        }
        Slider(
            value = value, valueRange = tool.second,
            onValueChange = { if (!dragging) { dragging = true; before = a }; onChange(a.with(current, it)) },
            onValueChangeFinished = { dragging = false; onCommit(before) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onAuto != null) item {
                FilterChip(selected = false, onClick = onAuto, label = { Text("Auto") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) })
            }
            items(tools) { (name, _, get) ->
                val changed = get(a) != 0f
                FilterChip(
                    selected = name == current, onClick = { current = name },
                    label = { Text(if (changed) "$name •" else name) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- filters

@Composable
private fun FiltersPanel(
    source: Bitmap?, filter: String, strength: Float,
    onPick: (String) -> Unit, onStrength: (Float) -> Unit, onStrengthEnd: (Float) -> Unit,
) {
    var thumbs by remember { mutableStateOf<List<Pair<String, Bitmap>>>(emptyList()) }
    LaunchedEffect(source) {
        val src = source ?: return@LaunchedEffect
        thumbs = withContext(Dispatchers.Default) {
            val k = 180f / max(src.width, src.height)
            val small = Bitmap.createScaledBitmap(src, (src.width * k).roundToInt().coerceAtLeast(1), (src.height * k).roundToInt().coerceAtLeast(1), true)
            Filters.all.map { (name, _) -> name to Renderer.render(small, EditState(filter = name)) }
        }
    }
    var before by remember { mutableStateOf(strength) }
    var dragging by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (filter != "Original") Slider(
            value = strength, valueRange = 0f..1f,
            onValueChange = { if (!dragging) { dragging = true; before = strength }; onStrength(it) },
            onValueChangeFinished = { dragging = false; onStrengthEnd(before) },
            modifier = Modifier.padding(horizontal = 24.dp).height(36.dp),
        ) else Spacer(Modifier.height(36.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(thumbs, key = { it.first }) { (name, bmp) ->
                val on = name == filter
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(name) }) {
                    Image(
                        bmp.asImageBitmap(), name, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp))
                            .border(if (on) 2.5.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp)),
                    )
                    Text(name, style = Caption, color = if (on) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- draw

@Composable
private fun ColorRow(selected: Int, onColor: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(palette) { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF), CircleShape)
                    .clickable { onColor(c) }
            )
        }
    }
}

@Composable
private fun DrawPanel(
    brush: Brush, color: Int, size: Float,
    onBrush: (Brush) -> Unit, onColor: (Int) -> Unit, onSize: (Float) -> Unit, onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Brush.entries) { b -> FilterChip(selected = b == brush, onClick = { onBrush(b) }, label = { Text(b.name) }) }
            item { TextButton(onClick = onClear) { Text("Clear all") } }
        }
        ColorRow(color, onColor)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Size", style = Caption, color = Color(0xCCFFFFFF))
            Slider(value = size, valueRange = 0.003f..0.05f, onValueChange = onSize, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

// ---------------------------------------------------------------- text

@Composable
private fun TextPanel(
    t: TextLayer?, onAdd: () -> Unit, onEdit: (TextLayer) -> Unit, onChange: (TextLayer) -> Unit,
    onCommit: (List<TextLayer>) -> Unit, textsNow: () -> List<TextLayer>, onDelete: (TextLayer) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item { Button(onClick = onAdd) { Text("Add text") } }
            if (t != null) {
                item { IconButton(onClick = { onEdit(t) }) { Icon(Icons.Rounded.Edit, "Edit text", tint = Color.White) } }
                items(Font.entries) { f ->
                    FilterChip(selected = t.font == f, onClick = { val b = textsNow(); onChange(t.copy(font = f)); onCommit(b) }, label = { Text(f.name) })
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Box", style = Caption, color = Color.White)
                        Switch(checked = t.background, onCheckedChange = { val b = textsNow(); onChange(t.copy(background = it)); onCommit(b) }, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                item { IconButton(onClick = { onDelete(t) }) { Icon(Icons.Rounded.Delete, "Delete", tint = Color.White) } }
            }
        }
        if (t != null) {
            ColorRow(t.color) { c -> val b = textsNow(); onChange(t.copy(color = c)); onCommit(b) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Size", style = Caption, color = Color(0xCCFFFFFF))
                Slider(value = t.size, valueRange = 0.025f..0.2f, onValueChange = { onChange(t.copy(size = it)) }, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
        } else Text(
            "Add text. Drag it to move, pinch to resize, twist with two fingers to rotate. Tap a text to change it.",
            style = Caption, color = Color(0xAAFFFFFF), modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun TextDialog(t: TextLayer, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(t.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.text.isEmpty()) "Add text" else "Edit text") },
        text = { OutlinedTextField(text, { text = it }, minLines = 1, maxLines = 4) },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- stickers

@Composable
private fun StickerPanel(s: Sticker?, onAdd: (String) -> Unit, onSize: (Sticker, Float) -> Unit, onDelete: (Sticker) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (s != null) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.emoji, fontSize = 22.sp)
            Slider(value = s.size, valueRange = 0.05f..0.5f, onValueChange = { onSize(s, it) }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            IconButton(onClick = { onDelete(s) }) { Icon(Icons.Rounded.Delete, "Delete", tint = Color.White) }
        }
        LazyVerticalGrid(GridCells.Adaptive(44.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f)) {
            items(emojis) { e ->
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onAdd(e) }, contentAlignment = Alignment.Center) {
                    Text(e, fontSize = 26.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- frame

@Composable
private fun FramePanel(frame: Frame, watermark: String, onFrame: (Frame) -> Unit, onWatermark: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Frame.entries) { f ->
                val label = when (f) {
                    Frame.None -> "None"; Frame.ThinWhite -> "Thin"; Frame.ThickWhite -> "Gallery"; Frame.Black -> "Black"
                    Frame.Polaroid -> "Polaroid"; Frame.Rounded -> "Rounded"
                }
                FilterChip(selected = f == frame, onClick = { onFrame(f) }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = watermark, onValueChange = onWatermark, singleLine = true,
            label = { Text("Watermark (bottom-right)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Suppress("unused")
private fun Color.argb(): Int = toArgb()
