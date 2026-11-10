@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.wonderdesk.cinema

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.wonderdesk.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : WonderActivity() {
    @Composable override fun Content() = CinemaApp()
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incoming.value = intent }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (playing.value && autoPip.value) enterPip(this) }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); pip.value = isInPictureInPictureMode
    }
    companion object {
        val incoming = mutableStateOf<Intent?>(null)
        val pip = mutableStateOf(false)
        val playing = mutableStateOf(false)
        val autoPip = mutableStateOf(true)
        fun enterPip(a: Activity) { runCatching { a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } }
    }
}

private const val ABOUT = "Cinema plays every video on your phone: resume where you left off, swipe to seek or change brightness and volume, pick audio and subtitle tracks, " +
    "load your own subtitles, change speed, and keep watching in picture-in-picture. Its editor trims clips, removes sound, rotates, shrinks for sharing and saves still frames."

private fun timeText(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@Composable
fun CinemaApp() {
    val context = LocalContext.current
    var playingItem by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var editing by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val incoming by MainActivity.incoming
    LaunchedEffect(incoming) {
        val i = incoming ?: (context as ComponentActivity).intent
        MainActivity.incoming.value = null; (context as ComponentActivity).intent = Intent()
        i?.data?.let { playingItem = it to context.displayName(it) }
    }
    val p = playingItem; val e = editing
    when {
        e != null -> EditorScreen(e.first, e.second, onClose = { editing = null })
        p != null -> PlayerScreen(p.first, p.second, onClose = { playingItem = null }, onEdit = { editing = p; playingItem = null })
        else -> LibraryScreen(onPlay = { playingItem = it.uri to it.title }, onEdit = { editing = it.uri to it.title })
    }
}

/* ------------------------------------------------------- library -------------------------------------------- */

private val VIDEO_PERMISSION = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun LibraryScreen(onPlay: (Video) -> Unit, onEdit: (Video) -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, VIDEO_PERMISSION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> onPlay(Video(-1, u, context.displayName(u), 0, 0, 0, 0, "", 0, "")) } }
    var videos by remember { mutableStateOf<List<Video>?>(null) }
    var tab by rememberSaveable { mutableStateOf("all") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, tick) { if (granted) videos = withContext(Dispatchers.IO) { Videos.all(context) } }
    WonderApp(appName = "Cinema", about = ABOUT,
        tabs = listOf(WonderTab("all", "Videos", WonderIcons.VideoLibrary), WonderTab("folders", "Folders", WonderIcons.Folder)),
        selectedTab = tab, onTab = { tab = it; folder = null }, title = folder ?: "Cinema", onBack = folder?.let { { folder = null } },
        actions = { ToolbarButton(WonderIcons.FolderOpen, "Open a video file", { pick.launch(arrayOf("video/*")) }); ToolbarButton(WonderIcons.Refresh, "Refresh", { tick++ }) },
        settings = {
            var auto by remember { mutableStateOf(context.prefs("cinema").getBoolean("autoPip", true)) }
            Panel(title = "Playback") {
                SwitchRow("Picture-in-picture when leaving", auto, { auto = it; MainActivity.autoPip.value = it; context.prefs("cinema").edit().putBoolean("autoPip", it).apply() },
                    subtitle = "Keep watching in a small window", icon = WonderIcons.Layers, tint = Color(0xFF1E7FE8))
            }
        }) {
        if (!granted) {
            EmptyState("See all your videos", "Allow Cinema to read the videos on this phone to build your library. You can also open single files without it.",
                action = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WButton("Allow access", { ask.launch(VIDEO_PERMISSION) }, kind = ButtonKind.Primary, icon = WonderIcons.VideoLibrary)
                    WButton("Open a file", { pick.launch(arrayOf("video/*")) })
                } })
            return@WonderApp
        }
        val all = videos
        if (all == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }; return@WonderApp }
        if (all.isEmpty()) { EmptyState("No videos yet", "Videos you record or download appear here automatically.", action = { WButton("Open a file", { pick.launch(arrayOf("video/*")) }) }); return@WonderApp }
        if (tab == "folders" && folder == null) {
            val folders = all.groupBy { it.folder }.toList().sortedByDescending { it.second.maxOf { v -> v.added } }
            LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(folders, key = { it.first }) { (name, list) ->
                    Column(Modifier.clickable { folder = name }) {
                        Thumb(list.first(), Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text("${list.size} video${if (list.size == 1) "" else "s"} · ${formatBytes(list.sumOf { it.size })}", style = MaterialTheme.typography.labelSmall, color = p.secondaryText)
                    }
                }
            }
            return@WonderApp
        }
        val base = all.filter { (folder == null || it.folder == folder) && (q.isBlank() || it.title.contains(q, true)) }
        val shown = when (sort) { 1 -> base.sortedBy { it.title.lowercase() }; 2 -> base.sortedByDescending { it.duration }; 3 -> base.sortedByDescending { it.size }; else -> base }
        val recent = remember(tick, all) { Resume.recent(context).mapNotNull { (u, _, _) -> all.find { it.uri == u } }.take(10) }
        LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchField(q, { q = it }, "Search ${all.size} videos")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Newest", "Name", "Longest", "Largest").forEachIndexed { i, s -> Chip(s, sort == i, { sort = i }) }
                    }
                }
            }
            if (recent.isNotEmpty() && folder == null && q.isBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionLabel("Continue watching", Modifier.padding(start = 6.dp, bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(recent, key = { "r" + it.id }) { v -> Column(Modifier.width(200.dp).clickable { onPlay(v) }) {
                                Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                                Text(v.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            } }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(if (folder != null) folder!! else "All videos", Modifier.padding(start = 6.dp)) }
            }
            items(shown, key = { it.id }) { v -> VideoCard(v, onPlay = { onPlay(v) }, onEdit = { onEdit(v) }) }
        }
    }
}

@Composable
private fun Thumb(v: Video, modifier: Modifier) {
    val context = LocalContext.current
    var bmp by remember(v.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(v.id) { bmp = withContext(Dispatchers.IO) { Videos.thumb(context, v) } }
    val progress = remember(v.uri) { Resume.progress(context, v.uri) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1D21))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (v.duration > 0) Text(timeText(v.duration), Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .7f)).padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
        if (progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .3f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFFE5483C))) }
    }
}

@Composable
private fun VideoCard(v: Video, onPlay: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.clickable(onClick = onPlay)) {
        Thumb(v, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(if (v.height > 0) "${minOf(v.width, v.height)}p" else null, formatBytes(v.size), relativeTime(v.added)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = p.secondaryText)
            }
            Box {
                Icon(WonderIcons.MoreVert, "Options", Modifier.size(20.dp).clickable { menu = true }, tint = p.secondaryText)
                DropdownMenu(menu, { menu = false }, Modifier.background(p.panel)) {
                    DropdownMenuItem({ Text("Play") }, { menu = false; onPlay() }, leadingIcon = { Icon(WonderIcons.PlayArrow, null) })
                    DropdownMenuItem({ Text("Edit or trim") }, { menu = false; onEdit() }, leadingIcon = { Icon(WonderIcons.ContentCut, null) })
                    DropdownMenuItem({ Text("Share") }, { menu = false; context.share(v.uri, "video/*") {} }, leadingIcon = { Icon(WonderIcons.Share, null) })
                    DropdownMenuItem({ Text("Show in Wayfinder") }, { menu = false
                        val path = "/storage/emulated/0/" + v.path + v.title
                        context.launchSafely(Intent("dev.wonderdesk.wayfinder.SHOW").setPackage("dev.wonderdesk.wayfinder").putExtra("path", path)) {} }, leadingIcon = { Icon(WonderIcons.FolderOpen, null) })
                }
            }
        }
    }
}

/* -------------------------------------------------------- player -------------------------------------------- */

@Composable
private fun PlayerScreen(uri: Uri, title: String, onClose: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var subtitle by remember { mutableStateOf<Uri?>(null) }
    val player = remember {
        ExoPlayer.Builder(context).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true
            val resume = Resume.get(context, uri); if (resume > 0) seekTo(resume)
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var loop by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity.window.attributes.screenBrightness.takeIf { it >= 0 } ?: .5f) }
    val inPip by MainActivity.pip
    MainActivity.autoPip.value = context.prefs("cinema").getBoolean("autoPip", true)
    DisposableEffect(Unit) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(p: Boolean) { isPlaying = p; MainActivity.playing.value = p } }
        player.addListener(l)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            Resume.set(context, uri, player.currentPosition, player.duration)
            player.removeListener(l); player.release(); MainActivity.playing.value = false
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ctl.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); if (pos > 0 && pos / 1000 % 10 == 0L) Resume.set(context, uri, pos, dur); delay(250) } }
    LaunchedEffect(controls, isPlaying, sheet) { if (controls && isPlaying && sheet == null) { delay(3500); controls = false } }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }
    LaunchedEffect(subtitle) {
        val s = subtitle ?: return@LaunchedEffect
        val at = player.currentPosition
        val cfg = MediaItem.SubtitleConfiguration.Builder(s).setMimeType(if (context.displayName(s).endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        player.setMediaItem(MediaItem.Builder().setUri(uri).setSubtitleConfigurations(listOf(cfg)).build(), at); player.prepare()
    }
    val subPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { subtitle = it }
    BackHandler { if (locked) hud = "Unlock first" else onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ ctx -> PlayerView(ctx).apply { useController = false; this.player = player; setShutterBackgroundColor(android.graphics.Color.BLACK) } }, Modifier.fillMaxSize(),
            update = { it.resizeMode = resize })
        if (inPip) return@Box
        // Gesture layer: tap toggles controls, double-tap seeks, drags change position/brightness/volume, pinch fills.
        Box(Modifier.fillMaxSize()
            .pointerInput(locked) {
                detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { o ->
                    if (locked) return@detectTapGestures
                    when { o.x < size.width / 3 -> { player.seekBack(); hud = "−10 s" }; o.x > size.width * 2 / 3 -> { player.seekForward(); hud = "+10 s" }
                        else -> { if (player.isPlaying) player.pause() else player.play() } }
                })
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                var mode = 0; var startPos = 0L; var acc = 0f
                detectDragGestures(onDragStart = { o -> mode = 0; acc = 0f; startPos = player.currentPosition; mode = if (o.x < size.width / 2) -1 else 1 },
                    onDrag = { ch, d ->
                        if (mode == -1 || mode == 1) { if (kotlin.math.abs(d.x) > kotlin.math.abs(d.y) * 1.5f) mode = 2 else if (kotlin.math.abs(d.y) > 2) mode = if (mode == -1) 3 else 4 }
                        when (mode) {
                            2 -> { acc += d.x; val target = (startPos + (acc / size.width * 120_000).toLong()).coerceIn(0, dur); player.seekTo(target); hud = "${timeText(target)} / ${timeText(dur)}" }
                            3 -> { brightness = (brightness - d.y / size.height).coerceIn(.02f, 1f); activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }; hud = "☀ ${(brightness * 100).toInt()}%" }
                            4 -> { acc -= d.y; val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val step = size.height / (max * 1.5f)
                                if (kotlin.math.abs(acc) > step) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (acc > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0); acc = 0f }
                                hud = "🔊 ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%" }
                        }
                        ch.consume()
                    })
            }
            .pointerInput(locked) { if (!locked) detectTransformGestures { _, _, zoom, _ -> if (zoom > 1.05f) { resize = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; hud = "Fill screen" } else if (zoom < .95f) { resize = AspectRatioFrameLayout.RESIZE_MODE_FIT; hud = "Fit" } } })
        hud?.let { Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .6f)).padding(16.dp, 10.dp), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        AnimatedVisibility(controls, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                if (locked) {
                    IconButton(onClick = { locked = false; hud = "Unlocked" }, Modifier.align(Alignment.CenterStart).padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f))) { Icon(WonderIcons.Lock, "Unlock", tint = Color.White) }
                    return@Box
                }
                // top bar
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent))).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(WonderIcons.ArrowBack, "Back", tint = Color.White) }
                    Text(title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { sheet = "tracks" }) { Icon(WonderIcons.Translate, "Audio and subtitles", tint = Color.White) }
                    IconButton(onClick = { sheet = "speed" }) { Text("${if (speed % 1 == 0f) speed.toInt() else speed}×", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                    IconButton(onClick = { MainActivity.enterPip(activity) }) { Icon(WonderIcons.Layers, "Picture in picture", tint = Color.White) }
                    IconButton(onClick = { sheet = "more" }) { Icon(WonderIcons.MoreVert, "More", tint = Color.White) }
                }
                // centre transport
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(WonderIcons.Replay10, "Back 10 seconds", 52) { player.seekBack() }
                    RoundBtn(if (isPlaying) WonderIcons.Pause else WonderIcons.PlayArrow, if (isPlaying) "Pause" else "Play", 72) { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
                    RoundBtn(WonderIcons.Forward10, "Forward 10 seconds", 52) { player.seekForward() }
                }
                // bottom bar
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))).navigationBarsPadding().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeText(pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Slider(if (dur > 0) pos.toFloat() / dur else 0f, { player.seekTo((it * dur).toLong()); pos = (it * dur).toLong() }, Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE5483C), inactiveTrackColor = Color.White.copy(alpha = .3f)))
                        Text("-" + timeText(dur - pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { locked = true; controls = false; hud = "Locked" }) { Icon(WonderIcons.Lock, "Lock controls", tint = Color.White) }
                        IconButton(onClick = { loop = !loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; hud = if (loop) "Loop on" else "Loop off" }) {
                            Icon(WonderIcons.Repeat, "Loop", tint = if (loop) Color(0xFFE5483C) else Color.White) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { resize = when (resize) { AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                            hud = when (resize) { AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill screen"; AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"; else -> "Fit" } }) { Icon(WonderIcons.Crop, "Aspect", tint = Color.White) }
                        IconButton(onClick = { landscape = !landscape; activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }) {
                            Icon(WonderIcons.RotateRight, "Rotate", tint = Color.White) }
                        IconButton(onClick = onEdit) { Icon(WonderIcons.ContentCut, "Edit", tint = Color.White) }
                    }
                }
            }
        }
    }
    when (sheet) {
        "speed" -> WonderDialog(onDismissRequest = { sheet = null }, title = { Text("Playback speed") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${"%.2f".format(speed)}×", style = MaterialTheme.typography.headlineSmall)
                Slider(speed, { speed = (it * 20).toInt() / 20f; player.playbackParameters = PlaybackParameters(speed) }, valueRange = .25f..3f)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { s -> Chip("${s}×", speed == s, { speed = s; player.playbackParameters = PlaybackParameters(s) }) }
                }
            }
        }, confirmButton = { TextButton(onClick = { sheet = null }) { Text("Done") } })
        "tracks" -> TrackSheet(player, onLoadSubs = { sheet = null; subPicker.launch(arrayOf("application/x-subrip", "text/*", "*/*")) }) { sheet = null }
        "more" -> WonderDialog(onDismissRequest = { sheet = null }, title = { Text(title) }, text = {
            Panel {
                ListRow("Save this frame as a picture", icon = WonderIcons.PhotoCamera, tint = Color(0xFF2E9E44), onClick = {
                    sheet = null
                    val at = player.currentPosition
                    kotlinx.coroutines.MainScope().launch {
                        val r = withContext(Dispatchers.IO) { runCatching {
                            val bmp = Videos.frame(context, uri, at) ?: error("Couldn't read that frame")
                            val f = File(context.cacheDir, "frame.png"); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            Videos.publish(context, f, title.substringBeforeLast('.') + " " + timeText(at).replace(':', '.') + ".png", image = true)
                        } }
                        hud = if (r.isSuccess) "Saved to Pictures/Cinema" else "Couldn't save the frame"
                    }
                })
                PanelDivider()
                ListRow("Share video", icon = WonderIcons.Share, tint = Color(0xFF1E7FE8), onClick = { sheet = null; context.share(uri, "video/*") {} })
            }
        }, confirmButton = { TextButton(onClick = { sheet = null }) { Text("Close") } })
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.Black.copy(alpha = .45f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * .55f).dp))
    }
}

@Composable
private fun TrackSheet(player: ExoPlayer, onLoadSubs: () -> Unit, onDismiss: () -> Unit) {
    val groups = player.currentTracks.groups
    fun langName(code: String?) = code?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifBlank { it } } ?: "Unknown"
    WonderDialog(onDismissRequest = onDismiss, title = { Text("Audio and subtitles") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(C.TRACK_TYPE_AUDIO to "Audio", C.TRACK_TYPE_TEXT to "Subtitles").forEach { (type, label) ->
                val g = groups.filter { it.type == type }
                Panel(title = label) {
                    if (type == C.TRACK_TYPE_TEXT) {
                        val off = g.none { it.isSelected }
                        ListRow("Off", selected = off, onClick = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); onDismiss() })
                    }
                    g.forEach { grp ->
                        for (i in 0 until grp.length) {
                            val f = grp.getTrackFormat(i)
                            PanelDivider(16.dp)
                            ListRow(f.label ?: langName(f.language), subtitle = listOfNotNull(f.sampleMimeType?.substringAfter('/'), if (f.channelCount > 0) "${f.channelCount} ch" else null).joinToString(" · "),
                                selected = grp.isTrackSelected(i), onClick = {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, false)
                                        .setOverrideForType(TrackSelectionOverride(grp.mediaTrackGroup, i)).build(); onDismiss()
                                })
                        }
                    }
                    if (g.isEmpty() && type == C.TRACK_TYPE_AUDIO) ListRow("No other audio tracks")
                    if (type == C.TRACK_TYPE_TEXT) { PanelDivider(0.dp); ListRow("Load subtitle file (.srt, .vtt)…", icon = WonderIcons.UploadFile, tint = Color(0xFF8E44AD), onClick = onLoadSubs) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}


/* -------------------------------------------------------- editor -------------------------------------------- */

@Composable
private fun EditorScreen(uri: Uri, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val p = wonderPalette()
    val player = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare() } }
    var dur by remember { mutableLongStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }
    var mute by remember { mutableStateOf(false) }
    var rotate by remember { mutableIntStateOf(0) }
    var size by remember { mutableIntStateOf(0) } // 0 original, 1 1080p, 2 720p, 3 480p
    var progress by remember { mutableStateOf<Int?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val strip = remember { mutableStateListOf<android.graphics.Bitmap>() }
    val toastRef = remember { mutableStateOf<(String) -> Unit>({}) }
    DisposableEffect(Unit) { val l = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }; player.addListener(l); onDispose { player.release(); Editor.cancel() } }
    LaunchedEffect(Unit) {
        while (dur <= 0) { dur = player.duration.takeIf { it > 0 } ?: 0; delay(100) }
        withContext(Dispatchers.IO) { (0 until 8).forEach { i -> Videos.frame(context, uri, dur * i / 8)?.let { b -> android.graphics.Bitmap.createScaledBitmap(b, 160, (160f * b.height / b.width).toInt().coerceAtLeast(1), true).let { s -> withContext(Dispatchers.Main) { strip += s } } } } }
    }
    LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; if (player.isPlaying && pos >= (range.endInclusive * dur).toLong()) { player.pause(); player.seekTo((range.start * dur).toLong()) }; delay(100) } }
    WonderApp(appName = "Cinema", about = ABOUT, title = "Edit video", subtitle = title, onBack = onClose) {
        toastRef.value = LocalToast.current
        Page {
            Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                AndroidView({ PlayerView(it).apply { useController = false; this.player = player } }, Modifier.fillMaxSize().graphicsLayer { rotationZ = rotate.toFloat() })
                RoundBtnSmall(if (playing) WonderIcons.Pause else WonderIcons.PlayArrow, Modifier.align(Alignment.Center)) {
                    if (player.isPlaying) player.pause() else { if (pos < (range.start * dur) || pos >= (range.endInclusive * dur)) player.seekTo((range.start * dur).toLong()); player.play() }
                }
            }
            Panel(title = "Trim") {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp))) {
                        strip.forEach { Image(it.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) }
                        if (strip.isEmpty()) Box(Modifier.fillMaxSize().background(p.separator))
                    }
                    RangeSlider(range, { r -> val moved = if (r.start != range.start) r.start else r.endInclusive; range = r; player.seekTo((moved * dur).toLong()) }, colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent))
                    Row { Text(timeText((range.start * dur).toLong()), style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f))
                        Text("Length " + timeText(((range.endInclusive - range.start) * dur).toLong()), style = MaterialTheme.typography.labelMedium, color = p.accent); Spacer(Modifier.weight(1f))
                        Text(timeText((range.endInclusive * dur).toLong()), style = MaterialTheme.typography.labelMedium) }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WButton("Start here", { if (dur > 0) range = (pos.toFloat() / dur).coerceAtMost(range.endInclusive - .01f)..range.endInclusive }, kind = ButtonKind.Plain)
                        WButton("End here", { if (dur > 0) range = range.start..(pos.toFloat() / dur).coerceAtLeast(range.start + .01f) }, kind = ButtonKind.Plain)
                    }
                }
            }
            Panel(title = "Adjust") {
                SwitchRow("Remove sound", mute, { mute = it }, icon = WonderIcons.VolumeUp, tint = Color(0xFFE5483C))
                PanelDivider()
                ListRow("Rotate", subtitle = if (rotate == 0) "Original" else "$rotate°", icon = WonderIcons.RotateRight, tint = Color(0xFF8E44AD), trailing = {
                    Row { IconButton(onClick = { rotate = (rotate + 270) % 360 }) { Icon(WonderIcons.RotateLeft, "Rotate left") }; IconButton(onClick = { rotate = (rotate + 90) % 360 }) { Icon(WonderIcons.RotateRight, "Rotate right") } }
                })
                PanelDivider()
                Column(Modifier.padding(12.dp)) {
                    Text("Size", style = MaterialTheme.typography.titleSmall)
                    Segmented(listOf("Original", "1080p", "720p", "480p"), size) { size = it }
                    Text("Smaller sizes are quicker to send.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText, modifier = Modifier.padding(top = 4.dp))
                }
            }
            WButton(if (progress != null) "Saving… ${progress!!.coerceAtLeast(0)}%" else "Save as new video", {
                val spec = EditSpec((range.start * dur).toLong(), (range.endInclusive * dur).toLong(), mute, rotate, listOf(null, 1080, 720, 480)[size])
                val out = File(context.cacheDir, "edit.mp4")
                progress = 0
                val poll = Editor.export(context, uri, spec, out) { r ->
                    kotlinx.coroutines.MainScope().launch {
                        val saved = r.mapCatching { withContext(Dispatchers.IO) { Videos.publish(context, it, title.substringBeforeLast('.') + " (edited).mp4", image = false) } }
                        progress = null
                        toastRef.value(saved.fold({ "Saved to Movies/Cinema" }, { "Couldn't export: ${it.message}" }))
                    }
                }
                kotlinx.coroutines.MainScope().launch { while (progress != null) { progress = poll().takeIf { it >= 0 } ?: progress; delay(300) } }
            }, Modifier.fillMaxWidth(), icon = WonderIcons.Save, kind = ButtonKind.Primary, enabled = progress == null && dur > 0)
            if (progress != null) WButton("Cancel export", { Editor.cancel(); progress = null }, kind = ButtonKind.Plain)
        }
    }
}


@Composable
private fun RoundBtnSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
}
