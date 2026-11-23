package app.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.gallery.data.Media
import app.gallery.data.MediaRepo
import app.gallery.edit.EditorScreen

enum class Tab { Photos, Albums, Favorites, Vault }

sealed interface Route {
    data class Main(val tab: Tab) : Route
    data class AlbumView(val id: Long, val name: String) : Route
    /** Shows [list] (set in [Nav.viewerList]) starting at [start]. */
    data class Viewer(val start: Int, val key: Long = System.nanoTime()) : Route
    data class Editor(val media: Media) : Route
    data object Search : Route
    data object Settings : Route
    data object Trash : Route
    data object Videos : Route
    data class VaultViewer(val start: Int) : Route
    data class Virtual(val kind: String) : Route
    data class PickInto(val name: String) : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Main(Tab.Photos))
    /** The photos the viewer swipes through. */
    var viewerList: List<Media> = emptyList()

    val top get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun tab(t: Tab) { stack.clear(); stack.add(Route.Main(t)) }

    fun open(list: List<Media>, index: Int) {
        viewerList = list
        go(Route.Viewer(index))
    }

    /** A photo sent from another app ("Open with Gallery"). */
    fun openExternal(uri: Uri, mime: String, edit: Boolean) {
        val m = Media(
            id = -System.nanoTime(), uri = uri, name = uri.lastPathSegment ?: "Photo", mime = mime,
            date = System.currentTimeMillis(), size = 0, width = 0, height = 0, durationMs = 0,
            bucketId = -1, bucket = "", relativePath = "", isVideo = mime.startsWith("video"),
        )
        if (edit && !m.isVideo) go(Route.Editor(m)) else open(listOf(m), 0)
    }
}

private fun mediaPermissions(): Array<String> = arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION) + when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Full access, or only "selected photos" (Android 14+). */
fun hasFullAccess(c: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    else ContextCompat.checkSelfPermission(c, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

fun hasAnyAccess(c: android.content.Context): Boolean = hasFullAccess(c) ||
    (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

@Composable
fun rememberPermissionAsker(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return { launcher.launch(mediaPermissions()) }
}

@Composable
fun GalleryRoot() {
    val locked by AppLock.locked.collectAsState()
    val context = LocalContext.current
    var access by remember { mutableStateOf(hasAnyAccess(context)) }
    val ask = rememberPermissionAsker {
        access = hasAnyAccess(context)
        MediaRepo.refresh()
    }
    LaunchedEffect(access) { if (access) MediaRepo.refresh() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            locked -> LockScreen()
            !access -> Welcome(ask)
            else -> {
                BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }
                val route = Nav.top
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(150))
                    },
                    contentKey = { if (it is Route.Main) "main" else it.toString() },
                    label = "route",
                ) { r ->
                    when (r) {
                        is Route.Main -> MainScreen(r.tab, onAskAccess = ask)
                        is Route.AlbumView -> AlbumScreen(r.id, r.name)
                        is Route.Viewer -> ViewerScreen(Nav.viewerList, r.start)
                        is Route.Editor -> EditorScreen(r.media)
                        Route.Search -> SearchScreen()
                        Route.Settings -> SettingsScreen()
                        Route.Trash -> TrashScreen()
                        Route.Videos -> VideosScreen()
                        is Route.VaultViewer -> VaultViewer(r.start)
                        is Route.Virtual -> VirtualScreen(r.kind)
                        is Route.PickInto -> PickIntoScreen(r.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(ask: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(34.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp)) }
            Text("Your photos, beautifully", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                "Allow access to your photos and videos to get started. Everything stays on this phone — " +
                    "this app has no internet access at all.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Button(onClick = ask, modifier = Modifier.padding(top = 8.dp)) { Text("Allow access") }
        }
    }
}
