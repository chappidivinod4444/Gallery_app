package app.gallery.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.gallery.data.Vault
import app.gallery.data.VaultItem
import app.gallery.data.VaultThumb
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Items picked in the Vault (the main screen hides its tab bar while picking). */
object VaultSelection {
    val map = mutableStateMapOf<String, VaultItem>()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultTab() {
    val unlocked by Vault.unlocked.collectAsState()
    val items by Vault.items.collectAsState()
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val selected = VaultSelection.map
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    fun unlock() = AppLock.prompt(
        activity, "Open the Vault", "Your hidden photos are encrypted on this phone",
        onOk = {
            scope.launch {
                error = runCatching { withContext(Dispatchers.IO) { Vault.open() } }.exceptionOrNull()?.let { "Couldn't open the Vault: ${it.message}" }
            }
        },
        onFail = { error = it },
    )

    if (!unlocked) {
        LaunchedEffect(Unit) { if (AppLock.canUse(activity)) unlock() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(100.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(48.dp)) }
                Text("Vault", style = MaterialTheme.typography.headlineMedium)
                Text(
                    error ?: "Private photos and videos, encrypted with a key kept in your phone's secure hardware. " +
                        "Hidden from your gallery, other apps and screenshots.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                if (AppLock.canUse(activity)) Button(onClick = { error = null; unlock() }) {
                    Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.padding(end = 8.dp)); Text("Unlock")
                } else Text(
                    "Set up a screen lock or fingerprint on your phone to use the Vault.",
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vault", style = MaterialTheme.typography.headlineLarge)
                        Text("${items.size} encrypted items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Vault.lock() }) { Icon(Icons.Rounded.Lock, "Lock now") }
                }
            }
            if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Rounded.Shield, "Nothing hidden yet",
                    "In Photos, long-press to select pictures, then tap Hide. Or open a photo and choose Move to Vault.",
                )
            }
            items(items, key = { it.id }) { v ->
                val isSel = v.id in selected
                Box(
                    Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickable(
                            onClick = {
                                if (selected.isNotEmpty()) { if (isSel) selected.remove(v.id) else selected[v.id] = v }
                                else Nav.go(Route.VaultViewer(items.indexOf(v)))
                            },
                            onLongClick = { if (isSel) selected.remove(v.id) else selected[v.id] = v },
                        ),
                ) {
                    AsyncImage(VaultThumb(v.id), v.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (isSel) 10.dp else 0.dp).clip(RoundedCornerShape(if (isSel) 16.dp else 0.dp)))
                    if (v.isVideo) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    if (isSel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "Cancel") }
                Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                FloatingBar {
                    BarButton(Icons.Rounded.Restore, "Unhide") {
                        val list = selected.values.toList()
                        busy = true
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { Vault.restore(list) }
                            selected.clear(); busy = false
                            Toast.makeText(activity, "Restored $n to your gallery", Toast.LENGTH_SHORT).show()
                        }
                    }
                    BarButton(Icons.Rounded.Delete, "Delete") { confirmDelete = true }
                }
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${selected.size} for good?") },
        text = { Text("They're only stored in the Vault, so they can't be recovered.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                val list = selected.values.toList()
                scope.launch { withContext(Dispatchers.IO) { Vault.deleteForever(list) }; selected.clear() }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultViewer(start: Int) {
    val unlocked by Vault.unlocked.collectAsState()
    val items by Vault.items.collectAsState()
    if (!unlocked || items.isEmpty()) { LaunchedEffect(Unit) { Nav.back() }; return }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    Immersive(!chrome)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Vault.clearTemp() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, key = { items[it].id }, beyondViewportPageCount = 1) { page ->
            val v = items[page]
            if (v.isVideo) {
                var file by remember(v.id) { mutableStateOf<java.io.File?>(null) }
                val active = page == pager.settledPage
                LaunchedEffect(v.id, active) { if (active && file == null) file = withContext(Dispatchers.IO) { runCatching { Vault.videoFile(v) }.getOrNull() } }
                val f = file
                if (f != null && active) VideoPlayerBox(android.net.Uri.fromFile(f), true, { chrome = !chrome }) {}
                else AsyncImage(VaultThumb(v.id), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                var bmp by remember(v.id) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(v.id) { bmp = withContext(Dispatchers.IO) { runCatching { Vault.image(v) }.getOrNull() } }
                val b = bmp
                ZoomableBox(onTap = { chrome = !chrome }) {
                    if (b != null) Image(b.asImageBitmap(), v.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else AsyncImage(VaultThumb(v.id), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (chrome) Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text(items.getOrNull(pager.currentPage)?.name.orEmpty(), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.padding(12.dp).size(18.dp))
        }
        if (chrome) Text(
            "Encrypted • only visible here", color = Color(0xB3FFFFFF), style = Caption,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}
