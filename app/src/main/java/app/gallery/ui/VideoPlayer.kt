package app.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.gallery.data.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the video screen shows, kept up to date from the player. */
@Stable
class VideoState(val player: ExoPlayer, val uri: Uri) {
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var ended by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var speed by mutableFloatStateOf(1f)
    var muted by mutableStateOf(false)
    var loop by mutableStateOf(false)
    var fill by mutableStateOf(false)
    var aspect by mutableFloatStateOf(16f / 9f)
    /** Set whenever the person touches a control, so the controls stay up a bit longer. */
    var touched by mutableLongStateOf(0L)

    fun toggle() {
        touched = System.nanoTime()
        if (ended) { player.seekTo(0); ended = false; player.play(); return }
        if (player.isPlaying) player.pause() else player.play()
    }
    fun seekBy(ms: Long) { touched = System.nanoTime(); player.seekTo((player.currentPosition + ms).coerceIn(0, duration.coerceAtLeast(0))) ; ended = false }
    fun seekTo(ms: Long) { player.seekTo(ms.coerceIn(0, duration.coerceAtLeast(0))); position = ms; ended = false }
    fun setSpeedTo(s: Float) { speed = s; player.playbackParameters = PlaybackParameters(s) }
    fun toggleMute() { muted = !muted; player.volume = if (muted) 0f else 1f; touched = System.nanoTime() }
    fun toggleLoop() { loop = !loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; touched = System.nanoTime() }
}

/** Creates a player for [uri] (released when it leaves the screen) and pauses it when the app goes to the background. */
@OptIn(UnstableApi::class)
@Composable
fun rememberVideo(uri: Uri): VideoState {
    val ctx = LocalContext.current
    val state = remember(uri) {
        val p = ExoPlayer.Builder(ctx)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
        VideoState(p, uri)
    }
    DisposableEffect(state) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.playing = isPlaying }
            override fun onPlaybackStateChanged(s: Int) {
                state.ended = s == Player.STATE_ENDED
                if (s == Player.STATE_READY) state.duration = state.player.duration.coerceAtLeast(0)
            }
            override fun onPlayerError(error: PlaybackException) { state.error = "This video can't be played here" }
            override fun onVideoSizeChanged(v: VideoSize) {
                if (v.width > 0 && v.height > 0) state.aspect = v.width * v.pixelWidthHeightRatio / v.height
            }
        }
        state.player.addListener(l)
        onDispose { state.player.removeListener(l); state.player.release() }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, state) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) state.player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // Keep the time and progress moving smoothly.
    LaunchedEffect(state) {
        while (true) {
            val p = state.player
            state.position = p.currentPosition.coerceAtLeast(0)
            state.buffered = p.bufferedPosition.coerceAtLeast(0)
            if (p.duration > 0) state.duration = p.duration
            delay(if (state.playing) 120 else 400)
        }
    }
    // Screen stays on while a video plays.
    val view = LocalView.current
    DisposableEffect(state.playing) {
        view.keepScreenOn = state.playing
        onDispose { view.keepScreenOn = false }
    }
    return state
}

fun timeText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * The video picture with gestures:
 * tap = show/hide controls · double-tap left/right = 10 s back/forward · double-tap middle = play/pause ·
 * hold = 2× speed · slide up/down on the left = brightness, on the right = volume · pinch = fill / fit screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(v: VideoState, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val activity = ctx as? Activity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var seekFlash by remember { mutableIntStateOf(0) } // -n / +n taps of 10 s
    var seekFlashKey by remember { mutableLongStateOf(0L) }
    var fast by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf<Pair<Boolean, Float>?>(null) } // (isVolume, 0..1)
    var zoomHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekFlashKey) { if (seekFlash != 0) { delay(700); seekFlash = 0 } }
    LaunchedEffect(zoomHint) { if (zoomHint != null) { delay(900); zoomHint = null } }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = -1f } }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = v.player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.resizeMode = if (v.fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize()
                .pointerInput(v) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { o ->
                            when {
                                o.x < wPx * 0.35f -> { v.seekBy(-10_000); seekFlash = if (seekFlash < 0) seekFlash - 1 else -1; seekFlashKey = System.nanoTime() }
                                o.x > wPx * 0.65f -> { v.seekBy(10_000); seekFlash = if (seekFlash > 0) seekFlash + 1 else 1; seekFlashKey = System.nanoTime() }
                                else -> v.toggle()
                            }
                        },
                        onLongPress = {
                            if (v.player.isPlaying) { fast = true; v.player.playbackParameters = PlaybackParameters(2f) }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (fast) { fast = false; v.player.playbackParameters = PlaybackParameters(v.speed) }
                        },
                    )
                }
                .pointerInput(v) {
                    // Up/down slides: brightness (left half) or volume (right half). Pinch: fill or fit.
                    val slop = 18.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val isVol = down.position.x > wPx / 2f
                        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        var startLevel = if (isVol) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                        else (activity?.window?.attributes?.screenBrightness ?: -1f).let { if (it < 0f) 0.5f else it }
                        var dy = 0f; var dx = 0f
                        var vertical = false
                        var pinch = 1f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.count { it.pressed }
                            if (pressed == 0) break
                            if (pressed >= 2) {
                                pinch *= ev.calculateZoom()
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val c = ev.changes.first()
                            val d = c.position - c.previousPosition
                            dy += d.y; dx += d.x
                            if (!vertical && abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) vertical = true
                            if (vertical) {
                                c.consume()
                                val nv = (startLevel - dy / (hPx * 0.7f)).coerceIn(0f, 1f)
                                if (isVol) audio.setStreamVolume(AudioManager.STREAM_MUSIC, (nv * maxVol).roundToInt(), 0)
                                else activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = nv.coerceAtLeast(0.01f) } }
                                level = isVol to nv
                            }
                        }
                        if (pinch > 1.15f && !v.fill) { v.fill = true; zoomHint = "Fill screen" }
                        else if (pinch < 0.87f && v.fill) { v.fill = false; zoomHint = "Fit to screen" }
                        if (vertical) scope.launch { delay(600); level = null }
                    }
                },
        )

        // Double-tap seek bubbles (YouTube style)
        if (seekFlash != 0) {
            val left = seekFlash < 0
            Box(
                Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().width(with(density) { (wPx * 0.36f).toDp() })
                    .clip(if (left) RoundedCornerShape(topEnd = 400.dp, bottomEnd = 400.dp) else RoundedCornerShape(topStart = 400.dp, bottomStart = 400.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (left) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text("${abs(seekFlash) * 10} seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (fast) Pill(Modifier.align(Alignment.TopCenter).padding(top = 90.dp), "2×  ▸▸")
        zoomHint?.let { Pill(Modifier.align(Alignment.Center), it) }
        level?.let { (isVol, l) ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (isVol) (if (l == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp) else Icons.Rounded.Brightness6, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Canvas(Modifier.width(140.dp).height(4.dp)) {
                    drawRoundRect(Color(0x55FFFFFF), cornerRadius = CornerRadius(4f))
                    drawRoundRect(Color.White, size = Size(size.width * l, size.height), cornerRadius = CornerRadius(4f))
                }
                Spacer(Modifier.width(12.dp))
                Text("${(l * 100).roundToInt()}", color = Color.White, fontSize = 13.sp, modifier = Modifier.widthIn(min = 26.dp))
            }
        }
        v.error?.let { e ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(e, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Pill(modifier: Modifier, text: String) {
    Text(
        text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Bottom controls: frame preview while scrubbing, time | seek bar | length, then
 * speed · loop · back 10 · play/pause · forward 10 · sound · rotate.
 */
@Composable
fun VideoControls(v: VideoState, name: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scrub by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    val retriever = remember(v.uri) { runCatching { MediaMetadataRetriever().apply { setDataSource(ctx, v.uri) } }.getOrNull() }
    DisposableEffect(retriever) { onDispose { runCatching { retriever?.release() } } }
    LaunchedEffect(scrub) {
        val t = scrub ?: run { preview = null; return@LaunchedEffect }
        delay(60)
        val r = retriever ?: return@LaunchedEffect
        preview = withContext(Dispatchers.IO) {
            runCatching { r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 360) }.getOrNull()
        } ?: preview
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Seek bar with a frame preview that follows the finger.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barW = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            val s = scrub
            val pb = preview
            if (s != null && v.duration > 0) {
                val frac = s.toFloat() / v.duration
                val bubbleW = with(density) { 132.dp.toPx() }
                val x = (frac * barW - bubbleW / 2f).coerceIn(0f, barW - bubbleW)
                Column(
                    Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(132.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (pb != null) Image(
                        pb.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(132.dp).height(132.dp / v.aspect.coerceIn(0.5f, 2.4f)).clip(RoundedCornerShape(10.dp)).border(1.5.dp, Color.White, RoundedCornerShape(10.dp)),
                    )
                    Text(timeText(s), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeText(scrub ?: v.position), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(min = 44.dp))
            SeekBar(
                v.duration, scrub ?: v.position, v.buffered,
                onScrub = { scrub = it; v.touched = System.nanoTime() },
                onDone = { t -> v.seekTo(t); scrub = null },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Text(timeText(v.duration), color = Color(0xCCFFFFFF), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(min = 44.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
                CtrlText(if (v.speed == 1f) "1×" else "${v.speed}×".replace(".0×", "×")) { speedMenu = true }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    for (sp in listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)) DropdownMenuItem(
                        text = { Text((if (sp == 1f) "Normal" else "$sp×") + if (sp == v.speed) "  ✓" else "") },
                        onClick = { v.setSpeedTo(sp); speedMenu = false },
                    )
                }
            }
            CtrlIcon(if (v.loop) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Loop", on = v.loop) { v.toggleLoop() }
            CtrlIcon(Icons.Rounded.Replay10, "Back 10 seconds") { v.seekBy(-10_000) }
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Color.White).clickable { v.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when { v.ended -> Icons.Rounded.Replay; v.playing -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                    if (v.playing) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(34.dp),
                )
            }
            CtrlIcon(Icons.Rounded.Forward10, "Forward 10 seconds") { v.seekBy(10_000) }
            CtrlIcon(if (v.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Sound") { v.toggleMute() }
            RotateButton(v)
        }
    }
}

/** Saves the frame on screen as a new photo (Pictures/Edited). */
fun captureFrame(ctx: Context, v: VideoState, name: String, scope: kotlinx.coroutines.CoroutineScope) {
    v.player.pause()
    val at = v.player.currentPosition
    scope.launch {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever().apply { setDataSource(ctx, v.uri) }
                val b = r.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST); r.release()
                b?.let { Actions.saveBitmap(ctx, it, name.substringBeforeLast('.') + "_frame", null) }
            }.getOrNull()
        }
        Toast.makeText(ctx, if (uri != null) "Frame saved to Pictures/Edited" else "Couldn't save the frame", Toast.LENGTH_SHORT).show()
    }
}

/** Turns the screen sideways for wide videos (and back). */
@Composable
fun RotateButton(v: VideoState) {
    val activity = LocalContext.current as? Activity
    var landscape by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    CtrlIcon(Icons.Rounded.ScreenRotation, "Rotate", on = landscape) {
        landscape = !landscape
        activity?.requestedOrientation = if (landscape) {
            if (v.aspect >= 1f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        v.touched = System.nanoTime()
    }
}

@Composable
private fun CtrlIcon(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (on) Color(0x33FFFFFF) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun CtrlText(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** Thin seek bar that grows while you drag it; shows how much is loaded. */
@Composable
private fun SeekBar(duration: Long, position: Long, buffered: Long, onScrub: (Long) -> Unit, onDone: (Long) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    var last by remember { mutableLongStateOf(0L) }
    Canvas(
        modifier.height(28.dp).pointerInput(duration) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (duration <= 0) return@awaitEachGesture
                active = true
                fun at(x: Float) = ((x / size.width).coerceIn(0f, 1f) * duration).toLong()
                last = at(down.position.x); onScrub(last)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    last = at(c.position.x); onScrub(last); c.consume()
                }
                active = false
                onDone(last)
            }
        }
    ) {
        val h = if (active) 6.dp.toPx() else 3.5.dp.toPx()
        val y = size.height / 2f
        val r = CornerRadius(h / 2f)
        drawRoundRect(Color(0x40FFFFFF), Offset(0f, y - h / 2), Size(size.width, h), r)
        if (duration > 0) {
            drawRoundRect(Color(0x66FFFFFF), Offset(0f, y - h / 2), Size(size.width * (buffered.toFloat() / duration).coerceIn(0f, 1f), h), r)
            val x = size.width * (position.toFloat() / duration).coerceIn(0f, 1f)
            drawRoundRect(Color.White, Offset(0f, y - h / 2), Size(x, h), r)
            drawCircle(Color.White, if (active) 9.dp.toPx() else 6.5.dp.toPx(), Offset(x, y))
        }
    }
}

/** Stand-alone player with its own controls (used by the Vault). */
@Composable
fun VideoPlayerBox(uri: Uri, active: Boolean, onTap: () -> Unit, placeholder: @Composable () -> Unit) {
    if (!active) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() }; return }
    val v = rememberVideo(uri)
    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(controls, v.playing, v.touched) { if (controls && v.playing) { delay(3500); controls = false } }
    Box(Modifier.fillMaxSize()) {
        VideoSurface(v, onTap = { controls = !controls; onTap() })
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .navigationBarsPadding().padding(top = 24.dp, bottom = 44.dp),
            ) { VideoControls(v, uri.lastPathSegment ?: "video") }
        }
    }
}
