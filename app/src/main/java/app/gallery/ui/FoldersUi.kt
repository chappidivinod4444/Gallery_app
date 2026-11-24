package app.gallery.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.gallery.data.Actions
import app.gallery.data.Album
import app.gallery.data.Folders
import app.gallery.data.HiddenScan
import app.gallery.data.MediaRepo
import app.gallery.data.Thumb
import coil3.compose.AsyncImage

/** Opens Android's "All files access" page for this app (needed to see folders other apps hid). */
private fun askAllFiles(c: android.content.Context) {
    val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + c.packageName))
    runCatching { c.startActivity(i) }.onFailure { c.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}

/** Re-checks "All files access" whenever you come back to the app. */
@Composable
private fun rememberAllFilesAllowed(): Boolean {
    var allowed by remember { mutableStateOf(HiddenScan.allowed()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                val now = HiddenScan.allowed()
                if (now != allowed) { allowed = now; if (now && Folders.showNomedia.value) MediaRepo.refresh(force = true) }
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return allowed
}

private fun count(n: Int) = java.text.NumberFormat.getIntegerInstance().format(n)

/** Settings → Folders: choose which folders show, which stay out of Photos, and which are pinned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFoldersScreen() {
    val everything by MediaRepo.everything.collectAsState()
    val hidden by Folders.hidden.collectAsState()
    val outOfPhotos by Folders.notInPhotos.collectAsState()
    val pinned by Folders.pinned.collectAsState()
    val showNomedia by Folders.showNomedia.collectAsState()
    val allowed = rememberAllFilesAllowed()
    val ctx = LocalContext.current
    val albums = remember(everything) { MediaRepo.albums(everything).sortedBy { it.name.lowercase() } }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Album?>(null) }
    val shown = remember(albums, query) { if (query.isBlank()) albums else albums.filter { it.name.contains(query.trim(), true) || it.path.contains(query.trim(), true) } }
    val pinnedAlbums = pinned.mapNotNull { id -> albums.firstOrNull { it.id == id } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { BackTitle("Folders", "${albums.size} folders · ${hidden.size} hidden") }
        item {
            Text(
                "Switch a folder off to hide it from Photos and Albums (nothing is deleted — find it under Albums → Hidden folders). " +
                    "Tap a folder for more: keep it out of the Photos timeline, or pin it to the top of Albums.",
                style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        item {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search folders") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (hidden.isNotEmpty() && query.isBlank()) item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { Folders.setVisible(hidden, true) }) { Text("Show all folders") }
            }
        }

        if (pinnedAlbums.isNotEmpty() && query.isBlank()) {
            item { FolderSection("Pinned to the top of Albums") }
            item {
                Card {
                    pinnedAlbums.forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(a)
                            Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(enabled = i > 0, onClick = { Folders.move(a.id, -1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                            IconButton(enabled = i < pinnedAlbums.lastIndex, onClick = { Folders.move(a.id, 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { Folders.setPinned(a.id, false) }) { Icon(Icons.Rounded.Close, "Unpin") }
                        }
                    }
                }
            }
        }

        item { FolderSection(if (query.isBlank()) "All folders" else "${shown.size} found") }
        item {
            Card {
                shown.forEach { a ->
                    val visible = a.id !in hidden
                    Row(
                        Modifier.fillMaxWidth().clickable { sheet = a }.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.alpha(if (visible) 1f else 0.4f)) { Cover(a) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp).alpha(if (visible) 1f else 0.55f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${count(a.count)} · ${a.path.trimEnd('/')}", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val tags = buildList {
                                if (!visible) add("Hidden")
                                if (a.id in outOfPhotos) add("Not in Photos")
                                if (a.id in pinned) add("Pinned")
                            }
                            if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), style = Caption, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(visible, { Folders.setVisible(a.id, it) })
                    }
                }
            }
        }

        item { FolderSection("Hidden by other apps") }
        item {
            Card {
                Row(Modifier.fillMaxWidth().clickable { Folders.setShowNomedia(!showNomedia); if (!showNomedia && !allowed) askAllFiles(ctx) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show folders other apps hid", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Some apps hide their pictures from galleries (a \".nomedia\" file or a folder starting with \".\"). " +
                                "Seeing them needs Android's \"All files access\" for this app. They appear under Albums → Hidden folders.",
                            style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(showNomedia, { on -> Folders.setShowNomedia(on); if (on && !allowed) askAllFiles(ctx) })
                }
                if (showNomedia && !allowed) Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
                    Button(onClick = { askAllFiles(ctx) }) { Text("Allow All files access") }
                }
            }
        }
    }
    sheet?.let { a -> FolderSheet(a) { sheet = null } }
}

@Composable
private fun FolderSection(t: String) {
    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun Cover(a: Album) {
    AsyncImage(
        Thumb(a.cover.uri, 192), a.name, contentScale = ContentScale.Crop,
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** Options for one folder (also opened by long-pressing an album). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(a: Album, onDismiss: () -> Unit) {
    val hidden by Folders.hidden.collectAsState()
    val outOfPhotos by Folders.notInPhotos.collectAsState()
    val pinned by Folders.pinned.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(a)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(a.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${count(a.count)} items · ${a.path.trimEnd('/')}", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetSwitch(Icons.Rounded.Visibility, "Show this folder", "Off = hidden from Photos and Albums", a.id !in hidden) { Folders.setVisible(a.id, it) }
            SheetSwitch(Icons.Rounded.Tune, "Show in the Photos timeline", "Off = only inside its album (good for WhatsApp, Screenshots…)", a.id !in outOfPhotos) { Folders.setInPhotos(a.id, it) }
            SheetSwitch(Icons.Rounded.PushPin, "Pin to the top of Albums", null, a.id in pinned) { Folders.setPinned(a.id, it) }
            TextButton(onClick = { onDismiss(); Nav.go(Route.AlbumView(a.id, a.name)) }, modifier = Modifier.padding(start = 12.dp)) { Text("Open folder") }
        }
    }
}

@Composable
private fun SheetSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, set)
    }
}

/** Albums → Hidden folders: folders you hid, and (if turned on) folders other apps hid. */
@Composable
fun HiddenFoldersScreen() {
    val everything by MediaRepo.everything.collectAsState()
    val hidden by Folders.hidden.collectAsState()
    val nomedia by MediaRepo.nomedia.collectAsState()
    val showNomedia by Folders.showNomedia.collectAsState()
    val allowed = rememberAllFilesAllowed()
    val ctx = LocalContext.current
    val mine = remember(everything, hidden) { MediaRepo.albums(everything.filter { it.bucketId in hidden }) }
    val apps = remember(nomedia) { MediaRepo.albums(nomedia).sortedByDescending { it.count } }
    var sheet by remember { mutableStateOf<Album?>(null) }

    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BackTitle("Hidden folders", "${mine.size + apps.size} folders") {
                IconButton(onClick = { Nav.go(Route.ManageFolders) }) { Icon(Icons.Rounded.Tune, "Manage folders") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Hidden by you", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (mine.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Nothing hidden. Long-press any album, or go to Manage folders, to hide one.", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(mine, key = { it.id }) { a ->
            AlbumTile(a.name, a.count, listOf(a.cover), Modifier, onLongClick = { sheet = a }) { Nav.go(Route.AlbumView(a.id, a.name)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Hidden by other apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        }
        if (!showNomedia || !allowed) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.FolderOff, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Apps like WhatsApp or downloaders can hide folders from galleries. To see them, this app needs Android's \"All files access\". " +
                        "It's only used to look for pictures; nothing leaves your phone (the app has no internet access).",
                    style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { Folders.setShowNomedia(true); if (!allowed) askAllFiles(ctx) }) { Text(if (allowed) "Show them" else "Allow and show") }
            }
        } else if (apps.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Looking… (or none found)", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showNomedia && allowed) items(apps, key = { it.id }) { a ->
            AlbumTile(a.name, a.count, listOf(a.cover), Modifier) { Nav.go(Route.HiddenAppFolder(a.id, a.name)) }
        }
    }
    sheet?.let { a -> FolderSheet(a) { sheet = null } }
}

/** A folder another app hid: view and share only (the files belong to that app). */
@Composable
fun HiddenAppFolderScreen(id: Long, name: String) {
    val nomedia by MediaRepo.nomedia.collectAsState()
    val list = remember(nomedia, id) { nomedia.filter { it.bucketId == id } }
    val selection = remember { Selection() }
    val ctx = LocalContext.current
    BackHandler(enabled = selection.active) { selection.clear() }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(list, selection, header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "back") { BackTitle(name, "${list.size} items · hidden by an app") }
        })
        if (selection.active) {
            Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, list) }
            FilledTonalButton(
                onClick = { Actions.share(ctx, selection.items); selection.clear() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
        }
    }
}
