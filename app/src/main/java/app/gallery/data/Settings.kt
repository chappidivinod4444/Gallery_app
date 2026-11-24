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

/** Which folders show where. Hidden folders disappear from Photos and Albums (still on the phone, see "Hidden folders"). */
object Folders {
    private lateinit var p: SharedPreferences
    /** Hidden everywhere. */
    val hidden = MutableStateFlow<Set<Long>>(emptySet())
    /** Still an album, but its pictures stay out of the Photos timeline (e.g. WhatsApp, Screenshots). */
    val notInPhotos = MutableStateFlow<Set<Long>>(emptySet())
    /** Pinned to the top of Albums, in this order. */
    val pinned = MutableStateFlow<List<Long>>(emptyList())
    /** Also look for folders other apps hid from the gallery (.nomedia). Needs "All files access". */
    val showNomedia = MutableStateFlow(false)

    fun init(c: Context) {
        p = c.getSharedPreferences("folders", Context.MODE_PRIVATE)
        hidden.value = p.getString("hidden", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        notInPhotos.value = p.getString("notInPhotos", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        pinned.value = p.getString("pinned", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        showNomedia.value = p.getBoolean("nomedia", false)
    }

    private fun save() {
        p.edit()
            .putString("hidden", hidden.value.joinToString(","))
            .putString("notInPhotos", notInPhotos.value.joinToString(","))
            .putString("pinned", pinned.value.joinToString(","))
            .putBoolean("nomedia", showNomedia.value)
            .apply()
    }

    fun setVisible(id: Long, visible: Boolean) {
        hidden.value = if (visible) hidden.value - id else hidden.value + id
        if (!visible) pinned.value = pinned.value - id
        save(); MediaRepo.publish()
    }
    fun setVisible(ids: Collection<Long>, visible: Boolean) {
        hidden.value = if (visible) hidden.value - ids.toSet() else hidden.value + ids
        if (!visible) pinned.value = pinned.value - ids.toSet()
        save(); MediaRepo.publish()
    }
    fun setInPhotos(id: Long, inPhotos: Boolean) {
        notInPhotos.value = if (inPhotos) notInPhotos.value - id else notInPhotos.value + id
        save()
    }
    fun setPinned(id: Long, pin: Boolean) {
        pinned.value = if (pin) (pinned.value - id) + id else pinned.value - id
        save()
    }
    fun move(id: Long, by: Int) {
        val l = pinned.value.toMutableList()
        val i = l.indexOf(id); if (i < 0) return
        val j = (i + by).coerceIn(0, l.lastIndex)
        l.removeAt(i); l.add(j, id)
        pinned.value = l; save()
    }
    fun setShowNomedia(v: Boolean) { showNomedia.value = v; save(); MediaRepo.refresh(force = true) }
}
