package app.gallery.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.gallery.data.Actions
import app.gallery.data.Favorites
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Hides the status and navigation bars while [hidden] (immersive photo viewing). */
@Composable
fun Immersive(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(list: List<Media>, start: Int) {
    val live by MediaRepo.all.collectAsState()
    // Keep the list in step with deletions while viewing.
    val items = remember(live, list) { val ids = live.map { it.id }.toHashSet(); list.filter { it.id < 0 || it.id in ids } }
    if (items.isEmpty()) { LaunchedEffect(Unit) { Nav.back() }; return }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf<Boolean?>(null) }
    var hide by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    val favs by Favorites.ids.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberSystemAsk()
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val external = current.id < 0
    Immersive(!chrome)
    BackHandler(enabled = slideshow) { slideshow = false }

    LaunchedEffect(slideshow) {
        if (slideshow) chrome = false
        while (slideshow) {
            delay(3500)
            if (pager.currentPage < items.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) else slideshow = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, beyondViewportPageCount = 1, key = { items[it].id }, pageSpacing = 12.dp) { page ->
            val m = items[page]
            if (m.isVideo) VideoPage(m, active = page == pager.settledPage, onTap = { chrome = !chrome })
            else ZoomableImage(m, onTap = { chrome = !chrome; slideshow = false })
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    val dt = Instant.ofEpochMilli(current.date).atZone(ZoneId.systemDefault())
                    Text(dayLabel(dt.toLocalDate()), color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(dt.format(DateTimeFormatter.ofPattern("h:mm a")), color = Color(0xCCFFFFFF), style = Caption)
                }
                if (!external) IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "Details", tint = Color.White) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Slideshow") }, onClick = { menu = false; slideshow = true })
                        DropdownMenuItem(text = { Text("Set as wallpaper / contact") }, onClick = { menu = false; Actions.setAs(context, current) })
                        DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; Actions.openWith(context, current) })
                        if (!external) {
                            DropdownMenuItem(text = { Text("Move to Vault") }, onClick = { menu = false; hide = true })
                            DropdownMenuItem(text = { Text("Copy to album") }, onClick = { menu = false; album = false })
                            DropdownMenuItem(text = { Text("Move to album") }, onClick = { menu = false; album = true })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                        }
                    }
                }
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.Rounded.Share, "Share") { Actions.share(context, listOf(current)) }
                if (!current.isVideo) ViewerAction(Icons.Rounded.Edit, "Edit") { Nav.go(Route.Editor(current)) }
                if (!external) {
                    val fav = current.id in favs
                    ViewerAction(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (fav) Color(0xFFFF5A72) else Color.White) {
                        Favorites.toggle(listOf(current.id))
                    }
                    ViewerAction(Icons.Rounded.Delete, "Delete") {
                        ask(Actions.trashRequest(context, listOf(current))) { if (it) MediaRepo.refreshSoon() }
                    }
                }
            }
        }
    }

    if (info) ModalBottomSheet(onDismissRequest = { info = false }) { InfoSheet(current) }
    if (rename) RenameDialog(current, onDismiss = { rename = false }) { name ->
        rename = false
        ask(Actions.writeRequest(context, listOf(current))) { ok ->
            if (ok) scope.launch { withContext(Dispatchers.IO) { Actions.rename(context, current, name) } }
        }
    }
    album?.let { move ->
        AlbumPickerDialog(if (move) "Move to" else "Copy to", onDismiss = { album = null }) { path ->
            album = null
            if (move) ask(Actions.writeRequest(context, listOf(current))) { ok ->
                if (ok) scope.launch { withContext(Dispatchers.IO) { Actions.move(context, listOf(current), path) } }
            } else scope.launch {
                withContext(Dispatchers.IO) { Actions.copy(context, listOf(current), path) }
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (hide) HideDialog(1, onDismiss = { hide = false }) {
        hide = false
        hideInVault(context, scope, ask, listOf(current)) {}
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
        Text(label, color = Color.White, style = Caption)
    }
}

/**
 * A picture you can pinch to zoom, drag around when zoomed, and double-tap to zoom in/out.
 * When not zoomed, sideways swipes go to the pager (next/previous photo).
 */
@Composable
fun ZoomableImage(m: Media, onTap: () -> Unit) {
    ZoomableBox(onTap = onTap) {
        val ctx = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(m.uri).size(2560).build(),
            contentDescription = m.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ZoomableBox(onTap: () -> Unit, content: @Composable () -> Unit) {
    val scale = remember { Animatable(1f) }
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun clamp(s: Float) {
        val maxX = (size.width * (s - 1)) / 2f
        val maxY = (size.height * (s - 1)) / 2f
        scope.launch { ox.snapTo(ox.value.coerceIn(-maxX, maxX)) }
        scope.launch { oy.snapTo(oy.value.coerceIn(-maxY, maxY)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { size = this.size.let { IntSize(it.width, it.height) } }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                launch { scale.animateTo(1f) }; launch { ox.animateTo(0f) }; launch { oy.animateTo(0f) }
                            } else {
                                val target = 2.6f
                                val cx = this@pointerInput.size.width / 2f; val cy = this@pointerInput.size.height / 2f
                                launch { scale.animateTo(target) }
                                launch { ox.animateTo((cx - p.x) * (target - 1)) }
                                launch { oy.animateTo((cy - p.y) * (target - 1)) }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val fingers = ev.changes.count { it.pressed }
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        if (fingers >= 2 || scale.value > 1.01f) {
                            val newScale = (scale.value * zoom).coerceIn(1f, 6f)
                            val c = ev.calculateCentroid()
                            val cx = size.width / 2f; val cy = size.height / 2f
                            val k = newScale / scale.value
                            val nx = (ox.value + (c.x - cx) * (1 - k)) * 1f + pan.x
                            val ny = (oy.value + (c.y - cy) * (1 - k)) * 1f + pan.y
                            scope.launch { scale.snapTo(newScale) }
                            scope.launch { ox.snapTo(nx) }
                            scope.launch { oy.snapTo(ny) }
                            clamp(newScale)
                            // Let the pager have the swipe when we're at an edge of the zoomed picture.
                            val maxX = (size.width * (newScale - 1)) / 2f
                            val atEdge = fingers < 2 && ((nx >= maxX && pan.x > 0) || (nx <= -maxX && pan.x < 0))
                            if (!atEdge) ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (scale.value < 1.02f) scope.launch { scale.animateTo(1f); ox.animateTo(0f); oy.animateTo(0f) }
                }
            }
            .graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                translationX = ox.value; translationY = oy.value
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun VideoPage(m: Media, active: Boolean, onTap: () -> Unit) {
    VideoPlayerBox(m.uri, active, onTap) {
        AsyncImage(app.gallery.data.Thumb(m.uri, 1024), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}

/** Plays a video with standard controls only while its page is on screen. */
@Composable
fun VideoPlayerBox(uri: android.net.Uri, active: Boolean, onTap: () -> Unit, placeholder: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!active) {
            placeholder()
        } else {
            val player = remember(uri) {
                ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        setShowNextButton(false); setShowPreviousButton(false)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { onTap() })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun InfoSheet(m: Media) {
    val ctx = LocalContext.current
    var exif by remember(m.uri) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(m.uri) {
        exif = withContext(Dispatchers.IO) { readExif(ctx, m) }
    }
    val dt = Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault())
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        InfoRow("Name", m.name)
        InfoRow("Date", dt.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy • h:mm a")))
        if (m.width > 0) InfoRow("Size", "${m.width} × ${m.height} • ${"%.1f".format(m.width * m.height / 1_000_000f)} MP • ${humanSize(m.size)}")
        if (m.isVideo) InfoRow("Length", durationText(m.durationMs))
        InfoRow("Folder", m.relativePath.ifBlank { m.bucket })
        exif.forEach { (k, v) -> InfoRow(k, v) }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column(Modifier.padding(vertical = 7.dp)) {
        Text(k, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toFloat())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toFloat())
    b >= 1L shl 10 -> "%d KB".format(b / 1024)
    else -> "$b B"
}

private fun readExif(ctx: android.content.Context, m: Media): List<Pair<String, String>> {
    if (m.isVideo) return emptyList()
    return runCatching {
        val uri = runCatching { android.provider.MediaStore.setRequireOriginal(m.uri) }.getOrDefault(m.uri)
        val stream = runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull() ?: ctx.contentResolver.openInputStream(m.uri)!!
        stream.use { s ->
            val e = androidx.exifinterface.media.ExifInterface(s)
            buildList {
                val camera = listOfNotNull(e.getAttribute("Make"), e.getAttribute("Model")).joinToString(" ").trim()
                if (camera.isNotBlank()) add("Camera" to camera)
                val parts = listOfNotNull(
                    e.getAttribute("FNumber")?.let { "f/$it" },
                    e.getAttribute("ExposureTime")?.toDoubleOrNull()?.let { if (it < 1) "1/${(1 / it).toInt()}s" else "${it}s" },
                    e.getAttribute("PhotographicSensitivity")?.let { "ISO $it" },
                    e.getAttribute("FocalLength")?.let { v -> v.split("/").let { if (it.size == 2) "%.1f mm".format(it[0].toDouble() / it[1].toDouble()) else "$v mm" } },
                )
                if (parts.isNotEmpty()) add("Exposure" to parts.joinToString(" • "))
                e.latLong?.let { add("Location" to "%.5f, %.5f".format(it[0], it[1])) }
                e.getAttribute("Software")?.let { add("Software" to it) }
            }
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun RenameDialog(m: Media, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(m.name.substringBeforeLast('.')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
