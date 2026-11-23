package app.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gallery.data.Favorites
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import app.gallery.data.Thumb
import coil3.compose.AsyncImage
import java.text.NumberFormat
import kotlinx.coroutines.launch

/** Smart albums made from your photos (not folders). */
enum class Smart(val title: String, val icon: ImageVector) {
    Recent("Recent", Icons.Rounded.Collections),
    Videos("Videos", Icons.Rounded.Videocam),
    Favourites("Favourites", Icons.Rounded.Favorite),
    Gifs("GIFs", Icons.Rounded.Gif),
    Panoramas("Panoramas", Icons.Rounded.Panorama),
    ScreenRecordings("Screen recordings", Icons.Rounded.Videocam),
    Edited("Edited", Icons.Rounded.Edit),
    Large("Large files", Icons.Rounded.PhotoSizeSelectLarge);

    fun filter(all: List<Media>, favs: Set<Long>): List<Media> = when (this) {
        Recent -> all
        Videos -> all.filter { it.isVideo }
        Favourites -> all.filter { it.id in favs }
        Gifs -> all.filter { it.isGif }
        Panoramas -> all.filter { !it.isVideo && it.width > 0 && it.height > 0 && maxOf(it.width, it.height) >= 2.2f * minOf(it.width, it.height) }
        ScreenRecordings -> all.filter { it.isVideo && (it.isScreenshot || it.relativePath.contains("record", true) || it.name.contains("record", true)) }
        Edited -> all.filter { it.relativePath.contains("Edited", true) || it.name.contains("_edited", true) }
        Large -> all.filter { it.size > 25L * 1024 * 1024 }.sortedByDescending { it.size }
    }
}

enum class AlbumSort(val label: String) { Recent("Recently updated"), Name("Name"), Size("Most items") }

private fun count(n: Int) = NumberFormat.getIntegerInstance().format(n)

@Composable
fun AlbumsPage(all: List<Media>, sort: AlbumSort) {
    val favs by Favorites.ids.collectAsState()
    val trash by MediaRepo.trash.collectAsState()
    val folders = remember(all, sort) {
        val a = MediaRepo.albums(all)
        when (sort) {
            AlbumSort.Recent -> a
            AlbumSort.Name -> a.sortedBy { it.name.lowercase() }
            AlbumSort.Size -> a.sortedByDescending { it.count }
        }
    }
    // Pinned: the ones people open most, like OnePlus Photos.
    val camera = folders.firstOrNull { it.name.equals("Camera", true) }
    val shots = folders.firstOrNull { it.name.contains("screenshot", true) }
    val others = folders.filter { it != camera && it != shots }
    val pinnedFolders = others.take(4)
    val moreFolders = others.drop(4)
    val favList = remember(all, favs) { all.filter { it.id in favs } }
    val videos = remember(all) { all.filter { it.isVideo } }

    data class Tile(val name: String, val count: Int, val covers: List<Media>, val open: () -> Unit)
    val pinned = buildList {
        camera?.let { a -> add(Tile(a.name, a.count, listOf(a.cover)) { Nav.go(Route.AlbumView(a.id, a.name)) }) }
        add(Tile("Videos", videos.size, videos.take(1)) { Nav.go(Route.Virtual(Smart.Videos.name)) })
        add(Tile("Favourites", favList.size, favList.take(1)) { Nav.go(Route.Virtual(Smart.Favourites.name)) })
        pinnedFolders.forEach { a -> add(Tile(a.name, a.count, listOf(a.cover)) { Nav.go(Route.AlbumView(a.id, a.name)) }) }
        add(Tile("Recent", all.size, all.take(4)) { Nav.go(Route.Virtual(Smart.Recent.name)) })
        shots?.let { a ->
            val list = all.filter { it.bucketId == a.id }.take(4)
            add(Tile(a.name, a.count, list) { Nav.go(Route.AlbumView(a.id, a.name)) })
        }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 96.dp

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = 130.dp)) {
            item { SectionTitle("Pinned") }
            items(pinned.chunked(3)) { row ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { t -> AlbumTile(t.name, t.count, t.covers, Modifier.weight(1f), t.open) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (moreFolders.isNotEmpty()) {
                item { SectionTitle("More albums") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(moreFolders, key = { it.id }) { a ->
                            AlbumTile(a.name, a.count, listOf(a.cover), Modifier.width(118.dp)) { Nav.go(Route.AlbumView(a.id, a.name)) }
                        }
                    }
                }
            }
            item { SectionTitle("Media types") }
            item {
                val types = listOf(Smart.Gifs, Smart.Panoramas, Smart.ScreenRecordings, Smart.Edited, Smart.Large)
                    .map { it to remember(all, it) { it.filter(all, favs).size } }
                Card {
                    types.forEachIndexed { i, (k, n) ->
                        ListRow(k.icon, k.title, count(n)) { Nav.go(Route.Virtual(k.name)) }
                        if (i < types.lastIndex) HorizontalDivider(Modifier.padding(start = 64.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
            item { SectionTitle("More") }
            item {
                Card {
                    ListRow(Icons.Rounded.Lock, "Vault", "Private") { Nav.tab(Tab.Vault) }
                    HorizontalDivider(Modifier.padding(start = 64.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ListRow(Icons.Rounded.Delete, "Recently deleted", count(trash.size)) { Nav.go(Route.Trash) }
                }
            }
        }
        FloatingTitle("Albums") { AlbumsMenu() }
    }
}

@Composable
private fun AlbumsMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(androidx.compose.material.icons.Icons.Rounded.MoreVert, "More") { open = true }
        androidx.compose.material3.DropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp)) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; Nav.go(Route.Settings) })
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp))
}

/** Rounded square cover (or a 2×2 collage) with the name and count under it. */
@Composable
fun AlbumTile(name: String, count: Int, covers: List<Media>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (covers.size >= 4) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    covers.take(4).chunked(2).forEach { pair ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { m ->
                                AsyncImage(Thumb(m.uri, 256), null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp)))
                            }
                        }
                    }
                }
            } else covers.firstOrNull()?.let { m ->
                AsyncImage(Thumb(m.uri, 384), name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(count(count), style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
fun ListRow(icon: ImageVector?, title: String, value: String?, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A smart album (Videos, GIFs, Edited…). */
@Composable
fun VirtualScreen(kind: String) {
    val all by MediaRepo.all.collectAsState()
    val favs by Favorites.ids.collectAsState()
    val k = runCatching { Smart.valueOf(kind) }.getOrDefault(Smart.Recent)
    val list = remember(all, favs, k) { k.filter(all, favs) }
    SimpleList(k.title, list) { EmptyState(k.icon, "Nothing here yet", "") }
}

@Composable
fun NewAlbumDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New album") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Album name") })
                Text("Next, pick the photos to add.", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(name.trim()) }) { Text("Next") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick photos to put into a new album. */
@Composable
fun PickIntoScreen(name: String) {
    val all by MediaRepo.all.collectAsState()
    val selection = remember { Selection() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ask = rememberSystemAsk()
    val path = "Pictures/" + name.replace("/", "_") + "/"
    Box(Modifier.fillMaxSize()) {
        MediaGrid(all, selection, header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "back") { BackTitle("Add to \"$name\"", "${selection.map.size} selected") }
        })
        if (selection.active) Box(Modifier.align(Alignment.BottomCenter)) {
            FloatingBar {
                BarButton(Icons.Rounded.Collections, "Copy here") {
                    val items = selection.items
                    scope.launch {
                        val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.gallery.data.Actions.copy(context, items, path) }
                        android.widget.Toast.makeText(context, "Added $n to $name", android.widget.Toast.LENGTH_SHORT).show()
                        Nav.back()
                    }
                }
                BarButton(Icons.Rounded.Edit, "Move here") {
                    val items = selection.items
                    ask(app.gallery.data.Actions.writeRequest(context, items)) { ok ->
                        if (ok) scope.launch {
                            val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.gallery.data.Actions.move(context, items, path) }
                            android.widget.Toast.makeText(context, "Moved $n to $name", android.widget.Toast.LENGTH_SHORT).show()
                            Nav.back()
                        }
                    }
                }
            }
        }
    }
}
