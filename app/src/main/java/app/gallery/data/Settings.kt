package app.gallery.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** App settings, kept on the phone only. */
object Settings {
    private lateinit var p: SharedPreferences

    enum class ThemeMode { System, Light, Dark }

    val theme = MutableStateFlow(ThemeMode.System)
    val dynamicColor = MutableStateFlow(true)
    val amoled = MutableStateFlow(false)
    val appLock = MutableStateFlow(false)
    /** Seconds away from the app before it locks again (0 = right away). */
    val lockAfter = MutableStateFlow(0)
    val secureScreen = MutableStateFlow(false)
    val columns = MutableStateFlow(4)
    val showVideosInPhotos = MutableStateFlow(true)

    fun init(c: Context) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        theme.value = runCatching { ThemeMode.valueOf(p.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System)
        dynamicColor.value = p.getBoolean("dynamic", true)
        amoled.value = p.getBoolean("amoled", false)
        appLock.value = p.getBoolean("lock", false)
        lockAfter.value = p.getInt("lockAfter", 0)
        secureScreen.value = p.getBoolean("secure_v2", false)
        columns.value = p.getInt("columns", 4)
        showVideosInPhotos.value = p.getBoolean("videosInPhotos", true)
    }

    fun setTheme(v: ThemeMode) { theme.value = v; p.edit().putString("theme", v.name).apply() }
    fun setDynamic(v: Boolean) { dynamicColor.value = v; p.edit().putBoolean("dynamic", v).apply() }
    fun setAmoled(v: Boolean) { amoled.value = v; p.edit().putBoolean("amoled", v).apply() }
    fun setAppLock(v: Boolean) { appLock.value = v; p.edit().putBoolean("lock", v).apply() }
    fun setLockAfter(v: Int) { lockAfter.value = v; p.edit().putInt("lockAfter", v).apply() }
    fun setSecure(v: Boolean) { secureScreen.value = v; p.edit().putBoolean("secure_v2", v).apply() }
    fun setColumns(v: Int) { columns.value = v.coerceIn(2, 15); p.edit().putInt("columns", columns.value).apply() }
    fun setVideosInPhotos(v: Boolean) { showVideosInPhotos.value = v; p.edit().putBoolean("videosInPhotos", v).apply() }
}

/** Favourites, kept by this app (instant, no system pop-up each time). */
object Favorites {
    private lateinit var p: SharedPreferences
    val ids = MutableStateFlow<Set<Long>>(emptySet())

    fun init(c: Context) {
        p = c.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        ids.value = p.getStringSet("ids", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun isFav(id: Long) = id in ids.value

    fun toggle(items: Collection<Long>) {
        val allFav = items.all { it in ids.value }
        ids.value = if (allFav) ids.value - items.toSet() else ids.value + items
        p.edit().putStringSet("ids", ids.value.map { it.toString() }.toSet()).apply()
    }
}
