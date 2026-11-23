package app.gallery.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import app.gallery.data.Thumb
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private val straightenParam = Param("_st", "Straighten", -45f, 45f, 0f, fmt = { "${it.roundToInt()}°" })
private val healSizeParam = Param("_hs", "Brush size", 0.008f, 0.12f, 0.03f)
private val localSizeParam = Param("_bs", "Brush size", 0.015f, 0.2f, 0.06f)
private val textSizeParam = Param("_ts", "Size", 0.015f, 0.4f, 0.07f)
private val opacityParam = Param("_op", "Opacity", 0f, 1f, 1f)
private val spacingParam = Param("_ls", "Letter spacing", -0.1f, 0.6f, 0f, fmt = { "${(it * 100).roundToInt()}" })
private val outlineParam = Param("_ow", "Outline width", 0f, 1f, 0f)

@Composable
internal fun ToolPanel(
    tool: Tool, state: EditState, params: List<Param>, param: Param?, paramIdx: Int,
    onParam: (Int) -> Unit, getV: (Param) -> Float,
    onRulerStart: () -> Unit, onRulerChange: (Param, Float) -> Unit, onRulerEnd: (EditState) -> Unit,
    stateNow: () -> EditState, setState: (EditState) -> Unit, commit: (EditState) -> Unit,
    small: Bitmap?, fxIdx: Int, onFx: (Int) -> Unit, blurIdx: Int, onBlur: (Int) -> Unit,
    band: Int, onBand: (Int) -> Unit, curveCh: Int, onCurveCh: (Int) -> Unit,
    aspect: Float?, onAspect: (Float?) -> Unit, onAspectReset: () -> Unit,
    healSize: Float, onHealSize: (Float) -> Unit,
    localKind: Local, onLocalKind: (Local) -> Unit, localLevel: Float, onLocalLevel: (Float) -> Unit,
    localSize: Float, onLocalSize: (Float) -> Unit, showMask: Boolean, onShowMask: (Boolean) -> Unit,
    selPoint: Long?, onSelPoint: (Long?) -> Unit,
    pen: Brush, onPen: (Brush) -> Unit, penColor: Int, onPenColor: (Int) -> Unit,
    penSize: Float, onPenSize: (Float) -> Unit, penOpacity: Float, onPenOpacity: (Float) -> Unit,
    selText: Long?, onAddText: () -> Unit, onEditText: (TextLayer) -> Unit, onSelText: (Long?) -> Unit,
    selSticker: Long?, onSelSticker: (Long?) -> Unit,
    selPhoto: Long?, onSelPhoto: (Long?) -> Unit, onPickPhoto: () -> Unit,
) {
    /** Ruler for a slider of the current tool (live preview while dragging, one undo step per drag). */
    @Composable
    fun StateRuler(p: Param) {
        var before by remember { mutableStateOf(state) }
        Ruler(p, getV(p), onStart = { before = stateNow(); onRulerStart() }, onChange = { onRulerChange(p, it) }, onEnd = { onRulerEnd(before) })
    }

    /** Ruler for something that isn't saved in the edit (brush size and so on). */
    @Composable
    fun LocalRuler(p: Param, v: Float, set: (Float) -> Unit) = Ruler(p, v, onStart = {}, onChange = set, onEnd = {})

    /** Ruler that edits a layer: [get]/[put] read and write the value on a state. */
    @Composable
    fun LayerRuler(p: Param, v: Float, put: (EditState, Float) -> EditState) {
        var before by remember { mutableStateOf(state) }
        Ruler(p, v, onStart = { before = stateNow() }, onChange = { setState(put(stateNow(), it)) }, onEnd = { onRulerEnd(before) })
    }

    @Composable
    fun ParamPills() = PillRow {
        itemsIndexed(params) { i, p -> Pill(p.label, i == paramIdx, { onParam(i) }, dot = getV(p) != p.def) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (tool) {
            Tool.Tune, Tool.Details, Tool.Balance, Tool.Perspective -> {
                ParamPills()
                param?.let { StateRuler(it) }
                if (tool == Tool.Balance) Hint("Tip: tap the dropper below, then tap something white or grey in the photo.")
                if (tool == Tool.Tune) Hint("Swipe ↔ on the photo to adjust • ↕ to choose")
            }

            Tool.Color -> {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(Tools.hslSwatches) { i, c ->
                        val changed = state.has(Params.hue(i)) || state.has(Params.sat(i)) || state.has(Params.lum(i))
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).border(if (i == band) 2.5.dp else 0.dp, Color.White, CircleShape)
                                    .padding(if (i == band) 4.dp else 0.dp).clip(CircleShape).background(Color(c)).clickable { onBand(i) }
                            )
                            if (changed) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                ParamPills()
                param?.let { StateRuler(it) }
            }

            Tool.Looks -> {
                FilterThumbs(small, state.filter) { name ->
                    commit(stateNow().copy(filter = name, filterStrength = if (name == state.filter) state.filterStrength else 1f))
                }
                if (state.filter != "Original") StateRuler(Tools.lookStrength) else Spacer(Modifier.height(46.dp))
            }

            Tool.Effects, Tool.Blur -> {
                val list = if (tool == Tool.Effects) Tools.effects else Tools.blurs
                val idx = if (tool == Tool.Effects) fxIdx else blurIdx
                FxThumbs(small, list, idx, state) { i ->
                    if (tool == Tool.Effects) onFx(i) else onBlur(i)
                    val f = list[i]
                    if (!stateNow().has(f.main)) commit(stateNow().set(f.main, if (f.main.bipolar) -0.5f else 0.5f))
                }
                if (params.size > 1) ParamPills()
                param?.let { StateRuler(it) }
                if (list[idx].focus != Focus.None) Hint("Drag the blue dot to move the centre • Pinch to change its size")
            }

            Tool.Curves -> {
                PillRow { itemsIndexed(listOf("RGB", "Red", "Green", "Blue")) { i, n -> Pill(n, i == curveCh, { onCurveCh(i) }, dot = state.curves.channel(i) != Curves.LINE) } }
                PillRow {
                    items(Spline.presets) { (name, c) -> Pill(name, state.curves == c && name != "Linear", { commit(stateNow().copy(curves = c)) }) }
                }
                Hint("Drag the curve on the photo. Tap the curve to add a point; drag a point off the top or bottom to remove it.")
            }

            Tool.Crop -> {
                var before by remember { mutableStateOf(state) }
                Ruler(
                    straightenParam, state.straighten,
                    onStart = { before = stateNow() }, onChange = { setState(stateNow().copy(straighten = it)) }, onEnd = { onRulerEnd(before) },
                )
                PillRow {
                    item { ActionTile(Icons.AutoMirrored.Rounded.RotateLeft, "Rotate") { commit(stateNow().let { it.copy(rotation = (it.rotation + 270) % 360, crop = Crop()) }); onAspectReset() } }
                    item { ActionTile(Icons.Rounded.Flip, "Flip") { commit(stateNow().let { it.copy(flipH = !it.flipH) }) } }
                    item { ActionTile(Icons.Rounded.Flip, "Flip ↕", rotate = 90f) { commit(stateNow().let { it.copy(flipV = !it.flipV) }) } }
                    val ratios = listOf<Pair<String, Float?>>("Free" to null, "1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "2:3" to 2f / 3f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "3:2" to 1.5f, "4:3" to 4f / 3f)
                    items(ratios) { (label, r) -> Pill(label, aspect == r, { onAspect(r) }) }
                    item { Pill("Reset", false, { commit(Tools.reset(Tool.Crop, stateNow())); onAspectReset() }) }
                }
            }

            Tool.Healing -> {
                Hint("Paint over spots, wires or blemishes — they're replaced with matching detail from nearby. Zoom in with two fingers for small spots.")
                LocalRuler(healSizeParam, healSize, onHealSize)
            }

            Tool.Brush -> {
                PillRow { items(Local.entries) { k -> Pill(k.label, k == localKind, { onLocalKind(k) }, dot = state.masks.any { it.kind == k }) } }
                PillRow {
                    val levels = listOf("−10" to -1f, "−5" to -0.5f, "Eraser" to 0f, "+5" to 0.5f, "+10" to 1f)
                    items(levels) { (n, v) -> Pill(n, v == localLevel, { onLocalLevel(v) }) }
                    item { Pill("Mask", showMask, { onShowMask(!showMask) }, icon = Icons.Rounded.Visibility) }
                }
                LocalRuler(localSizeParam, localSize, onLocalSize)
            }

            Tool.Selective -> {
                val cp = state.points.firstOrNull { it.id == selPoint }
                if (cp == null) {
                    Hint("Tap the photo to place a point. It changes the area around it with similar colours — e.g. brighten a face or deepen a sky.")
                    Spacer(Modifier.height(20.dp))
                } else {
                    PillRow {
                        itemsIndexed(params) { i, p -> Pill(p.label, i == paramIdx, { onParam(i) }, dot = getV(p) != p.def) }
                        item { Pill("Copy", false, {
                            val c = cp.copy(id = System.nanoTime(), x = (cp.x + 0.06f).coerceAtMost(1f))
                            commit(stateNow().copy(points = stateNow().points + c)); onSelPoint(c.id)
                        }, icon = Icons.Rounded.ContentCopy) }
                        item { Pill("Delete", false, { commit(stateNow().copy(points = stateNow().points.filter { it.id != cp.id })); onSelPoint(null) }, icon = Icons.Rounded.Delete) }
                    }
                    param?.let { StateRuler(it) }
                }
            }

            Tool.Text -> TextPanel(state, selText, onAddText, onEditText, onSelText, stateNow, setState, commit, onRulerEnd)

            Tool.Stickers -> {
                val st = state.stickers.firstOrNull { it.id == selSticker }
                if (st != null) {
                    PillRow {
                        item { Pill("Flip", st.flip, { commit(stateNow().copy(stickers = stateNow().stickers.map { if (it.id == st.id) it.copy(flip = !it.flip) else it })) }, icon = Icons.Rounded.Flip) }
                        item { Pill("Duplicate", false, {
                            val c = st.copy(id = System.nanoTime(), x = (st.x + 0.05f).coerceAtMost(1f), y = (st.y + 0.05f).coerceAtMost(1f))
                            commit(stateNow().copy(stickers = stateNow().stickers + c)); onSelSticker(c.id)
                        }, icon = Icons.Rounded.ContentCopy) }
                        item { Pill("Delete", false, { commit(stateNow().copy(stickers = stateNow().stickers.filter { it.id != st.id })); onSelSticker(null) }, icon = Icons.Rounded.Delete) }
                    }
                    LayerRuler(opacityParam, st.opacity) { s, v -> s.copy(stickers = s.stickers.map { if (it.id == st.id) it.copy(opacity = v) else it }) }
                }
                var cat by remember { mutableIntStateOf(0) }
                PillRow { itemsIndexed(Tools.emojiSets) { i, (n, _) -> Pill(n, i == cat, { cat = i }) } }
                LazyVerticalGrid(GridCells.Adaptive(46.dp), contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(if (st != null) 96.dp else 150.dp)) {
                    items(Tools.emojiSets[cat].second) { e ->
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).clickable {
                            val s = Sticker(System.nanoTime(), e, 0.5f, 0.5f)
                            commit(stateNow().copy(stickers = stateNow().stickers + s)); onSelSticker(s.id)
                        }, contentAlignment = Alignment.Center) { Text(e, fontSize = 28.sp) }
                    }
                }
            }

            Tool.Draw -> {
                PillRow {
                    items(Brush.entries) { b -> Pill(b.label, b == pen, { onPen(b) }) }
                    item { Pill("Clear all", false, { commit(stateNow().copy(strokes = emptyList())) }, icon = Icons.Rounded.Delete) }
                }
                ColorRow(penColor, onPenColor)
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { LocalRuler(Param("_pw", "Size", 0.002f, 0.06f, 0.012f), penSize, onPenSize) }
                    Box(Modifier.weight(1f)) { LocalRuler(opacityParam, penOpacity, onPenOpacity) }
                }
            }

            Tool.Photo -> {
                val l = state.photos.firstOrNull { it.id == selPhoto }
                if (l == null) {
                    Button(onClick = onPickPhoto, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a photo")
                    }
                    Hint("Place another photo on top — for collages, double exposure and blends. Drag, pinch, or use the corner handle.")
                } else {
                    fun upd(f: (PhotoLayer) -> PhotoLayer) = commit(stateNow().copy(photos = stateNow().photos.map { if (it.id == l.id) f(it) else it }))
                    PillRow { items(Blend.entries) { b -> Pill(b.label, b == l.blend, { upd { it.copy(blend = b) } }) } }
                    LayerRuler(opacityParam, l.opacity) { s, v -> s.copy(photos = s.photos.map { if (it.id == l.id) it.copy(opacity = v) else it }) }
                    PillRow {
                        item { Pill("Double exposure", false, { upd { it.copy(x = 0.5f, y = 0.5f, rotation = 0f, scale = max(1f, 1f / it.aspect), blend = Blend.Screen, opacity = 0.6f) } }) }
                        item { Pill("Fill", false, { upd { it.copy(x = 0.5f, y = 0.5f, rotation = 0f, scale = 1f) } }) }
                        item { Pill("Flip", l.flip, { upd { it.copy(flip = !it.flip) } }, icon = Icons.Rounded.Flip) }
                        item { Pill("Add another", false, onPickPhoto, icon = Icons.Rounded.Add) }
                        item { Pill("Delete", false, { commit(stateNow().copy(photos = stateNow().photos.filter { it.id != l.id })); onSelPhoto(null) }, icon = Icons.Rounded.Delete) }
                    }
                }
            }

            Tool.Frame -> {
                PillRow { items(Frame.entries) { f -> Pill(f.label, f == state.frame, { commit(stateNow().copy(frame = f)) }) } }
                if (state.frame != Frame.None) {
                    ColorRow(state.frameColor) { c -> commit(stateNow().copy(frameColor = c)) }
                    StateRuler(Params.frameSize)
                }
                var wm by remember { mutableStateOf(state.watermark) }
                OutlinedTextField(
                    value = wm, onValueChange = { wm = it; setState(stateNow().copy(watermark = it)) }, singleLine = true,
                    placeholder = { Text("Watermark text (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, rotate: Float = 0f, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = Ed.text, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotate })
        Text(label, color = Ed.sub, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- thumbnails

@Composable
private fun ThumbTile(bmp: Bitmap?, name: String, on: Boolean, dot: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Ed.tile)
                .border(if (on) 2.5.dp else 0.dp, if (on) Ed.accent else Color.Transparent, RoundedCornerShape(14.dp)),
        ) {
            if (bmp != null) Image(bmp.asImageBitmap(), name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
            if (dot) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(8.dp).clip(CircleShape).background(Ed.accent))
        }
        Text(name, fontSize = 11.sp, maxLines = 1, color = if (on) Ed.accent else Ed.sub, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FilterThumbs(small: Bitmap?, current: String, onPick: (String) -> Unit) {
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(small) {
        val src = small ?: return@LaunchedEffect
        thumbs = withContext(Dispatchers.Default) { Filters.all.associate { (name, _) -> name to Renderer.render(src, EditState(filter = name)) } }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Filters.all.map { it.first }) { name -> ThumbTile(thumbs[name], name, name == current, false) { onPick(name) } }
    }
}

@Composable
private fun FxThumbs(small: Bitmap?, list: List<Fx>, idx: Int, state: EditState, onPick: (Int) -> Unit) {
    var thumbs by remember(list) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(small, list) {
        val src = small ?: return@LaunchedEffect
        thumbs = withContext(Dispatchers.Default) { list.associate { f -> f.name to Renderer.render(src, EditState().set(f.main, f.preview)) } }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(list) { i, f -> ThumbTile(thumbs[f.name], f.name, i == idx, state.has(f.main)) { onPick(i) } }
    }
}

// ---------------------------------------------------------------- text

@Composable
private fun TextPanel(
    state: EditState, selText: Long?, onAdd: () -> Unit, onEdit: (TextLayer) -> Unit, onSel: (Long?) -> Unit,
    stateNow: () -> EditState, setState: (EditState) -> Unit, commit: (EditState) -> Unit, onRulerEnd: (EditState) -> Unit,
) {
    val t = state.texts.firstOrNull { it.id == selText }
    if (t == null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add text") }
            Hint("Tap a text to pick it. Drag to move, pinch or use the corner handle to resize and turn. Double-tap to edit.")
        }
        return
    }
    fun upd(f: (TextLayer) -> TextLayer) = commit(stateNow().copy(texts = stateNow().texts.map { if (it.id == t.id) f(it) else it }))
    @Composable
    fun TextRuler(p: Param, v: Float, put: (TextLayer, Float) -> TextLayer) {
        var before by remember { mutableStateOf(state) }
        Ruler(p, v, onStart = { before = stateNow() }, onChange = { x -> setState(stateNow().copy(texts = stateNow().texts.map { if (it.id == t.id) put(it, x) else it })) }, onEnd = { onRulerEnd(before) })
    }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Font", "Colour", "Outline", "Background", "Style", "Size")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillRow {
            item { Pill("Edit", false, { onEdit(t) }, icon = Icons.Rounded.Edit) }
            itemsIndexed(tabs) { i, n -> Pill(n, i == tab, { tab = i }) }
            item { Pill("Duplicate", false, {
                val c = t.copy(id = System.nanoTime(), y = (t.y + 0.08f).coerceAtMost(1f))
                commit(stateNow().copy(texts = stateNow().texts + c)); onSel(c.id)
            }, icon = Icons.Rounded.ContentCopy) }
            item { Pill("Delete", false, { commit(stateNow().copy(texts = stateNow().texts.filter { it.id != t.id })); onSel(null) }, icon = Icons.Rounded.Delete) }
            item { Pill("New", false, onAdd, icon = Icons.Rounded.Add) }
        }
        when (tab) {
            0 -> LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Font.entries) { f ->
                    val on = f == t.font
                    Box(
                        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Ed.text else Ed.tile).clickable { upd { it.copy(font = f) } }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Aa ${f.label}", color = if (on) Ed.bg else Ed.text, fontSize = 15.sp,
                            fontFamily = FontFamily(androidx.compose.ui.text.font.Typeface(Renderer.typeface(f))))
                    }
                }
            }
            1 -> ColorRow(t.color) { c -> upd { it.copy(color = c) } }
            2 -> {
                ColorRow(t.outlineColor) { c -> upd { it.copy(outlineColor = c, outline = if (it.outline == 0f) 0.4f else it.outline) } }
                TextRuler(outlineParam, t.outline) { l, v -> l.copy(outline = v) }
            }
            3 -> {
                PillRow { item { Pill(if (t.background) "Background on" else "Background off", t.background, { upd { it.copy(background = !it.background) } }) } }
                ColorRow(t.bgColor) { c -> upd { it.copy(bgColor = (c and 0x00FFFFFF) or (0xD9 shl 24), background = true) } }
            }
            4 -> {
                PillRow {
                    itemsIndexed(listOf("Centre", "Left", "Right")) { i, n -> Pill(n, t.align == i, { upd { it.copy(align = i) } }) }
                    item { Pill("Shadow", t.shadow, { upd { it.copy(shadow = !it.shadow) } }) }
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { TextRuler(opacityParam, t.opacity) { l, v -> l.copy(opacity = v) } }
                    Box(Modifier.weight(1f)) { TextRuler(spacingParam, t.spacing) { l, v -> l.copy(spacing = v) } }
                }
            }
            else -> TextRuler(textSizeParam, t.size) { l, v -> l.copy(size = v) }
        }
    }
}

@Composable
internal fun TextDialog(t: TextLayer, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(t.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.text.isEmpty()) "Add text" else "Edit text") },
        text = { OutlinedTextField(text, { text = it }, minLines = 1, maxLines = 5, modifier = Modifier.focusRequester(focus)) },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- add photo

@Composable
internal fun PhotoPicker(onDismiss: () -> Unit, onPick: (Media) -> Unit) {
    val all by MediaRepo.all.collectAsState()
    val photos = remember(all) { all.filter { !it.isVideo }.take(600) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ed.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Ed.text) }
                Text("Choose a photo", color = Ed.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(2.dp)) {
                items(photos, key = { it.id }) { m ->
                    AsyncImage(
                        Thumb(m.uri, 256), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(1.dp).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable { onPick(m) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- edits list (Snapseed "View edits")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditsSheet(state: EditState, onDismiss: () -> Unit, onOpen: (Tool) -> Unit, onRemove: (Tool) -> Unit, onRemoveAll: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ed.panel) {
        val list = Tool.entries.filter { Tools.changed(it, state) }
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Edits", color = Ed.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (list.isNotEmpty()) TextButton(onClick = onRemoveAll) { Text("Remove all") }
            }
            if (list.isEmpty()) Hint("No edits yet. Your original is never changed.")
            for (t in list) Row(
                Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ed.tile), contentAlignment = Alignment.Center) { Icon(t.icon, null, tint = Ed.text) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(t.label, color = Ed.text, fontSize = 15.sp)
                    Text(Tools.summary(t, state), color = Ed.sub, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { onRemove(t) }) { Icon(Icons.Rounded.Delete, "Remove", tint = Ed.sub) }
            }
        }
    }
}

// ---------------------------------------------------------------- export

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportSheet(onDismiss: () -> Unit, onGo: (Bitmap.CompressFormat, Int, Int, Boolean) -> Unit) {
    var format by remember { mutableIntStateOf(0) }
    var size by remember { mutableIntStateOf(0) }
    var quality by remember { mutableStateOf(0.95f) }
    val formats = listOf("JPEG" to Bitmap.CompressFormat.JPEG, "PNG" to Bitmap.CompressFormat.PNG, "WebP" to Bitmap.CompressFormat.WEBP_LOSSY)
    val sizes = listOf("Best (4096 px)" to 4096, "Large (2048 px)" to 2048, "Small (1080 px)" to 1080)
    val qParam = Param("_q", "Quality", 0.5f, 1f, 0.95f, fmt = { "${(it * 100).roundToInt()}%" })
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ed.panel) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Export", color = Ed.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Format", color = Ed.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            PillRow { itemsIndexed(formats) { i, (n, _) -> Pill(n, i == format, { format = i }) } }
            Text("Size", color = Ed.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
            PillRow { itemsIndexed(sizes) { i, (n, _) -> Pill(n, i == size, { size = i }) } }
            if (format != 1) Ruler(qParam, quality, onStart = {}, onChange = { quality = it }, onEnd = {})
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onGo(formats[format].second, (quality * 100).roundToInt(), sizes[size].second, true) }, modifier = Modifier.weight(1f)) { Text("Share") }
                Button(
                    onClick = { onGo(formats[format].second, (quality * 100).roundToInt(), sizes[size].second, false) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Ed.accent, contentColor = Color.Black),
                ) { Text("Save copy") }
            }
            Text("Saved to Pictures/Edited. Your original stays as it is.", color = Ed.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}
