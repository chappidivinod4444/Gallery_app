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
import androidx.compose.material3.AlertDialog
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

@Composable
fun MainScreen(tab: Tab, onAskAccess: () -> Unit) {
    val selection = remember(tab) { Selection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    BackHandler(enabled = !selection.active && tab != Tab.Photos) { Nav.tab(Tab.Photos) }
    val all by MediaRepo.all.collectAsState()
    val favIds by Favorites.ids.collectAsState()
    val videosInPhotos by Settings.showVideosInPhotos.collectAsState()
    val visible = when (tab) {
        Tab.Photos -> if (videosInPhotos) all else all.filter { !it.isVideo }
        Tab.Favorites -> all.filter { it.id in favIds }
        else -> emptyList()
    }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.Photos -> PhotosTab(visible, selection, onAskAccess)
            Tab.Albums -> AlbumsTab(all)
            Tab.Favorites -> FavoritesTab(visible, selection)
            Tab.Vault -> VaultTab()
        }
        if (selection.active) Box(Modifier.align(Alignment.TopCenter)) { SelectionTopBar(selection, visible) }
        Box(Modifier.align(Alignment.BottomCenter)) {
            if (selection.active) SelectionActions(selection)
            else if (!(tab == Tab.Vault && VaultSelection.map.isNotEmpty())) NavBar(tab, onTab = { VaultSelection.map.clear(); Nav.tab(it) })
        }
    }
}

@Composable
private fun TitleActions() {
    IconButton(onClick = { Nav.go(Route.Search) }) { Icon(Icons.Rounded.Search, "Search") }
    IconButton(onClick = { Nav.go(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
}

@Composable
private fun PhotosTab(list: List<Media>, selection: Selection, onAskAccess: () -> Unit) {
    val loaded by MediaRepo.loaded.collectAsState()
    val context = LocalContext.current
    val partial = remember { !hasFullAccess(context) }
    MediaGrid(list, selection, header = {
        bigTitle("Photos", if (list.isNotEmpty()) "${list.size} items" else null) { TitleActions() }
        item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                item { QuickChip(Icons.Rounded.Videocam, "Videos") { Nav.go(Route.Videos) } }
                item {
                    QuickChip(Icons.Rounded.Screenshot, "Screenshots") {
                        val a = MediaRepo.albums(MediaRepo.all.value).firstOrNull { it.name.contains("screenshot", true) }
                        if (a != null) Nav.go(Route.AlbumView(a.id, a.name)) else Toast.makeText(context, "No screenshots", Toast.LENGTH_SHORT).show()
                    }
                }
                item { QuickChip(Icons.Rounded.Favorite, "Favourites") { Nav.tab(Tab.Favorites) } }
                item { QuickChip(Icons.Rounded.Delete, "Trash") { Nav.go(Route.Trash) } }
            }
        }
        if (partial) item(span = { GridItemSpan(maxLineSpan) }, key = "partial") {
            Row(
                Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onAskAccess).padding(16.dp),
            ) { Text("You've shared only some photos. Tap to allow all photos and videos.", color = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        if (loaded && list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            EmptyState(Icons.Rounded.PhotoLibrary, "No photos yet", "Photos and videos you take or save will show up here.")
        }
    })
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
private fun ListScreen(title: String, list: List<Media>, empty: @Composable () -> Unit = {}) {
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
    ListScreen(name, remember(all, id) { all.filter { it.bucketId == id } }) {
        EmptyState(Icons.Rounded.Folder, "This album is empty", "")
    }
}

@Composable
fun VideosScreen() {
    val all by MediaRepo.all.collectAsState()
    ListScreen("Videos", remember(all) { all.filter { it.isVideo } }) {
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
        item { Section("Look") }
        item {
            Choice("Theme", listOf("System", "Light", "Dark"), theme.ordinal) { Settings.setTheme(Settings.ThemeMode.entries[it]) }
        }
        item { Toggle("Colours from your wallpaper", "Material You", dynamic) { Settings.setDynamic(it) } }
        item { Toggle("Pure black dark mode", "Saves battery on OLED screens", amoled) { Settings.setAmoled(it) } }
        item { Choice("Photos per row", listOf("2", "3", "4", "5", "6", "7"), columns - 2) { Settings.setColumns(it + 2) } }
        item { Toggle("Show videos in Photos", null, videos) { Settings.setVideosInPhotos(it) } }
        item { Section("Privacy & security") }
        item {
            Toggle("App lock", "Fingerprint, face or screen lock to open Gallery", lock) { on ->
                if (!on) {
                    AppLock.prompt(activity, "Turn off app lock", onOk = { Settings.setAppLock(false) })
                } else if (!AppLock.canUse(activity)) {
                    Toast.makeText(activity, "Set up a screen lock or fingerprint on your phone first", Toast.LENGTH_LONG).show()
                } else {
                    AppLock.prompt(activity, "Turn on app lock", onOk = { Settings.setAppLock(true) })
                }
            }
        }
        if (lock) {
            item {
                Choice("Lock again after", listOf("Right away", "1 min", "5 min", "15 min"), listOf(0, 60, 300, 900).indexOf(lockAfter).coerceAtLeast(0)) {
                    Settings.setLockAfter(listOf(0, 60, 300, 900)[it])
                }
            }
            item { Toggle("Hide content in recent apps", "Also blocks screenshots", secure) { Settings.setSecure(it) } }
        }
        item {
            Text(
                "The Vault always asks to unlock, is always hidden from screenshots, and closes when you leave the app. " +
                    "This app has no internet permission: nothing ever leaves your phone.",
                style = Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item { Section("About") }
        item {
            val version = remember {
                runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrDefault("?")
            }
            Text("Gallery $version", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp))
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
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { i, o ->
                androidx.compose.material3.SegmentedButton(
                    selected = i == selected, onClick = { onPick(i) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(o, maxLines = 1) }
            }
        }
    }
}
