package app.gallery

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.gallery.data.Favorites
import app.gallery.data.MediaRepo
import app.gallery.data.Settings
import app.gallery.data.ThumbFetcher
import app.gallery.data.ThumbKeyer
import app.gallery.data.Vault
import app.gallery.data.VaultThumbFetcher
import app.gallery.data.VaultThumbKeyer
import app.gallery.ui.AppLock
import app.gallery.ui.GalleryRoot
import app.gallery.ui.GalleryTheme
import app.gallery.ui.Nav
import coil3.SingletonImageLoader
import coil3.request.crossfade
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GalleryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Favorites.init(this)
        app.gallery.data.Folders.init(this)
        MediaRepo.init(this)
        Thread { app.gallery.data.ThumbCache.trim(this) }.start()
        Vault.init(this)
        SingletonImageLoader.setSafe { ctx ->
            coil3.ImageLoader.Builder(ctx)
                .components {
                    add(ThumbFetcher.Factory(ctx))
                    add(ThumbKeyer())
                    add(VaultThumbFetcher.Factory())
                    add(VaultThumbKeyer())
                }
                // Pictures appear immediately (no fade), and a good share of memory keeps scrolling smooth.
                .crossfade(false)
                .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(ctx, 0.3).build() }
                .build()
        }
        AppLock.onAppStart()
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryTheme { GalleryRoot() } }
        handle(intent)
        // Screenshots are allowed everywhere, except: on Vault screens, or when the person turns on
        // "Hide content in recent apps" in Settings.
        lifecycleScope.launch {
            combine(Settings.secureScreen, app.gallery.ui.SecureZone.count) { secure, zones -> secure || zones > 0 }
                .collect { on ->
                    if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> Nav.openExternal(uri, intent.type ?: contentResolver.getType(uri) ?: "image/*", edit = false)
            Intent.ACTION_EDIT -> Nav.openExternal(uri, intent.type ?: contentResolver.getType(uri) ?: "image/*", edit = true)
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onReturn()
        MediaRepo.refresh()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            AppLock.onLeave()
            // The Vault always closes when you leave the app.
            Vault.lock()
            SingletonImageLoader.get(this).memoryCache?.let { cache ->
                cache.keys.filter { it.key.startsWith("vault:") }.forEach { cache.remove(it) }
            }
        }
    }
}
