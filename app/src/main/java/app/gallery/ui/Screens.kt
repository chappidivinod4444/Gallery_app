package app.gallery.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gallery.data.Actions
import app.gallery.data.Album
import app.gallery.data.Favorites
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import app.gallery.data.Settings
import app.gallery.data.Thumb
import coil3.compose.AsyncImage
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PhotoFilter(val label: String) {
    Photos("Photos"), Videos("Videos"), Screenshots("Screenshots"), Favorites("Favourites"), Edited("Edited"), All("All")
}

@Composable
fun MainScreen(tab: Tab, onAskAccess: () -> Unit) {
    val selection = remember(tab) { Selection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = !selection.active && tab != Tab.Photos) { Nav.tab(Tab.Photos) }
    val all by MediaRepo.all.collectAsState()
    val favIds by Favorites.ids.collectAsState()
    val videosInPhotos by Settings.showVideosInPhotos.collectAsState()
    var filter by remember { mutableStateOf(PhotoFilter.All) }
    var albumSort by remember { mutableStateOf(AlbumSort.Recent) }
    var newAlbum by remember { mutableStateOf(false) }
    val visible = when (tab) {
        Tab.Photos -> when (filter) {
            PhotoFilter.All -> if (videosInPhotos) all else all.filter { !it.isVideo }
            PhotoFilter.Photos -> all.filter { !it.isVideo }
            PhotoFilter.Videos -> all.filter { it.isVideo }
            PhotoFilter.Favorites -> all.filter { it.id in favIds }
            PhotoFilter.Screenshots -> all.filter { it.isScreenshot }
            PhotoFilter.Edited -> all.filter { it.relativePath.contains("Edited", true) || it.name.contains("_edited", true) }
        }
        Tab.Favorites -> all.filter { it.id in favIds }
        else -> emptyList()
    }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.Photos -> PhotosTab(visible, selection, filter, onAskAccess)
            Tab.Albums -> AlbumsPage(all, albumSort)
            Tab.Favorites -> FavoritesTab(visible, selection)
            Tab.Vault -> VaultTab()
        }
        if (selection.active) Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, visible) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (selection.active) Box(Modifier.align(Alignment.Center)) { SelectionActions(selection) }
            else if (!(tab == Tab.Vault && VaultSelection.map.isNotEmpty())) BottomChrome(tab, filter, albumSort, { filter = it }, { albumSort = it }, { newAlbum = true })
        }
    }
    if (newAlbum) NewAlbumDialog(onDismiss = { newAlbum = false }) { name -> newAlbum = false; Nav.go(Route.PickInto(name)) }
}

/** Bottom of the main screen, OnePlus-style: grid size · Photos | Albums · filter. */
@Composable
private fun BottomChrome(
    tab: Tab, filter: PhotoFilter, sort: AlbumSort,
    onFilter: (PhotoFilter) -> Unit, onSort: (AlbumSort) -> Unit, onNewAlbum: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab == Tab.Albums) Box {
            RoundButton(Icons.Rounded.Tune, "Sort albums") { sortMenu = true }
            androidx.compose.material3.DropdownMenu(sortMenu, { sortMenu = false }, shape = RoundedCornerShape(20.dp)) {
                AlbumSort.entries.forEach { o ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(o.label) },
                        trailingIcon = { if (o == sort) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { sortMenu = false; onSort(o) },
                    )
                }
            }
        } else RoundButton(Icons.Rounded.GridView, "Grid size", visible = tab == Tab.Photos || tab == Tab.Favorites) {
            // Cycle through comfortable sizes: 3 → 5 → 7 → 15 → 3
            val next = when (Settings.columns.value) { in 0..3 -> 5; in 4..5 -> 7; in 6..10 -> 15; else -> 3 }
            Settings.setColumns(next)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.shadow(12.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)).padding(5.dp),
        ) {
            listOf(Tab.Photos to "Photos", Tab.Albums to "Albums").forEach { (t, label) ->
                val on = t == tab
                Text(
                    label, style = MaterialTheme.typography.titleMedium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(26.dp))
                        .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .clickable { VaultSelection.map.clear(); Nav.tab(t) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (tab == Tab.Albums) RoundButton(Icons.Rounded.Add, "New album", onClick = onNewAlbum)
        else Box {
            RoundButton(Icons.Rounded.FilterList, "Filter", visible = tab == Tab.Photos, highlighted = filter != PhotoFilter.All) { menu = true }
            androidx.compose.material3.DropdownMenu(
                menu, { menu = false }, shape = RoundedCornerShape(26.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                PhotoFilter.entries.forEach { f ->
                    val icon = when (f) {
                        PhotoFilter.All -> Icons.Rounded.Collections; PhotoFilter.Photos -> Icons.Rounded.Image
                        PhotoFilter.Videos -> Icons.Rounded.Videocam; PhotoFilter.Favorites -> Icons.Rounded.FavoriteBorder
                        PhotoFilter.Screenshots -> Icons.Rounded.Screenshot; PhotoFilter.Edited -> Icons.Rounded.Edit
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(f.label, style = MaterialTheme.typography.titleMedium, color = if (f == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = { Icon(icon, null, tint = if (f == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        trailingIcon = { if (f == filter) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; onFilter(f) },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RoundButton(icon: ImageVector, label: String, visible: Boolean = true, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp)
            .then(if (visible) Modifier.shadow(10.dp, androidx.compose.foundation.shape.CircleShape) else Modifier)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (!visible) Color.Transparent
                else if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            )
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (visible) Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface) }
}

/** Title floating over the photos with round buttons, like OnePlus Photos. */
@Composable
fun FloatingTitle(title: String, menu: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        RoundButton(Icons.Rounded.Search, "Search") { Nav.go(Route.Search) }
        Spacer(Modifier.width(10.dp))
        menu()
    }
}

@Composable
private fun MoreMenu() {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(Icons.Rounded.MoreVert, "More") { open = true }
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Videos") }, onClick = { open = false; Nav.go(Route.Videos) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Favourites") }, onClick = { open = false; Nav.tab(Tab.Favorites) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Vault") }, onClick = { open = false; Nav.tab(Tab.Vault) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Trash") }, onClick = { open = false; Nav.go(Route.Trash) })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; Nav.go(Route.Settings) })
        }
    }
}

@Composable
private fun TitleActions() {
    IconButton(onClick = { Nav.go(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") }
    IconButton(onClick = { Nav.go(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
}

@Composable
private fun PhotosTab(list: List<Media>, selection: Selection, filter: PhotoFilter, onAskAccess: () -> Unit) {
    val loaded by MediaRepo.loaded.collectAsState()
    val context = LocalContext.current
    val partial = remember { !hasFullAccess(context) }
    Box(Modifier.fillMaxSize()) {
        val top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 92.dp
        MediaGrid(list, selection, topPadding = top, header = {
            if (partial) item(span = { GridItemSpan(maxLineSpan) }, key = "partial") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(onClick = onAskAccess).padding(16.dp),
                ) { Text("You've shared only some photos. Tap to allow all photos and videos.", color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            if (loaded && list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyState(Icons.Rounded.PhotoLibrary, if (filter == PhotoFilter.All) "No photos yet" else "Nothing here", "Photos and videos you take or save will show up here.")
            }
        })
        if (!selection.active) FloatingTitle(if (filter == PhotoFilter.All) "Photos" else filter.label) { MoreMenu() }
    }
}

@Composable
fun QuickChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FavoritesTab(list: List<Media>, selection: Selection) {
    MediaGrid(list, selection, header = {
        bigTitle("Favourites", if (list.isNotEmpty()) "${list.size} items" else null) { TitleActions() }
        if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            EmptyState(Icons.Rounded.Favorite, "No favourites yet", "Tap the heart on a photo to keep it here.")
        }
    })
}

@Composable
private fun AlbumsTab(all: List<Media>) {
    val albums = remember(all) { MediaRepo.albums(all) }
    val trash by MediaRepo.trash.collectAsState()
    val videos = remember(all) { all.filter { it.isVideo } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.statusBarsPadding().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Albums", style = MaterialTheme.typography.headlineLarge)
                    Text("${albums.size} albums", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TitleActions()
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UtilityTile(Icons.Rounded.Videocam, "Videos", "${videos.size}", Modifier.weight(1f)) { Nav.go(Route.Videos) }
                UtilityTile(Icons.Rounded.Favorite, "Favourites", "${Favorites.ids.value.size}", Modifier.weight(1f)) { Nav.tab(Tab.Favorites) }
                UtilityTile(Icons.Rounded.Lock, "Vault", "Private", Modifier.weight(1f)) { Nav.tab(Tab.Vault) }
                UtilityTile(Icons.Rounded.Delete, "Trash", "${trash.size}", Modifier.weight(1f)) { Nav.go(Route.Trash) }
            }
        }
        items(albums, key = { it.id }) { a -> AlbumCard(a) }
    }
}

@Composable
private fun UtilityTile(icon: ImageVector, label: String, count: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).padding(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        Text(count, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlbumCard(a: Album) {
    Column(Modifier.clip(RoundedCornerShape(22.dp)).clickable { Nav.go(Route.AlbumView(a.id, a.name)) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            AsyncImage(Thumb(a.cover.uri, 512), a.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x66000000))))
        }
        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Text("${a.count} items", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun BackTitle(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun SimpleList(title: String, list: List<Media>, empty: @Composable () -> Unit = {}) {
    val selection = remember { Selection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(list, selection, header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "back") { BackTitle(title, "${list.size} items") }
            if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { empty() }
        })
        if (selection.active) {
            Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, list) }
            Box(Modifier.align(Alignment.BottomCenter)) { SelectionActions(selection) }
        }
    }
}

@Composable
fun AlbumScreen(id: Long, name: String) {
    val all by MediaRepo.all.collectAsState()
    SimpleList(name, remember(all, id) { all.filter { it.bucketId == id } }) {
        EmptyState(Icons.Rounded.Folder, "This album is empty", "")
    }
}

@Composable
fun VideosScreen() {
    val all by MediaRepo.all.collectAsState()
    SimpleList("Videos", remember(all) { all.filter { it.isVideo } }) {
        EmptyState(Icons.Rounded.Videocam, "No videos", "Videos you record or save show up here.")
    }
}

@Composable
fun SearchScreen() {
    val all by MediaRepo.all.collectAsState()
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("All") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(all, q, kind) { search(all, q, kind) }
    val selection = remember { Selection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(results, selection, header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { Nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        TextField(
                            value = q, onValueChange = { q = it }, singleLine = true,
                            placeholder = { Text("Search by name, album, month or year") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                        )
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All", "Photos", "Videos", "Screenshots", "GIFs", "Favourites", "This month", "Large files")) { k ->
                            val on = k == kind
                            Text(
                                k, style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { kind = k }.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (q.isNotBlank() || kind != "All") Text(
                        "${results.size} results", style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        })
        if (selection.active) {
            Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, results) }
            Box(Modifier.align(Alignment.BottomCenter)) { SelectionActions(selection) }
        }
    }
}

private fun search(all: List<Media>, q: String, kind: String): List<Media> {
    val now = java.time.LocalDate.now()
    var list = when (kind) {
        "Photos" -> all.filter { !it.isVideo }
        "Videos" -> all.filter { it.isVideo }
        "Screenshots" -> all.filter { it.isScreenshot }
        "GIFs" -> all.filter { it.isGif }
        "Favourites" -> all.filter { Favorites.isFav(it.id) }
        "This month" -> all.filter { it.day.year == now.year && it.day.month == now.month }
        "Large files" -> all.filter { it.size > 20L * 1024 * 1024 }.sortedByDescending { it.size }
        else -> all
    }
    val words = q.trim().lowercase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return if (kind == "All") emptyList() else list
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy d EEEE", Locale.getDefault())
    list = list.filter { m ->
        val hay = (m.name + " " + m.bucket + " " + m.day.format(monthFmt) + " " + m.mime).lowercase(Locale.getDefault())
        words.all { hay.contains(it) }
    }
    return list
}

@Composable
fun TrashScreen() {
    val trash by MediaRepo.trash.collectAsState()
    val context = LocalContext.current
    val ask = rememberSystemAsk()
    val selection = remember { Selection() }
    var confirmEmpty by remember { mutableStateOf(false) }
    BackHandler(enabled = selection.active) { selection.clear() }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(trash, selection, grouped = false, header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "back") {
                BackTitle("Trash", "Items are deleted for good after 30 days") {
                    if (trash.isNotEmpty()) IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Rounded.DeleteSweep, "Empty trash") }
                }
            }
            if (trash.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                EmptyState(Icons.Rounded.Delete, "Trash is empty", "Deleted photos stay here for 30 days.")
            }
        })
        if (selection.active) {
            Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, trash) }
            Box(Modifier.align(Alignment.BottomCenter)) {
                FloatingBar {
                    BarButton(Icons.Rounded.Restore, "Restore") {
                        ask(Actions.trashRequest(context, selection.items, trash = false)) { if (it) { selection.clear(); MediaRepo.refreshSoon() } }
                    }
                    BarButton(Icons.Rounded.Delete, "Delete forever") {
                        ask(Actions.deleteRequest(context, selection.items)) { if (it) { selection.clear(); MediaRepo.refreshSoon() } }
                    }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Empty trash?") },
        text = { Text("All ${trash.size} items will be deleted for good.") },
        confirmButton = {
            TextButton(onClick = {
                confirmEmpty = false
                ask(Actions.deleteRequest(context, trash)) { MediaRepo.refreshSoon() }
            }) { Text("Empty") }
        },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
    )
}

@Composable
fun SettingsScreen() {
    val theme by Settings.theme.collectAsState()
    val dynamic by Settings.dynamicColor.collectAsState()
    val amoled by Settings.amoled.collectAsState()
    val lock by Settings.appLock.collectAsState()
    val lockAfter by Settings.lockAfter.collectAsState()
    val secure by Settings.secureScreen.collectAsState()
    val videos by Settings.showVideosInPhotos.collectAsState()
    val columns by Settings.columns.collectAsState()
    val activity = LocalContext.current as androidx.fragment.app.FragmentActivity
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { BackTitle("Settings") }
        item { Section("Browsing") }
        item {
            Card {
                Choice("Photos per row", listOf("3", "4", "5", "7", "10"), listOf(3, 4, 5, 7, 10).indexOf(columns).coerceAtLeast(0)) {
                    Settings.setColumns(listOf(3, 4, 5, 7, 10)[it])
                }
                Toggle("Show videos in Photos", null, videos) { Settings.setVideosInPhotos(it) }
            }
        }
        item { Section("Look") }
        item {
            Card {
                Choice("Theme", listOf("System", "Light", "Dark"), theme.ordinal) { Settings.setTheme(Settings.ThemeMode.entries[it]) }
                Toggle("Colours from your wallpaper", "Material You", dynamic) { Settings.setDynamic(it) }
                Toggle("Pure black dark mode", "Saves battery on OLED screens", amoled) { Settings.setAmoled(it) }
            }
        }
        item { Section("Privacy & security") }
        item {
            Card {
                Toggle("App lock", "Fingerprint, face or screen lock to open Gallery", lock) { on ->
                    if (!on) {
                        AppLock.prompt(activity, "Turn off app lock", onOk = { Settings.setAppLock(false) })
                    } else if (!AppLock.canUse(activity)) {
                        Toast.makeText(activity, "Set up a screen lock or fingerprint on your phone first", Toast.LENGTH_LONG).show()
                    } else {
                        AppLock.prompt(activity, "Turn on app lock", onOk = { Settings.setAppLock(true) })
                    }
                }
                if (lock) {
                    Choice("Lock again after", listOf("Right away", "1 min", "5 min", "15 min"), listOf(0, 60, 300, 900).indexOf(lockAfter).coerceAtLeast(0)) {
                        Settings.setLockAfter(listOf(0, 60, 300, 900)[it])
                    }
                }
                Toggle("Block screenshots", "Hides the app in recent apps too. Off by default; the Vault is always protected.", secure) { Settings.setSecure(it) }
                ListRow(null, "Vault", null, "Encrypted private album") { Nav.tab(Tab.Vault) }
            }
        }
        item {
            Text(
                "The Vault always asks to unlock, blocks screenshots only while it is open, and closes when you leave the app. " +
                    "This app has no internet permission: nothing ever leaves your phone.",
                style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
            )
        }
        item { Section("More") }
        item {
            val version = remember {
                runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrDefault("?")
            }
            Card {
                ListRow(null, "Recently deleted", null) { Nav.go(Route.Trash) }
                ListRow(null, "About Gallery", version) {}
            }
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
private fun Toggle(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = value, onCheckedChange = onChange)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = i == selected, onClick = { onPick(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(o, maxLines = 1) }
            }
        }
    }
}
