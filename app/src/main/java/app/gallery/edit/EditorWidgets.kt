package app.gallery.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Editor colours (always dark, like Snapseed / PicsArt, so colours are judged correctly). */
object Ed {
    val bg = Color(0xFF0B0C0F)
    val panel = Color(0xFF121318)
    val tile = Color(0xFF1D1F26)
    val tileOn = Color(0xFF2A3350)
    val accent = Color(0xFF6EA8FF)
    val text = Color(0xF2FFFFFF)
    val sub = Color(0x99FFFFFF)
    val faint = Color(0x33FFFFFF)
}

/** Pill-shaped choice chip. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, dot: Boolean = false, icon: ImageVector? = null) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) Ed.text else Ed.tile)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Ed.bg else Ed.text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (selected) Ed.bg else Ed.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (dot) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Ed.bg else Ed.accent)) }
    }
}

@Composable
fun PillRow(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Round icon button with a label underneath (PicsArt-style tool tile). */
@Composable
fun ToolTile(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false, edited: Boolean = false, rotate: Float = 0f) {
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Ed.tileOn else Ed.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, tint = if (selected) Ed.accent else Ed.text, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotate })
            }
            if (edited) Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(8.dp).clip(CircleShape).background(Ed.accent))
        }
        Text(
            label, color = if (selected) Ed.accent else Ed.sub, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * Pro-style tick ruler: drag left/right to change the value, double-tap to reset.
 * Shows the value above the centre mark and gives a tiny tick when passing the default.
 */
@Composable
fun Ruler(
    p: Param, value: Float, onStart: () -> Unit, onChange: (Float) -> Unit, onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val cur by rememberUpdatedState(value)
    val start by rememberUpdatedState(onStart)
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    var acc by remember { mutableFloatStateOf(0f) }
    val steps = if (p.choices != null) p.choices.size - 1 else if (p.bipolar) 200 else 100
    val unit = (p.max - p.min) / steps
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${p.label}  ${p.show(value)}", color = Ed.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(34.dp)
                .pointerInput(p.key) {
                    detectTapGestures(onDoubleTap = { start(); change(p.def); end() })
                }
                .pointerInput(p.key) {
                    val px = (if (p.choices != null) 36.dp else 5.dp).toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f; start() },
                        onDragEnd = { end() }, onDragCancel = { end() },
                    ) { ch, dx ->
                        ch.consume()
                        acc -= dx
                        val n = (acc / px).toInt()
                        if (n != 0) {
                            acc -= n * px
                            val old = cur
                            var nv = (old + n * unit).coerceIn(p.min, p.max)
                            if (p.choices != null) nv = nv.roundToInt().toFloat()
                            if ((old - p.def) * (nv - p.def) < 0f || (nv == p.def && old != p.def)) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            change(nv)
                        }
                    }
                },
        ) {
            val spacing = (if (p.choices != null) 36.dp else 5.dp).toPx()
            val cx = size.width / 2f
            val pos = (value - p.min) / unit // ticks from the start
            val first = (pos - cx / spacing).toInt() - 1
            val last = (pos + cx / spacing).toInt() + 1
            for (i in first..last) {
                if (i < 0 || i > steps) continue
                val x = cx + (i - pos) * spacing
                val major = p.choices != null || i % 10 == 0
                val isDef = abs(p.min + i * unit - p.def) < unit / 2f
                val hgt = if (isDef) size.height * 0.75f else if (major) size.height * 0.5f else size.height * 0.28f
                val fade = 1f - (abs(x - cx) / cx).coerceIn(0f, 1f) * 0.85f
                drawLine(
                    (if (isDef) Ed.accent else Color.White).copy(alpha = (if (major) 0.8f else 0.4f) * fade),
                    Offset(x, (size.height - hgt) / 2f), Offset(x, (size.height + hgt) / 2f),
                    strokeWidth = if (major || isDef) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}

/** Snapseed-style value bubble at the top of the picture: "Brightness +25" with a thin bar. */
@Composable
fun ValuePill(p: Param?, value: Float, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible && p != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        if (p == null) return@AnimatedVisibility
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC000000)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, color = Ed.text, fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text(p.show(value), color = Ed.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (p.choices == null) {
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.width(150.dp).height(3.dp)) {
                    drawRect(Color(0x40FFFFFF))
                    val t = (value - p.min) / (p.max - p.min)
                    if (p.bipolar) {
                        val c = size.width * (0f - p.min) / (p.max - p.min)
                        val x = size.width * t
                        drawRect(Ed.accent, Offset(minOf(c, x), 0f), androidx.compose.ui.geometry.Size(abs(x - c), size.height))
                    } else drawRect(Ed.accent, Offset.Zero, androidx.compose.ui.geometry.Size(size.width * t, size.height))
                }
            }
        }
    }
}

/** Snapseed's vertical list of adjustments (shown while swiping up/down on the picture). */
@Composable
fun ParamMenu(params: List<Param>, values: (Param) -> Float, highlighted: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(min = 210.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6141519)).padding(vertical = 6.dp),
    ) {
        params.forEachIndexed { i, p ->
            val on = i == highlighted
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(if (on) Ed.accent.copy(alpha = 0.9f) else Color.Transparent).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, color = if (on) Color.Black else Ed.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                val v = values(p)
                if (v != p.def) Text(p.show(v), color = if (on) Color.Black else Ed.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

val Palette = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF8E8E93, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE,
    0xFF32ADE6, 0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D55, 0xFFA2845E, 0xFFFFD6E0, 0xFFD1F2EB,
).map { it.toInt() }

@Composable
fun ColorRow(selected: Int, onColor: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Palette) { c ->
            val on = (c or -0x1000000) == (selected or -0x1000000)
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .border(if (on) 2.5.dp else 0.dp, if (on) Ed.accent else Color.Transparent, CircleShape)
                    .padding(if (on) 4.dp else 0.dp).clip(CircleShape).background(Color(c))
                    .border(1.dp, Color(0x40FFFFFF), CircleShape)
                    .clickable { onColor(c) }
            )
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, color = Ed.sub, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
}
