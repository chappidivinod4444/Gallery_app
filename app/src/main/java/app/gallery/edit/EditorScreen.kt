package app.gallery.edit

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gallery.data.Actions
import app.gallery.data.Media
import app.gallery.ui.Nav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Loads a picture at most [maxSide] pixels on its longest side, already turned the right way up. */
fun loadBitmap(c: android.content.Context, uri: Uri, maxSide: Int): Bitmap {
    val src = ImageDecoder.createSource(c.contentResolver, uri)
    return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val w = info.size.width; val h = info.size.height
        val k = maxSide.toFloat() / max(w, h)
        if (k < 1f) decoder.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
    }
}

@Composable
fun EditorScreen(media: Media) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Ed.accent, surface = Ed.panel, background = Ed.bg, surfaceContainerHigh = Ed.panel)) {
        EditorContent(media)
    }
}

/** A text, sticker or photo on screen: centre, size (display px) and rotation, for picking and handles. */
private data class Box2(val id: Long, val cx: Float, val cy: Float, val w: Float, val h: Float, val rot: Float)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun EditorContent(media: Media) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val worker = remember { Dispatchers.Default.limitedParallelism(1) }

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var proxy by remember { mutableStateOf<Bitmap?>(null) }
    var small by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var state by remember { mutableStateOf(EditState()) }
    val undo = remember { mutableStateListOf<EditState>() }
    val redo = remember { mutableStateListOf<EditState>() }
    var tool by remember { mutableStateOf<Tool?>(null) }
    var base by remember { mutableStateOf(EditState()) }
    val toolUndo = remember { mutableStateListOf<EditState>() }
    val toolRedo = remember { mutableStateListOf<EditState>() }

    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var showEdits by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var pickPhoto by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf<TextLayer?>(null) }

    // Per-tool choices
    var paramIdx by remember { mutableIntStateOf(0) }
    var pillTick by remember { mutableLongStateOf(0L) }
    var pillOn by remember { mutableStateOf(false) }
    var menuIdx by remember { mutableIntStateOf(-1) }
    var fxIdx by remember { mutableIntStateOf(0) }
    var blurIdx by remember { mutableIntStateOf(0) }
    var band by remember { mutableIntStateOf(0) }
    var curveCh by remember { mutableIntStateOf(0) }
    var selText by remember { mutableStateOf<Long?>(null) }
    var selSticker by remember { mutableStateOf<Long?>(null) }
    var selPhoto by remember { mutableStateOf<Long?>(null) }
    var selPoint by remember { mutableStateOf<Long?>(null) }
    var localKind by remember { mutableStateOf(Local.DodgeBurn) }
    var localLevel by remember { mutableFloatStateOf(0.5f) }
    var localSize by remember { mutableFloatStateOf(0.06f) }
    var showMask by remember { mutableStateOf(false) }
    var healSize by remember { mutableFloatStateOf(0.03f) }
    var pen by remember { mutableStateOf(Brush.Pen) }
    var penColor by remember { mutableIntStateOf(Palette[3]) }
    var penSize by remember { mutableFloatStateOf(0.012f) }
    var penOpacity by remember { mutableFloatStateOf(1f) }
    var eyedropper by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    val live = remember { mutableStateListOf<P>() }
    val photoBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    fun commit(new: EditState) {
        if (new == state) return
        if (tool != null) { toolUndo.add(state); toolRedo.clear() } else { undo.add(state); if (undo.size > 60) undo.removeAt(0); redo.clear() }
        state = new
    }
    fun mark(before: EditState) {
        if (before == state) return
        if (tool != null) { toolUndo.add(before); toolRedo.clear() } else { undo.add(before); redo.clear() }
    }
    fun open(t: Tool) {
        base = state; toolUndo.clear(); toolRedo.clear(); tool = t; paramIdx = 0; menuIdx = -1; eyedropper = false
        selText = null; selSticker = null; selPhoto = null; selPoint = null
    }
    fun apply() {
        if (state != base) { undo.add(base); if (undo.size > 60) undo.removeAt(0); redo.clear() }
        tool = null
    }
    fun cancel() { state = base; tool = null }
    fun pill() { pillOn = true; pillTick = System.nanoTime() }

    LaunchedEffect(media.uri) {
        try {
            withContext(Dispatchers.IO) {
                val b = loadBitmap(ctx, media.uri, 1600)
                val k = 760f / max(b.width, b.height)
                val p = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true) else b
                val k2 = 150f / max(b.width, b.height)
                val s = Bitmap.createScaledBitmap(b, (b.width * k2).roundToInt().coerceAtLeast(1), (b.height * k2).roundToInt().coerceAtLeast(1), true)
                source = b; proxy = p; small = s
            }
        } catch (t: Throwable) {
            loadError = "Can't edit this picture"
        }
    }
    LaunchedEffect(pillTick) { if (pillOn) { delay(1300); if (!dragging) pillOn = false } }

    // ---------------------------------------------------------------- live preview
    val geometryView = tool == Tool.Crop || tool == Tool.Perspective
    val layerTool = tool == Tool.Text || tool == Tool.Stickers || tool == Tool.Photo || tool == Tool.Draw
    val renderKey = when {
        comparing -> EditState(rotation = state.rotation, flipH = state.flipH, flipV = state.flipV, straighten = state.straighten, crop = state.crop)
        geometryView -> state.copy(crop = Crop(), strokes = emptyList(), texts = emptyList(), stickers = emptyList(), photos = emptyList(), frame = Frame.None, watermark = "")
        // Texts, stickers, photos and drawings are drawn live on top, so moving them is instant.
        layerTool -> state.copy(strokes = emptyList(), texts = emptyList(), stickers = emptyList(), photos = emptyList(), frame = Frame.None, watermark = "")
        tool == null || tool == Tool.Frame -> state
        else -> state.copy(frame = Frame.None)
    }
    LaunchedEffect(source, renderKey, dragging, photoBitmaps.size) {
        val src = (if (dragging) proxy else source) ?: return@LaunchedEffect
        if (!dragging) delay(10)
        val key = renderKey
        val out = withContext(worker) { runCatching { Renderer.render(src, key, { photoBitmaps[it] }) }.getOrNull() }
        if (out != null) rendered = out
    }

    BackHandler {
        when {
            tool != null -> cancel()
            undo.isNotEmpty() -> confirmExit = true
            else -> Nav.back()
        }
    }

    // ---------------------------------------------------------------- sliders of the current tool
    val fx: Fx? = when (tool) { Tool.Effects -> Tools.effects[fxIdx]; Tool.Blur -> Tools.blurs[blurIdx]; else -> null }
    val selCp = state.points.firstOrNull { it.id == selPoint }
    val params: List<Param> = when (tool) {
        Tool.Tune -> Tools.tune
        Tool.Details -> Tools.details
        Tool.Balance -> Tools.balance
        Tool.Perspective -> Tools.perspective
        Tool.Color -> listOf(Params.hue(band), Params.sat(band), Params.lum(band))
        Tool.Effects, Tool.Blur -> fx!!.params
        Tool.Looks -> if (state.filter != "Original") listOf(Tools.lookStrength) else emptyList()
        Tool.Selective -> if (selCp != null) Tools.selective else emptyList()
        else -> emptyList()
    }
    val param = params.getOrNull(paramIdx.coerceIn(0, max(0, params.lastIndex)))
    val paramsNow by rememberUpdatedState(params)
    val paramNow by rememberUpdatedState(param)
    fun getV(p: Param): Float {
        val cp = state.points.firstOrNull { it.id == selPoint }
        return when (p.key) {
            "_look" -> state.filterStrength
            "_sb" -> cp?.brightness ?: 0f
            "_sc" -> cp?.contrast ?: 0f
            "_ss" -> cp?.saturation ?: 0f
            "_sw" -> cp?.warmth ?: 0f
            "_sz" -> cp?.size ?: p.def
            else -> state.f(p)
        }
    }
    fun setV(p: Param, value: Float) {
        val v = value.coerceIn(p.min, p.max).let { if (p.choices != null) it.roundToInt().toFloat() else it }
        state = when (p.key) {
            "_look" -> state.copy(filterStrength = v)
            "_sb", "_sc", "_ss", "_sw", "_sz" -> state.copy(points = state.points.map {
                if (it.id != selPoint) it else when (p.key) {
                    "_sb" -> it.copy(brightness = v); "_sc" -> it.copy(contrast = v); "_ss" -> it.copy(saturation = v)
                    "_sw" -> it.copy(warmth = v); else -> it.copy(size = v)
                }
            })
            else -> state.set(p, v)
        }
    }

    val swipeTool = tool in setOf(Tool.Tune, Tool.Details, Tool.Balance, Tool.Perspective, Tool.Color, Tool.Effects, Tool.Blur, Tool.Looks, Tool.Selective)

    app.gallery.ui.Immersive(false)
    Column(Modifier.fillMaxSize().background(Ed.bg).safeDrawingPadding()) {
        // ------------------------------------------------------------ top bar
        if (tool == null) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (undo.isNotEmpty()) confirmExit = true else Nav.back() }) { Icon(Icons.Rounded.Close, "Close", tint = Ed.text) }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = undo.isNotEmpty(), onClick = { redo.add(state); state = undo.removeAt(undo.lastIndex) }) {
                Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = if (undo.isNotEmpty()) Ed.text else Ed.faint)
            }
            IconButton(enabled = redo.isNotEmpty(), onClick = { undo.add(state); state = redo.removeAt(redo.lastIndex) }) {
                Icon(Icons.AutoMirrored.Rounded.Redo, "Redo", tint = if (redo.isNotEmpty()) Ed.text else Ed.faint)
            }
            val edits = Tool.entries.count { Tools.changed(it, state) }
            Box {
                IconButton(onClick = { showEdits = true }) { Icon(Icons.Rounded.Layers, "Edits", tint = Ed.text) }
                if (edits > 0) Text(
                    "$edits", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp).clip(CircleShape).background(Ed.accent).padding(horizontal = 5.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(Ed.accent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.fillMaxHeight38().clickable(enabled = source != null && !saving) {
                        saving = true
                        scope.launch {
                            val uri = exportEdit(ctx, media, state, Bitmap.CompressFormat.JPEG, 95, 4096)
                            saving = false
                            if (uri != null) { Toast.makeText(ctx, "Saved to Pictures/Edited", Toast.LENGTH_SHORT).show(); Nav.back() }
                            else Toast.makeText(ctx, "Couldn't save — try a smaller size in Export", Toast.LENGTH_LONG).show()
                        }
                    }.padding(start = 16.dp, end = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Text("Save", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.width(1.dp).height(20.dp).background(Color(0x40000000)))
                Box(Modifier.fillMaxHeight38().clickable { showExport = true }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "More save options", tint = Color.Black)
                }
            }
            Spacer(Modifier.width(8.dp))
        } else Spacer(Modifier.height(8.dp))

        // ------------------------------------------------------------ picture
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var area by remember { mutableStateOf(IntSize.Zero) }
        fun clampPan(p: Offset, z: Float): Offset {
            val mx = area.width * (z - 1f) / 2f; val my = area.height * (z - 1f) / 2f
            return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
        }
        val layerPicked = (tool == Tool.Text && selText != null) || (tool == Tool.Stickers && selSticker != null) || (tool == Tool.Photo && selPhoto != null)
        val focusFx = fx?.focus?.takeIf { it != Focus.None }

        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { area = it }
                // Two fingers: zoom and move the picture (or resize the blur focus / picked layer).
                .pointerInput(layerPicked, focusFx) {
                    if (layerPicked) return@pointerInput
                    awaitEachGesture {
                        var multi = false
                        var sizeStart = -1f
                        val sizeParam = when (focusFx) { Focus.Lens -> Params.lensSize; Focus.Tilt -> Params.tiltSize; Focus.Vignette -> Params.vigSize; else -> null }
                        var acc = 1f
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val n = ev.changes.count { it.pressed }
                            if (n == 0) break
                            if (n >= 2) {
                                if (!multi) { multi = true; sizeStart = sizeParam?.let { state.f(it) } ?: -1f; acc = 1f }
                                if (sizeParam != null) {
                                    acc *= ev.calculateZoom()
                                    state = state.set(sizeParam, sizeStart * acc); paramIdx = paramsNow.indexOf(sizeParam).coerceAtLeast(0); pill()
                                } else {
                                    zoom = (zoom * ev.calculateZoom()).coerceIn(1f, 8f)
                                    pan = clampPan(pan + ev.calculatePan(), zoom)
                                }
                                ev.changes.forEach { it.consume() }
                            } else if (multi) ev.changes.forEach { it.consume() }
                        }
                        if (zoom < 1.03f) { zoom = 1f; pan = Offset.Zero }
                    }
                },
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                contentAlignment = Alignment.Center,
            ) {
                val img = rendered
                if (loadError != null) Text(loadError!!, color = Ed.text)
                else if (img == null) CircularProgressIndicator()
                else {
                    val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
                    val k = min(boxW / img.width, boxH / img.height)
                    val dw = img.width * k; val dh = img.height * k
                    val left = (boxW - dw) / 2f; val top = (boxH - dh) / 2f
                    val side = min(dw, dh)
                    fun norm(o: Offset) = P(((o.x - left) / dw).coerceIn(0f, 1f), ((o.y - top) / dh).coerceIn(0f, 1f))
                    fun toPx(p: P) = Offset(left + p.x * dw, top + p.y * dh)
                    val slop = with(density) { 10.dp.toPx() }
                    val grabR = with(density) { 30.dp.toPx() }

                    // The picture (and, for layer tools, the layers drawn live on top in the same canvas).
                    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.drawBitmap(img, null, RectF(left, top, left + dw, top + dh), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            if (layerTool && !comparing) {
                                nc.save(); nc.translate(left, top); nc.clipRect(0f, 0f, dw, dh)
                                for (l in state.photos) photoBitmaps[l.uri]?.let { Renderer.drawPhoto(nc, dw.roundToInt(), dh.roundToInt(), l, it) }
                                Renderer.drawOverlays(nc, dw.roundToInt(), dh.roundToInt(), state)
                                nc.restore()
                            }
                        }
                    }

                    // ---------------- gestures and on-picture guides for each tool
                    when {
                        tool == null -> Box(Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    coroutineScope {
                                        val j = launch { delay(220); comparing = true }
                                        tryAwaitRelease(); j.cancel(); comparing = false
                                    }
                                },
                                onDoubleTap = { o ->
                                    if (zoom > 1.01f) { zoom = 1f; pan = Offset.Zero }
                                    else { zoom = 2.5f; pan = clampPan(Offset((area.width / 2f - o.x) * 1.5f, (area.height / 2f - o.y) * 1.5f), 2.5f) }
                                },
                            )
                        })

                        tool == Tool.Crop -> if (!comparing) CropOverlay(state.crop, left, top, dw, dh, aspect?.let { a ->
                            val src = source!!
                            val (rw, rh) = Renderer.rotatedSize(src.width, src.height, state)
                            a * rh / rw
                        }, onChange = { state = state.copy(crop = it) }, onEnd = { before -> if (before != state.crop) { toolUndo.add(state.copy(crop = before)); toolRedo.clear() } })

                        tool == Tool.Curves -> CurvesOverlay(state.curves, curveCh, left, top, dw, dh,
                            onChange = { state = state.copy(curves = it) },
                            onEnd = { before -> if (before != state.curves) { toolUndo.add(state.copy(curves = before)); toolRedo.clear() } })

                        tool == Tool.Healing || tool == Tool.Brush || tool == Tool.Draw -> {
                            Box(Modifier.fillMaxSize().pointerInput(tool, left, top, dw, dh) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    live.clear(); live.add(norm(down.position))
                                    var ok = true
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.size > 1 || ev.changes.any { it.isConsumed }) { ok = false; break }
                                        val c = ev.changes[0]
                                        if (!c.pressed) break
                                        live.add(norm(c.position)); c.consume()
                                    }
                                    val pts = live.toList(); live.clear()
                                    if (!ok || pts.isEmpty()) return@awaitEachGesture
                                    when (tool) {
                                        Tool.Healing -> commit(state.copy(heals = state.heals + HealSpot(pts.map { state.toFrame(it) }, healSize * state.crop.w)))
                                        Tool.Brush -> commit(state.copy(masks = state.masks + MaskStroke(localKind, localLevel, pts.map { state.toFrame(it) }, localSize * state.crop.w)))
                                        else -> if (pen == Brush.Eraser) {
                                            val r = 0.04f
                                            commit(state.copy(strokes = state.strokes.filter { st -> st.points.none { p -> pts.any { q -> hypot(p.x - q.x, p.y - q.y) < r } } }))
                                        } else commit(state.copy(strokes = state.strokes + Stroke(if (pen.isShape) listOf(pts.first(), pts.last()) else pts, penColor, penSize, pen, penOpacity)))
                                    }
                                }
                            })
                            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                                // Painted areas of the local brush, in red, when "Show mask" is on.
                                if (tool == Tool.Brush && showMask) for (m in state.masks.filter { it.kind == localKind }) {
                                    val path = Path().apply { m.points.map { toPx(state.toOut(it)) }.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                                    val wdt = m.width / state.crop.w * side * 2f
                                    drawPath(
                                        path, if (m.amount > 0) Color(0x99FF3B30) else Color(0x990A84FF),
                                        style = DrawStroke(wdt, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                        blendMode = if (m.amount == 0f) BlendMode.Clear else BlendMode.SrcOver,
                                    )
                                }
                                if (live.size > 0) {
                                    val pts = if (tool == Tool.Draw && pen.isShape) listOf(live.first(), live.last()) else live
                                    val path = Path().apply { pts.forEachIndexed { i, p -> val o = toPx(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                                    val (col, wdt) = when (tool) {
                                        Tool.Healing -> Color(0x80FFFFFF) to healSize * side * 2f
                                        Tool.Brush -> (if (localLevel == 0f) Color(0x660A84FF) else Color(0x80FF3B30)) to localSize * side * 2f
                                        else -> (if (pen == Brush.Eraser) Color(0x66FFFFFF) else Color(penColor).copy(alpha = penOpacity * (if (pen == Brush.Marker) 0.45f else 1f))) to
                                            penSize * side * (if (pen == Brush.Marker) 2.2f else if (pen == Brush.Eraser) 3f else 1f)
                                    }
                                    if (tool == Tool.Draw && pen == Brush.Rect && pts.size == 2) {
                                        val a = toPx(pts[0]); val b = toPx(pts[1])
                                        drawRect(col, Offset(min(a.x, b.x), min(a.y, b.y)), androidx.compose.ui.geometry.Size(abs(a.x - b.x), abs(a.y - b.y)), style = DrawStroke(wdt))
                                    } else if (tool == Tool.Draw && pen == Brush.Circle && pts.size == 2) {
                                        val a = toPx(pts[0]); val b = toPx(pts[1])
                                        drawOval(col, Offset(min(a.x, b.x), min(a.y, b.y)), androidx.compose.ui.geometry.Size(abs(a.x - b.x), abs(a.y - b.y)), style = DrawStroke(wdt))
                                    } else drawPath(path, col, style = DrawStroke(wdt, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }

                        layerTool -> {
                            // Boxes of the layers this tool works on.
                            fun boxes(): List<Box2> = when (tool) {
                                Tool.Text -> state.texts.map { t -> val (bw, bh) = Renderer.textBounds(t); Box2(t.id, left + t.x * dw, top + t.y * dh, bw * side, bh * side, t.rotation) }
                                Tool.Stickers -> state.stickers.map { s -> Box2(s.id, left + s.x * dw, top + s.y * dh, s.size * side * 1.25f, s.size * side * 1.25f, s.rotation) }
                                Tool.Photo -> state.photos.map { l -> val w = l.scale * dw; Box2(l.id, left + l.x * dw, top + l.y * dh, w, w * l.aspect, l.rotation) }
                                else -> emptyList()
                            }
                            val selId = when (tool) { Tool.Text -> selText; Tool.Stickers -> selSticker; Tool.Photo -> selPhoto; else -> null }
                            fun select(id: Long?) { when (tool) { Tool.Text -> selText = id; Tool.Stickers -> selSticker = id; Tool.Photo -> selPhoto = id; else -> {} } }
                            fun corner(b: Box2, sx: Float, sy: Float): Offset {
                                val a = Math.toRadians(b.rot.toDouble()); val x = sx * b.w / 2f; val y = sy * b.h / 2f
                                return Offset(b.cx + (x * cos(a) - y * sin(a)).toFloat(), b.cy + (x * sin(a) + y * cos(a)).toFloat())
                            }
                            fun hit(b: Box2, p: Offset, pad: Float): Boolean {
                                val a = -Math.toRadians(b.rot.toDouble()); val dx = p.x - b.cx; val dy = p.y - b.cy
                                val x = dx * cos(a) - dy * sin(a); val y = dx * sin(a) + dy * cos(a)
                                return abs(x) < b.w / 2 + pad && abs(y) < b.h / 2 + pad
                            }
                            /** Moves / resizes / turns a layer, relative to its state in [from]. */
                            fun change(from: EditState, id: Long, dx: Float, dy: Float, scale: Float, dRot: Float): EditState = when (tool) {
                                Tool.Text -> from.copy(texts = from.texts.map { if (it.id != id) it else it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f), size = (it.size * scale).coerceIn(0.012f, 0.6f), rotation = it.rotation + dRot) })
                                Tool.Stickers -> from.copy(stickers = from.stickers.map { if (it.id != id) it else it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f), size = (it.size * scale).coerceIn(0.03f, 1.6f), rotation = it.rotation + dRot) })
                                else -> from.copy(photos = from.photos.map { if (it.id != id) it else it.copy(x = (it.x + dx).coerceIn(-0.2f, 1.2f), y = (it.y + dy).coerceIn(-0.2f, 1.2f), scale = (it.scale * scale).coerceIn(0.05f, 4f), rotation = it.rotation + dRot) })
                            }
                            fun delete(id: Long) {
                                commit(when (tool) {
                                    Tool.Text -> state.copy(texts = state.texts.filter { it.id != id })
                                    Tool.Stickers -> state.copy(stickers = state.stickers.filter { it.id != id })
                                    else -> state.copy(photos = state.photos.filter { it.id != id })
                                }); select(null)
                            }
                            var lastTap by remember { mutableLongStateOf(0L) }
                            Box(Modifier.fillMaxSize().pointerInput(tool, left, top, dw, dh, selId) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val p0 = down.position
                                    val before = state
                                    val cur = boxes().firstOrNull { it.id == selId }
                                    // Handles of the picked layer: ✕ top-left deletes, ⤡ bottom-right resizes and turns.
                                    if (cur != null && (p0 - corner(cur, -1f, -1f)).getDistance() < grabR) {
                                        down.consume(); delete(cur.id)
                                        while (awaitPointerEvent().changes.any { it.pressed }) { /* wait for release */ }
                                        return@awaitEachGesture
                                    }
                                    if (cur != null && (p0 - corner(cur, 1f, 1f)).getDistance() < grabR) {
                                        down.consume()
                                        val c0 = Offset(cur.cx, cur.cy); val v0 = p0 - c0
                                        val a0 = atan2(v0.y, v0.x); val d0 = v0.getDistance().coerceAtLeast(1f)
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!c.pressed) break
                                            val v = c.position - c0
                                            val rot = Math.toDegrees((atan2(v.y, v.x) - a0).toDouble()).toFloat()
                                            state = change(before, cur.id, 0f, 0f, v.getDistance() / d0, rot)
                                            c.consume()
                                        }
                                        mark(before); return@awaitEachGesture
                                    }
                                    val hitBox = boxes().lastOrNull { hit(it, p0, 12f) }
                                    if (hitBox == null) { select(null); return@awaitEachGesture }
                                    val now = System.currentTimeMillis()
                                    if (hitBox.id == selId && now - lastTap < 300 && tool == Tool.Text) {
                                        editingText = state.texts.firstOrNull { it.id == hitBox.id }
                                    }
                                    lastTap = now
                                    select(hitBox.id)
                                    down.consume()
                                    // Drag to move, pinch to resize, twist to turn.
                                    var s = before
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.none { it.pressed }) break
                                        val z = ev.calculateZoom(); val r = ev.calculateRotation(); val pn = ev.calculatePan()
                                        s = change(s, hitBox.id, pn.x / dw, pn.y / dh, z, r)
                                        state = s
                                        ev.changes.forEach { it.consume() }
                                    }
                                    mark(before)
                                }
                            })
                            // Selection box with handles.
                            val cur = boxes().firstOrNull { it.id == selId }
                            if (cur != null) Canvas(Modifier.fillMaxSize()) {
                                rotate(cur.rot, Offset(cur.cx, cur.cy)) {
                                    drawRoundRect(
                                        Color.White, Offset(cur.cx - cur.w / 2, cur.cy - cur.h / 2), androidx.compose.ui.geometry.Size(cur.w, cur.h),
                                        androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = DrawStroke(1.5.dp.toPx()),
                                    )
                                }
                                val r = 13.dp.toPx()
                                val del = corner(cur, -1f, -1f); val rs = corner(cur, 1f, 1f)
                                drawCircle(Color.White, r, del); drawCircle(Color.White, r, rs)
                                val l = 4.5.dp.toPx(); val sw = 2.dp.toPx()
                                drawLine(Color.Black, del + Offset(-l, -l), del + Offset(l, l), sw); drawLine(Color.Black, del + Offset(-l, l), del + Offset(l, -l), sw)
                                drawLine(Color.Black, rs + Offset(-l, -l), rs + Offset(l, l), sw)
                                drawLine(Color.Black, rs + Offset(l, l), rs + Offset(l - 4.dp.toPx(), l), sw); drawLine(Color.Black, rs + Offset(l, l), rs + Offset(l, l - 4.dp.toPx()), sw)
                                drawLine(Color.Black, rs + Offset(-l, -l), rs + Offset(-l + 4.dp.toPx(), -l), sw); drawLine(Color.Black, rs + Offset(-l, -l), rs + Offset(-l, -l + 4.dp.toPx()), sw)
                            }
                        }

                        swipeTool -> {
                            val areaW = boxW
                            Box(Modifier.fillMaxSize().pointerInput(tool, params, left, top, dw, dh, eyedropper, focusFx, selPoint) {
                                val itemH = 40.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val p0 = down.position
                                    val before = state
                                    // Something under the finger to drag? (a Selective point or the blur / vignette centre)
                                    val cps = state.points.map { it to toPx(state.toOut(P(it.x, it.y))) }
                                    val grabbedPoint = if (tool == Tool.Selective) cps.lastOrNull { (it.second - p0).getDistance() < grabR }?.first else null
                                    val centre = when (focusFx) {
                                        Focus.Lens -> P(state.f(Params.lensX), state.f(Params.lensY))
                                        Focus.Tilt -> P(0.5f, state.f(Params.tiltY))
                                        Focus.Vignette -> P(state.f(Params.vigX), state.f(Params.vigY))
                                        else -> null
                                    }
                                    val grabbedCentre = centre != null && (toPx(centre) - p0).getDistance() < grabR * 1.4f
                                    if (grabbedPoint != null) selPoint = grabbedPoint.id
                                    var mode = 0 // 1 move thing, 2 adjust value, 3 pick slider
                                    var total = Offset.Zero
                                    val startIdx = paramIdx
                                    val param = paramNow
                                    val params = paramsNow
                                    val startV = param?.let { getV(it) } ?: 0f
                                    var cancelled = false
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.size > 1 || ev.changes.any { it.isConsumed }) { cancelled = true; break }
                                        val c = ev.changes[0]
                                        if (!c.pressed) break
                                        total += c.position - c.previousPosition
                                        if (mode == 0 && total.getDistance() > slop) {
                                            mode = when {
                                                grabbedPoint != null || grabbedCentre -> 1
                                                abs(total.x) > abs(total.y) -> 2
                                                else -> 3
                                            }
                                            if (mode == 2) dragging = true
                                        }
                                        when (mode) {
                                            1 -> {
                                                val o = norm(c.position)
                                                if (grabbedPoint != null) {
                                                    val f = state.toFrame(o)
                                                    state = state.copy(points = state.points.map { if (it.id == grabbedPoint.id) it.copy(x = f.x, y = f.y) else it })
                                                } else state = when (focusFx) {
                                                    Focus.Lens -> state.setAll(Params.lensX to o.x, Params.lensY to o.y)
                                                    Focus.Tilt -> state.set(Params.tiltY, o.y)
                                                    else -> state.setAll(Params.vigX to o.x, Params.vigY to o.y)
                                                }
                                            }
                                            2 -> param?.let { p ->
                                                val steps = if (p.choices != null) total.x / (70.dp.toPx()) else total.x / areaW * (p.max - p.min) * 0.85f
                                                setV(p, startV + steps); pill()
                                            }
                                            3 -> if (params.isNotEmpty()) menuIdx = (startIdx + (total.y / itemH).roundToInt()).coerceIn(0, params.lastIndex)
                                        }
                                        c.consume()
                                    }
                                    if (mode == 3 && menuIdx >= 0) { paramIdx = menuIdx; pill() }
                                    menuIdx = -1
                                    dragging = false
                                    if (cancelled && mode == 2) { state = before; return@awaitEachGesture }
                                    if (mode == 0 && !cancelled) {
                                        // A tap.
                                        val o = norm(p0)
                                        when {
                                            tool == Tool.Balance && eyedropper -> rendered?.let { bmp ->
                                                val c = Renderer.sample(bmp, o.x, o.y)
                                                val r = android.graphics.Color.red(c) / 255f; val g = android.graphics.Color.green(c) / 255f; val b = android.graphics.Color.blue(c) / 255f
                                                val dt = ((b - r) / (0.12f * (r + b)).coerceAtLeast(0.02f)).coerceIn(-1f, 1f)
                                                val m = (r + b) / 2f
                                                val dti = ((g - m) / (0.11f * g + 0.05f * m).coerceAtLeast(0.02f)).coerceIn(-1f, 1f)
                                                state = state.setAll(Params.temp to state.f(Params.temp) + dt, Params.wbTint to state.f(Params.wbTint) + dti)
                                                eyedropper = false; paramIdx = 0; pill()
                                            }
                                            tool == Tool.Selective && grabbedPoint == null -> {
                                                val f = state.toFrame(o)
                                                val cp = CtrlPoint(System.nanoTime(), f.x, f.y, size = 0.18f)
                                                state = state.copy(points = state.points + cp); selPoint = cp.id; paramIdx = 0; pill()
                                            }
                                            focusFx != null && !grabbedCentre -> state = when (focusFx) {
                                                Focus.Lens -> state.setAll(Params.lensX to o.x, Params.lensY to o.y)
                                                Focus.Tilt -> state.set(Params.tiltY, o.y)
                                                else -> state.setAll(Params.vigX to o.x, Params.vigY to o.y)
                                            }
                                            else -> {}
                                        }
                                    }
                                    mark(before)
                                }
                            })
                            // Guides on the picture.
                            Canvas(Modifier.fillMaxSize()) {
                                if (tool == Tool.Perspective) {
                                    for (i in 1..5) {
                                        val x = left + dw * i / 6f; val y = top + dh * i / 6f
                                        drawLine(Color(0x55FFFFFF), Offset(x, top), Offset(x, top + dh), 1.dp.toPx())
                                        drawLine(Color(0x55FFFFFF), Offset(left, y), Offset(left + dw, y), 1.dp.toPx())
                                    }
                                }
                                if (tool == Tool.Selective) for (cp in state.points) {
                                    val o = toPx(state.toOut(P(cp.x, cp.y)))
                                    val on = cp.id == selPoint
                                    if (on && (pillOn || dragging)) drawCircle(Color(0x33FFFFFF), cp.size / state.crop.w * side, o)
                                    drawCircle(Color(0xAA000000), 15.dp.toPx(), o)
                                    drawCircle(if (on) Ed.accent else Color.White, 15.dp.toPx(), o, style = DrawStroke(2.5.dp.toPx()))
                                    if (on) drawCircle(Ed.accent, 5.dp.toPx(), o)
                                }
                                if (focusFx != null && (pillOn || dragging || tool == Tool.Blur || tool == Tool.Effects)) {
                                    val faint = Color(0x88FFFFFF)
                                    when (focusFx) {
                                        Focus.Lens -> {
                                            val c = toPx(P(state.f(Params.lensX), state.f(Params.lensY)))
                                            val r = state.f(Params.lensSize) * side
                                            drawCircle(faint, r * 0.5f, c, style = DrawStroke(1.5.dp.toPx())); drawCircle(faint, r * 0.5f + r * 0.9f + 0.08f * side, c, style = DrawStroke(1.dp.toPx()))
                                            drawCircle(Ed.accent, 7.dp.toPx(), c)
                                        }
                                        Focus.Tilt -> {
                                            val y = top + state.f(Params.tiltY) * dh; val hh = state.f(Params.tiltSize) * dh * 0.5f
                                            for (yy in floatArrayOf(y - hh, y + hh)) drawLine(faint, Offset(left, yy), Offset(left + dw, yy), 1.5.dp.toPx())
                                            drawCircle(Ed.accent, 7.dp.toPx(), Offset(left + dw / 2f, y))
                                        }
                                        else -> {
                                            val c = toPx(P(state.f(Params.vigX), state.f(Params.vigY)))
                                            val r = state.f(Params.vigSize) * 0.75f * hypot(dw, dh) / 2f
                                            drawCircle(faint, r, c, style = DrawStroke(1.5.dp.toPx()))
                                            drawCircle(Ed.accent, 7.dp.toPx(), c)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }

            // Overlays that don't zoom with the picture.
            ValuePill(param, param?.let { getV(it) } ?: 0f, pillOn && menuIdx < 0 && swipeTool, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            if (menuIdx >= 0 && params.isNotEmpty()) ParamMenu(params, { getV(it) }, menuIdx, Modifier.align(Alignment.Center))
            if (zoom > 1.01f) Text(
                "Fit • ${(zoom * 100).roundToInt()}%", color = Ed.text, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x99000000))
                    .clickable { zoom = 1f; pan = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (tool != null && (toolUndo.isNotEmpty() || toolRedo.isNotEmpty())) Row(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                SmallRound(Icons.AutoMirrored.Rounded.Undo, toolUndo.isNotEmpty()) { toolRedo.add(state); state = toolUndo.removeAt(toolUndo.lastIndex) }
                Spacer(Modifier.width(8.dp))
                SmallRound(Icons.AutoMirrored.Rounded.Redo, toolRedo.isNotEmpty()) { toolUndo.add(state); state = toolRedo.removeAt(toolRedo.lastIndex) }
            }
            if (tool == null && undo.isEmpty() && rendered != null) Text(
                "Pick a tool below • Hold the picture to see the original", color = Ed.sub, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }

        // ------------------------------------------------------------ bottom
        if (tool == null) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(Tool.entries) { i, t ->
                    if (i > 0 && Tool.entries[i - 1].group != t.group) Spacer(Modifier.width(10.dp))
                    ToolTile(t.icon, t.label.substringBefore(" ("), onClick = { open(t) }, edited = Tools.changed(t, state))
                }
            }
        } else {
            val t = tool!!
            Column(Modifier.fillMaxWidth().background(Ed.panel).padding(top = 10.dp)) {
                ToolPanel(
                    tool = t, state = state, params = params, param = param, paramIdx = paramIdx,
                    onParam = { paramIdx = it; pill() }, getV = { getV(it) },
                    onRulerStart = { dragging = true; pill() },
                    onRulerChange = { p, v -> setV(p, v); pill() },
                    onRulerEnd = { before -> dragging = false; mark(before) },
                    stateNow = { state }, setState = { state = it }, commit = { commit(it) },
                    small = small, fxIdx = fxIdx, onFx = { fxIdx = it; paramIdx = 0; pill() }, blurIdx = blurIdx, onBlur = { blurIdx = it; paramIdx = 0; pill() },
                    band = band, onBand = { band = it; pill() }, curveCh = curveCh, onCurveCh = { curveCh = it },
                    aspect = aspect, onAspect = { a ->
                        aspect = a
                        val src = source ?: return@ToolPanel
                        val (rw, rh) = Renderer.rotatedSize(src.width, src.height, state)
                        commit(state.copy(crop = fitAspect(a?.let { it * rh / rw })))
                    }, onAspectReset = { aspect = null },
                    healSize = healSize, onHealSize = { healSize = it },
                    localKind = localKind, onLocalKind = { localKind = it }, localLevel = localLevel, onLocalLevel = { localLevel = it },
                    localSize = localSize, onLocalSize = { localSize = it }, showMask = showMask, onShowMask = { showMask = it },
                    selPoint = selPoint, onSelPoint = { selPoint = it },
                    pen = pen, onPen = { pen = it }, penColor = penColor, onPenColor = { penColor = it },
                    penSize = penSize, onPenSize = { penSize = it }, penOpacity = penOpacity, onPenOpacity = { penOpacity = it },
                    selText = selText, onAddText = { editingText = TextLayer(System.nanoTime(), "", 0.5f, 0.5f) },
                    onEditText = { editingText = it }, onSelText = { selText = it },
                    selSticker = selSticker, onSelSticker = { selSticker = it },
                    selPhoto = selPhoto, onSelPhoto = { selPhoto = it }, onPickPhoto = { pickPhoto = true },
                )
                // ✕   Tool name (+ quick buttons)   ✓
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cancel() }) { Icon(Icons.Rounded.Close, "Cancel", tint = Ed.text) }
                    Spacer(Modifier.weight(1f))
                    if (swipeTool && params.size > 1) IconButton(onClick = { paramIdx = (paramIdx + 1) % params.size; pill() }) {
                        Icon(Icons.Rounded.SwapVert, "Next adjustment", tint = Ed.text)
                    }
                    Text(t.label, color = Ed.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
                    if (t == Tool.Tune) IconButton(onClick = { source?.let { s -> commit(state.setAll(*Renderer.autoEnhance(s).toTypedArray())); pill() } }) {
                        Icon(Icons.Rounded.AutoAwesome, "Auto", tint = Ed.accent)
                    }
                    if (t == Tool.Balance) IconButton(onClick = { eyedropper = !eyedropper }) {
                        Icon(Icons.Rounded.Colorize, "Pick a grey or white spot", tint = if (eyedropper) Ed.accent else Ed.text)
                    }
                    // Hold to compare with the original.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(); comparing = true
                                do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                                comparing = false
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Compare, "Hold to compare", tint = Ed.text) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { apply() }) { Icon(Icons.Rounded.Check, "Apply", tint = Ed.accent) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs and sheets
    editingText?.let { t ->
        TextDialog(t, onDismiss = { editingText = null }) { text ->
            editingText = null
            if (text.isBlank()) return@TextDialog
            val exists = state.texts.any { it.id == t.id }
            commit(state.copy(texts = if (exists) state.texts.map { if (it.id == t.id) it.copy(text = text) else it } else state.texts + t.copy(text = text)))
            selText = t.id
        }
    }
    if (pickPhoto) PhotoPicker(onDismiss = { pickPhoto = false }) { m ->
        pickPhoto = false
        scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { loadBitmap(ctx, m.uri, 1600) }.getOrNull() } ?: return@launch
            photoBitmaps[m.uri.toString()] = b
            val l = PhotoLayer(System.nanoTime(), m.uri.toString(), b.height.toFloat() / b.width)
            commit(state.copy(photos = state.photos + l)); selPhoto = l.id
        }
    }
    if (showEdits) EditsSheet(state, onDismiss = { showEdits = false }, onOpen = { showEdits = false; open(it) }, onRemove = { commit(Tools.reset(it, state)) },
        onRemoveAll = { commit(EditState()); showEdits = false })
    if (showExport) ExportSheet(onDismiss = { showExport = false }) { format, quality, size, share ->
        showExport = false
        saving = true
        scope.launch {
            val uri = exportEdit(ctx, media, state, format, quality, size)
            saving = false
            if (uri == null) { Toast.makeText(ctx, "Couldn't save — try a smaller size", Toast.LENGTH_LONG).show(); return@launch }
            if (share) {
                val i = Intent(Intent.ACTION_SEND).setType(ctx.contentResolver.getType(uri) ?: "image/*").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else Toast.makeText(ctx, "Saved to Pictures/Edited", Toast.LENGTH_SHORT).show()
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Discard your edits?") },
        text = { Text("Your original photo is never changed. Save to keep a copy with your edits.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; Nav.back() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep editing") } },
    )
}

private fun Modifier.fillMaxHeight38() = this.height(38.dp)

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Color(0xB3000000)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) Ed.text else Ed.faint, modifier = Modifier.size(20.dp)) }
}

/** Renders the edit at full quality and saves it as a new picture. */
private suspend fun exportEdit(
    ctx: android.content.Context, media: Media, state: EditState, format: Bitmap.CompressFormat, quality: Int, maxSide: Int,
): Uri? = withContext(Dispatchers.Default) {
    runCatching {
        val full = loadBitmap(ctx, media.uri, maxSide)
        val layers = state.photos.map { it.uri }.distinct().associateWith { u -> runCatching { loadBitmap(ctx, Uri.parse(u), min(maxSide, 2048)) }.getOrNull() }
        val out = Renderer.render(full, state, { layers[it] })
        full.recycle()
        Actions.saveBitmap(ctx, out, media.name, if (media.id > 0) media.date else null, format, quality)
    }.getOrNull()
}

/** Largest centred crop box with the given shape ([r] = width/height in 0..1 units, null = whole picture). */
internal fun fitAspect(r: Float?): Crop {
    if (r == null) return Crop()
    return if (r >= 1f) { val h = 1f / r; Crop(0f, (1f - h) / 2f, 1f, (1f + h) / 2f) }
    else { val w = r; Crop((1f - w) / 2f, 0f, (1f + w) / 2f, 1f) }
}

// ---------------------------------------------------------------- crop

@Composable
internal fun CropOverlay(
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
                    } else if (grab.isNotEmpty()) {
                        if ('l' in grab) c = c.copy(l = (c.l + dx).coerceIn(0f, c.r - minS))
                        if ('r' in grab) c = c.copy(r = (c.r + dx).coerceIn(c.l + minS, 1f))
                        if ('t' in grab) c = c.copy(t = (c.t + dy).coerceIn(0f, c.b - minS))
                        if ('b' in grab) c = c.copy(b = (c.b + dy).coerceIn(c.t + minS, 1f))
                        if (ratio != null) {
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
        val dim = Color(0xAA000000)
        drawRect(dim, Offset(left, top), androidx.compose.ui.geometry.Size(w, t - top))
        drawRect(dim, Offset(left, b), androidx.compose.ui.geometry.Size(w, top + h - b))
        drawRect(dim, Offset(left, t), androidx.compose.ui.geometry.Size(l - left, b - t))
        drawRect(dim, Offset(r, t), androidx.compose.ui.geometry.Size(left + w - r, b - t))
        drawRect(Color.White, Offset(l, t), androidx.compose.ui.geometry.Size(r - l, b - t), style = DrawStroke(1.5.dp.toPx()))
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f; val y = t + (b - t) * i / 3f
            drawLine(Color(0x66FFFFFF), Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color(0x66FFFFFF), Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
        val hl = 20.dp.toPx(); val sw = 4.dp.toPx()
        for ((cx, cy, sx, sy) in listOf(Quad(l, t, 1f, 1f), Quad(r, t, -1f, 1f), Quad(l, b, 1f, -1f), Quad(r, b, -1f, -1f))) {
            drawLine(Color.White, Offset(cx, cy), Offset(cx + hl * sx, cy), sw)
            drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + hl * sy), sw)
        }
        // Edge handles
        val e = 14.dp.toPx()
        drawLine(Color.White, Offset((l + r) / 2 - e, t), Offset((l + r) / 2 + e, t), sw)
        drawLine(Color.White, Offset((l + r) / 2 - e, b), Offset((l + r) / 2 + e, b), sw)
        drawLine(Color.White, Offset(l, (t + b) / 2 - e), Offset(l, (t + b) / 2 + e), sw)
        drawLine(Color.White, Offset(r, (t + b) / 2 - e), Offset(r, (t + b) / 2 + e), sw)
    }
}

internal data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

// ---------------------------------------------------------------- curves (drawn on the picture, like Snapseed)

@Composable
internal fun CurvesOverlay(
    curves: Curves, channel: Int, left: Float, top: Float, w: Float, h: Float,
    onChange: (Curves) -> Unit, onEnd: (Curves) -> Unit,
) {
    var cur by remember { mutableStateOf(curves) }
    cur = curves
    val colors = listOf(Color.White, Color(0xFFFF5A52), Color(0xFF4CD964), Color(0xFF4DA3FF))
    Canvas(
        Modifier.fillMaxSize().pointerInput(channel, left, top, w, h) {
            val grab = 28.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val start = cur
                val pts = start.channel(channel).sortedBy { it.x }.toMutableList()
                fun toPx(p: P) = Offset(left + p.x * w, top + (1f - p.y) * h)
                fun toP(o: Offset) = P(((o.x - left) / w).coerceIn(0f, 1f), (1f - (o.y - top) / h).coerceIn(0f, 1f))
                var idx = pts.indices.minByOrNull { (toPx(pts[it]) - down.position).getDistance() }?.takeIf { (toPx(pts[it]) - down.position).getDistance() < grab } ?: -1
                if (idx < 0) {
                    val p = toP(down.position)
                    if (p.x <= 0.02f || p.x >= 0.98f) return@awaitEachGesture
                    pts.add(p); pts.sortBy { it.x }; idx = pts.indexOf(p)
                }
                down.consume()
                var removed = false
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.size > 1 || ev.changes.any { it.isConsumed }) { onChange(start); return@awaitEachGesture }
                    val c = ev.changes[0]
                    if (!c.pressed) break
                    val p = toP(c.position)
                    val outside = c.position.y < top - grab * 2 || c.position.y > top + h + grab * 2
                    val isEnd = idx == 0 || idx == pts.lastIndex
                    if (outside && !isEnd) { removed = true } else {
                        removed = false
                        val minX = if (idx > 0) pts[idx - 1].x + 0.02f else 0f
                        val maxX = if (idx < pts.lastIndex) pts[idx + 1].x - 0.02f else 1f
                        pts[idx] = if (isEnd) P(pts[idx].x, p.y) else P(p.x.coerceIn(minX, maxX), p.y)
                    }
                    val list = if (removed) pts.filterIndexed { i, _ -> i != idx } else pts.toList()
                    onChange(start.with(channel, list))
                    c.consume()
                }
                onEnd(start)
            }
        }
    ) {
        drawRect(Color(0x33000000), Offset(left, top), androidx.compose.ui.geometry.Size(w, h))
        for (i in 1..3) {
            drawLine(Color(0x40FFFFFF), Offset(left + w * i / 4f, top), Offset(left + w * i / 4f, top + h), 1.dp.toPx())
            drawLine(Color(0x40FFFFFF), Offset(left, top + h * i / 4f), Offset(left + w, top + h * i / 4f), 1.dp.toPx())
        }
        drawLine(Color(0x30FFFFFF), Offset(left, top + h), Offset(left + w, top), 1.dp.toPx())
        for (ch in listOf(1, 2, 3, 0).filter { it != channel } + channel) {
            val lut = Spline.lut(curves.channel(ch))
            if (ch != channel && curves.channel(ch) == Curves.LINE) continue
            val path = Path()
            for (i in 0..64) {
                val x = i / 64f; val y = lut[(x * 255).roundToInt()] / 255f
                val o = Offset(left + x * w, top + (1f - y) * h)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, colors[ch].copy(alpha = if (ch == channel) 1f else 0.4f), style = DrawStroke(if (ch == channel) 2.5.dp.toPx() else 1.5.dp.toPx()))
        }
        for (p in curves.channel(channel)) {
            val o = Offset(left + p.x * w, top + (1f - p.y) * h)
            drawCircle(Color.Black, 9.dp.toPx(), o)
            drawCircle(colors[channel], 7.dp.toPx(), o)
        }
    }
}
