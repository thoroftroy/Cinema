@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.wonderdesk.shared

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoItem(val uri: Uri, val name: String, val durationMs: Long, val size: Long)

fun listDeviceVideos(context: Context, limit: Int = 200): List<VideoItem> {
    val out = mutableListOf<VideoItem>()
    runCatching {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE),
            null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext() && out.size < limit) {
                out += VideoItem(
                    android.content.ContentUris.withAppendedId(collection, c.getLong(id)),
                    c.getString(name) ?: "Video",
                    c.getLong(dur),
                    c.getLong(size)
                )
            }
        }
    }
    return out
}

@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("video", Context.MODE_PRIVATE) }
    val op = rememberOperation()
    var library by rememberSaveable { mutableStateOf(true) }
    var videos by remember { mutableStateOf(listOf<VideoItem>()) }
    var fullscreen by remember { mutableStateOf(false) }
    var uriText by rememberSaveable { mutableStateOf(activity.intent.data?.toString() ?: prefs.getString("last", null)) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("speed", 1f)) }
    var repeat by remember { mutableStateOf(prefs.getBoolean("repeat", false)) }
    var muted by remember { mutableStateOf(prefs.getBoolean("muted", false)) }
    var loadedUri by remember { mutableStateOf<String?>(null) }
    val currentUri by rememberUpdatedState(loadedUri)
    val player = remember { ExoPlayer.Builder(context).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true).setHandleAudioBecomingNoisy(true).build() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value ->
        value?.let { context.keepRead(it); uriText = it.toString(); prefs.edit().putString("last", uriText).apply(); library = false }
    }
    fun loadLibrary() { op.run("Reading videos", { listDeviceVideos(context) }) { videos = it; op.notice("${it.size} videos on this device.") } }
    LaunchedEffect(library) { if (library) loadLibrary() }
    LaunchedEffect(uriText) {
        loadedUri?.let { previous ->
            if (previous != uriText) prefs.edit().putLong("position:$previous", player.currentPosition).apply()
        }
        uriText?.let {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(it)), prefs.getLong("position:$it", 0L))
            player.setPlaybackSpeed(speed)
            player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.volume = if (muted) 0f else 1f
            player.prepare()
            loadedUri = it
            op.notice("Video loaded. Tap play in the video controls. Codec support depends on the device.")
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { op.notice("Playback failed: ${error.errorCodeName}. File may be unsupported, missing or access revoked.") }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                currentUri?.let { prefs.edit().putLong("position:$it", player.currentPosition).apply() }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            currentUri?.let { prefs.edit().putLong("position:$it", player.currentPosition).apply() }
            activity.lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    // Fullscreen: hide system bars while in the player, keep them in the library.
    LaunchedEffect(fullscreen) {
        val window = activity.window
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        if (fullscreen) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    if (!fullscreen) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { library = true }, modifier = Modifier.weight(1f)) { Text(if (library) "✓ Library" else "Library") }
            TextButton(onClick = { library = false }, modifier = Modifier.weight(1f)) { Text(if (!library) "✓ Player" else "Player") }
        }
    }
    if (library) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AquaButton(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Open video") }
                AquaSecondaryButton(onClick = { loadLibrary() }, enabled = !op.busy) { Text("Refresh") }
            }
            OperationStatus(op)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(videos, key = { it.uri.toString() }) { video ->
                    Card(onClick = { uriText = video.uri.toString(); prefs.edit().putString("last", video.uri.toString()).apply(); library = false }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            var thumb by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                            LaunchedEffect(video.uri) { thumb = withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(video.uri, android.util.Size(160, 90), null) }.getOrNull() } }
                            thumb?.let { Image(it.asImageBitmap(), null, Modifier.size(width = 80.dp, height = 45.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(video.name)
                                Text("${video.durationMs / 1000}s · ${android.text.format.Formatter.formatShortFileSize(context, video.size)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!fullscreen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AquaButton(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Open video") }
                    AquaSecondaryButton(onClick = { uriText?.let { context.share(Uri.parse(it), "video/*", op::notice) } }, enabled = uriText != null) { Text("Share") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { PlayerView(it).apply { this.player = player; setShowSubtitleButton(true); setShowNextButton(false); setShowPreviousButton(false) } }, modifier = Modifier.fillMaxSize())
                if (!fullscreen) TextButton(onClick = { fullscreen = true }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).background(Color.Black.copy(alpha = 0.55f))) { Text("⛶", color = Color.White) }
            }
            if (!fullscreen) {
                OperationStatus(op)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { value ->
                        TextButton(onClick = { speed = value; player.setPlaybackSpeed(value); prefs.edit().putFloat("speed", value).apply() }) { Text(if (speed == value) "• ${value}×" else "${value}×") }
                    }
                }
                TextButton(onClick = {
                    repeat = !repeat
                    player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    prefs.edit().putBoolean("repeat", repeat).apply()
                }) { Text(if (repeat) "Repeat: On" else "Repeat: Off") }
                TextButton(onClick = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                    prefs.edit().putBoolean("muted", muted).apply()
                }) { Text(if (muted) "Unmute" else "Mute") }
                Text("Library lists the videos Android has indexed on the phone. Fullscreen hides the bars; swipe from an edge to bring them back. No background video or clipping yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Box(Modifier.fillMaxSize()) {
                    TextButton(onClick = { fullscreen = false }, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd).background(Color.Black.copy(alpha = 0.55f))) { Text("Exit fullscreen", color = Color.White) }
                }
            }
        }
    }
}
