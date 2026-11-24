package app.gallery.ui

import android.app.PendingIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.UnfoldMore
import kotlin.math.roundToInt
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gallery.data.Actions
import app.gallery.data.Album
import app.gallery.data.Favorites
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import app.gallery.data.Settings
import app.gallery.data.Thumb
import app.gallery.data.Vault
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------- selection

class Selection {
    val map = mutableStateMapOf<Long, Media>()
    val active get() = map.isNotEmpty()
    val items get() = map.values.toList()
    fun toggle(m: Media) { if (m.id in map) map.remove(m.id) else map[m.id] = m }
    fun clear() = map.clear()
    fun setAll(list: List<Media>, on: Boolean) { if (on) list.forEach { map[it.id] = it } else list.forEach { map.remove(it.id) } }
}

// ---------------------------------------------------------------- dates

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.isAfter(today.minusDays(7)) -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

fun monthLabel(d: LocalDate): String =
    if (d.year == LocalDate.now().year) d.format(DateTimeFormatter.ofPattern("MMMM")) else d.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

fun durationText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- the grid

private sealed interface GridRow {
    data class Header(val label: String, val items: List<Media>) : GridRow
    data class Cell(val m: Media, val index: Int) : GridRow
}

/**
 * Photo grid: grouped by day (or by month when zoomed out), pinch to change the size,
 * long-press and drag to select many, tap to open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    list: List<Media>,
    selection: Selection,
    grouped: Boolean = true,
    state: LazyGridState = rememberLazyGridState(),
    bottomPadding: Int = 120,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    header: LazyGridScope.() -> Unit = {},
) {
    val columns by Settings.columns.collectAsState()
    val byMonth = columns >= 7
    val rows = remember(list, grouped, byMonth) {
        if (!grouped) list.mapIndexed { i, m -> GridRow.Cell(m, i) }
        else buildList<GridRow> {
            var i = 0
            val groups = if (byMonth) list.groupBy { it.day.withDayOfMonth(1) } else list.groupBy { it.day }
            for ((d, items) in groups) {
                add(GridRow.Header(if (byMonth) monthLabel(d) else dayLabel(d), items))
                items.forEach { add(GridRow.Cell(it, i++)) }
            }
        }
    }
    val favs by Favorites.ids.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag-to-select state.
    var dragStart by remember { mutableStateOf(-1) }
    var dragDeselect by remember { mutableStateOf(false) }
    var dragBase by remember { mutableStateOf<Map<Long, Media>>(emptyMap()) }
    var autoScroll by remember { mutableStateOf(0f) }
    LaunchedEffect(autoScroll) {
        while (autoScroll != 0f && isActive) {
            state.scrollBy(autoScroll)
            delay(16)
        }
    }

    fun indexAt(p: Offset): Int? {
        val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= it.offset.y && p.y < it.offset.y + it.size.height
        } ?: return null
        return (rows.getOrNull(hit.index) as? GridRow.Cell)?.index
    }

    fun selectRange(to: Int) {
        if (dragStart < 0) return
        val range = if (to >= dragStart) dragStart..to else to..dragStart
        selection.map.clear()
        selection.map.putAll(dragBase)
        for (i in range) {
            val m = list.getOrNull(i) ?: continue
            if (dragDeselect) selection.map.remove(m.id) else selection.map[m.id] = m
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Pinch with two fingers to zoom the grid in and out, like OnePlus / Google Photos.
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoom = 1f
                    var pinching = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val down = ev.changes.count { it.pressed }
                        if (down == 0) break
                        if (down >= 2) {
                            pinching = true
                            zoom *= ev.calculateZoom()
                            if (zoom > 1.22f) { stepColumns(-1); zoom = 1f }
                            else if (zoom < 0.82f) { stepColumns(+1); zoom = 1f }
                        }
                        if (pinching) ev.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding.dp),
        horizontalArrangement = Arrangement.spacedBy(if (columns >= 7) 1.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(if (columns >= 7) 1.dp else 2.dp),
        modifier = Modifier
            .fillMaxSize()
            // Long-press, then drag across pictures to select them all.
            .pointerInput(rows) {
                val edge = with(density) { 90.dp.toPx() }
                detectDragGesturesAfterLongPress(
                    onDragStart = { p ->
                        val i = indexAt(p) ?: return@detectDragGesturesAfterLongPress
                        val m = list[i]
                        dragDeselect = m.id in selection.map
                        dragBase = selection.map.toMap()
                        dragStart = i
                        selectRange(i)
                    },
                    onDrag = { change, _ ->
                        indexAt(change.position)?.let { selectRange(it) }
                        val y = change.position.y
                        val h = size.height
                        autoScroll = when {
                            y < edge -> -((edge - y) / edge) * 28f
                            y > h - edge -> ((y - (h - edge)) / edge) * 28f
                            else -> 0f
                        }
                    },
                    onDragEnd = { dragStart = -1; autoScroll = 0f },
                    onDragCancel = { dragStart = -1; autoScroll = 0f },
                )
            },
    ) {
        header()
        items(
            rows,
            key = { r -> if (r is GridRow.Header) "h:" + r.label + r.items.first().id else (r as GridRow.Cell).m.id },
            span = { r -> if (r is GridRow.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { r -> if (r is GridRow.Header) 0 else 1 },
        ) { r ->
            when (r) {
                is GridRow.Header -> {
                    val all = r.items.all { it.id in selection.map }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = if (byMonth) 12.dp else 22.dp, bottom = if (byMonth) 6.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            r.label,
                            style = if (byMonth) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (selection.active) {
                            Icon(
                                if (all) Icons.Rounded.CheckCircle else Icons.Rounded.LibraryAddCheck, "Select day",
                                tint = if (all) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clip(CircleShape).clickable { selection.setAll(r.items, !all) }.padding(6.dp),
                            )
                        }
                    }
                }
                is GridRow.Cell -> MediaCell(
                    r.m, selected = r.m.id in selection.map, selecting = selection.active, fav = r.m.id in favs,
                    thumbPx = when { columns <= 2 -> 640; columns <= 3 -> 384; columns <= 5 -> 320; columns <= 7 -> 192; else -> 128 },
                    compact = columns >= 7,
                    modifier = Modifier.animateItem(),
                    onClick = { if (selection.active) selection.toggle(r.m) else Nav.open(list, r.index) },
                    onLongClick = { selection.toggle(r.m) },
                )
            }
        }
    }
    FastScroller(state, rows.size) { idx ->
        (rows.getOrNull(idx) as? GridRow.Cell)?.m?.day?.let { if (byMonth) monthLabel(it) else dayLabel(it) }
            ?: (rows.getOrNull(idx) as? GridRow.Header)?.label
    }
    }
}

/** Zoom steps for the grid: big photos … tiny month overview. */
private val levels = listOf(2, 3, 4, 5, 7, 10, 15)

fun stepColumns(dir: Int) {
    val now = Settings.columns.value
    val i = levels.indexOfFirst { it >= now }.let { if (it < 0) levels.lastIndex else it }
    Settings.setColumns(levels[(i + dir).coerceIn(0, levels.lastIndex)])
}

/** The draggable handle on the right edge for jumping through years of photos quickly. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.FastScroller(state: LazyGridState, total: Int, labelAt: (Int) -> String?) {
    if (total < 60) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var trackH by remember { mutableStateOf(1f) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true else { delay(1500); visible = false }
    }
    val progress = if (dragging) null else state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)
    var dragFrac by remember { mutableStateOf(0f) }
    val frac = progress ?: dragFrac
    val density = LocalDensity.current
    val thumbH = with(density) { 56.dp.toPx() }
    AnimatedVisibility(
        visible, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd).fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize().padding(top = 120.dp, bottom = 130.dp)
                .pointerInput(Unit) { trackH = size.height.toFloat() }
        ) {
            val y = ((trackH - thumbH) * frac.coerceIn(0f, 1f))
            if (dragging) labelAt((frac * (total - 1)).roundToInt())?.let { label ->
                Text(
                    label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .offset { androidx.compose.ui.unit.IntOffset(-with(density) { 64.dp.roundToPx() }, y.roundToInt()) }
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset { androidx.compose.ui.unit.IntOffset(0, y.roundToInt()) }
                    .padding(end = 6.dp)
                    .size(40.dp, 56.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerInput(total) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true; dragFrac = state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1) },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onVerticalDrag = { ch, d ->
                                ch.consume()
                                dragFrac = (dragFrac + d / (trackH - thumbH)).coerceIn(0f, 1f)
                                scope.launch { state.scrollToItem((dragFrac * (total - 1)).roundToInt()) }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.UnfoldMore, "Scroll", tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCell(
    m: Media,
    selected: Boolean,
    selecting: Boolean,
    fav: Boolean,
    thumbPx: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, spring(stiffness = 700f), label = "inset")
    val corner by animateDpAsState(if (selected) 18.dp else 2.dp, label = "corner")
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = Thumb(m.uri, thumbPx),
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        if (m.isVideo && !compact) Row(
            Modifier.align(Alignment.BottomEnd).padding(inset + 5.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0x8C000000)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Text(durationText(m.durationMs), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        if (m.isGif) Icon(
            Icons.Rounded.Gif, null, tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(inset + 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000)),
        )
        if (fav) Icon(
            Icons.Rounded.Favorite, null, tint = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(inset + 6.dp).size(15.dp),
        )
        if (selecting) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- system confirmation pop-ups

/** Runs Android's own "Allow Gallery to delete / change these?" dialog, then [onDone]. */
class SystemAsk(private val launch: (PendingIntent, (Boolean) -> Unit) -> Unit) {
    operator fun invoke(pi: PendingIntent, onDone: (Boolean) -> Unit = {}) = launch(pi, onDone)
}

@Composable
fun rememberSystemAsk(): SystemAsk {
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        pending?.invoke(r.resultCode == android.app.Activity.RESULT_OK)
        pending = null
    }
    return remember {
        SystemAsk { pi, done ->
            pending = done
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    }
}

// ---------------------------------------------------------------- selection bar and actions

@Composable
fun SelectionTopBar(selection: Selection, all: List<Media>) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().height(60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { selection.clear() }) { Icon(Icons.Rounded.Close, "Cancel") }
        Text("${selection.map.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { selection.setAll(all, selection.map.size < all.size) }) {
            Text(if (selection.map.size < all.size) "Select all" else "Clear")
        }
    }
}

/** Floating bar with what you can do with the selected photos. */
@Composable
fun SelectionActions(selection: Selection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberSystemAsk()
    var albumPicker by remember { mutableStateOf<Boolean?>(null) } // true = move, false = copy
    var confirmHide by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val favs by Favorites.ids.collectAsState()
    val items = selection.items
    val allFav = items.isNotEmpty() && items.all { it.id in favs }

    FloatingBar(modifier) {
        BarButton(Icons.Rounded.Share, "Share") { Actions.share(context, items) }
        BarButton(if (allFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite") { Favorites.toggle(items.map { it.id }) }
        BarButton(Icons.Rounded.Lock, "Hide") { confirmHide = true }
        BarButton(Icons.Rounded.Delete, "Delete") {
            ask(Actions.trashRequest(context, items)) { ok -> if (ok) { selection.clear(); MediaRepo.refreshSoon() } }
        }
        Box {
            BarButton(Icons.Rounded.MoreVert, "More") { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(text = { Text("Copy to album") }, leadingIcon = { Icon(Icons.Rounded.Folder, null) }, onClick = { more = false; albumPicker = false })
                DropdownMenuItem(text = { Text("Move to album") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) }, onClick = { more = false; albumPicker = true })
            }
        }
    }

    albumPicker?.let { move ->
        AlbumPickerDialog(if (move) "Move to" else "Copy to", onDismiss = { albumPicker = null }) { path ->
            albumPicker = null
            if (move) {
                ask(Actions.writeRequest(context, items)) { ok ->
                    if (ok) scope.launch {
                        val n = withContext(Dispatchers.IO) { Actions.move(context, items, path) }
                        Toast.makeText(context, "Moved $n", Toast.LENGTH_SHORT).show()
                        selection.clear()
                    }
                }
            } else scope.launch {
                val n = withContext(Dispatchers.IO) { Actions.copy(context, items, path) }
                Toast.makeText(context, "Copied $n", Toast.LENGTH_SHORT).show()
                selection.clear()
            }
        }
    }

    if (confirmHide) HideDialog(items.size, onDismiss = { confirmHide = false }) {
        confirmHide = false
        hideInVault(context, scope, ask, items) { selection.clear() }
    }
}

/** Encrypts the items into the Vault, then asks Android to remove the originals. */
fun hideInVault(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    ask: SystemAsk,
    items: List<Media>,
    after: () -> Unit,
) {
    scope.launch {
        Toast.makeText(context, "Encrypting ${items.size}…", Toast.LENGTH_SHORT).show()
        val done = withContext(Dispatchers.IO) { runCatching { Vault.hide(items) }.getOrDefault(emptyList()) }
        if (done.isEmpty()) {
            Toast.makeText(context, "Couldn't hide these", Toast.LENGTH_SHORT).show()
            return@launch
        }
        ask(Actions.deleteRequest(context, done)) { ok ->
            Toast.makeText(
                context,
                if (ok) "Moved ${done.size} to the Vault" else "Copied to the Vault — the originals are still in your gallery",
                Toast.LENGTH_LONG,
            ).show()
            MediaRepo.refreshSoon()
            after()
        }
    }
}

@Composable
fun HideDialog(count: Int, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Move $count to the Vault?") },
        text = {
            Text(
                "They'll be encrypted and only visible in the Vault after you unlock it. " +
                    "Android will then ask you to allow removing the originals from your gallery."
            )
        },
        confirmButton = { TextButton(onClick = onOk) { Text("Move to Vault") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AlbumPickerDialog(title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val all by MediaRepo.all.collectAsState()
    val albums = remember(all) { MediaRepo.albums(all) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("New album") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (newName.isNotBlank()) IconButton(onClick = { onPick("Pictures/" + newName.trim().replace("/", "_") + "/") }) {
                            Icon(Icons.Rounded.Add, "Create")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    items(albums, key = { it.id }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(a.path) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(Thumb(a.cover.uri, 160), null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${a.count}", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- floating bars

@Composable
fun FloatingBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 14.dp).shadow(14.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, modifier = Modifier.size(22.dp))
        Text(label, style = Caption, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun NavBar(tab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    FloatingBar(modifier) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            val (icon, label) = when (t) {
                Tab.Photos -> Icons.Rounded.PhotoLibrary to "Photos"
                Tab.Albums -> Icons.Rounded.Folder to "Albums"
                Tab.Favorites -> Icons.Rounded.Favorite to "Favourites"
                Tab.Vault -> Icons.Rounded.Lock to "Vault"
            }
            val bg by androidx.compose.animation.animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "tab",
            )
            Row(
                Modifier.clip(RoundedCornerShape(22.dp)).background(bg).clickable { onTab(t) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon, label, modifier = Modifier.size(22.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(selected, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Big screen title with optional actions, scrolls with the grid. */
fun LazyGridScope.bigTitle(title: String, subtitle: String?, actions: @Composable () -> Unit = {}) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            actions()
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun albumsOf(list: List<Media>): List<Album> = MediaRepo.albums(list)

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
