package dev.wonderdesk.cinema

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.wonderdesk.shared.*

private fun neededPermissions(c: Context, cfg: CaptureConfig): List<String> {
    val out = mutableListOf<String>()
    if (cfg.audio != 0 && ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) out += Manifest.permission.RECORD_AUDIO
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) out += Manifest.permission.POST_NOTIFICATIONS
    return out
}

/** The Record tab: quality, frame rate, sound, and clip mode, then one big button. */
@Composable
fun RecorderScreen() {
    val c = LocalContext.current
    val p = wonderPalette()
    var cfg by remember { mutableStateOf(CaptureConfig.load(c)) }
    fun change(n: CaptureConfig) { cfg = n; n.save(c) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == Activity.RESULT_OK && d != null) RecorderService.start(c, r.resultCode, d)
        else RecorderState.message = "Recording was not allowed"
    }
    fun askScreen() = capture.launch(c.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { askScreen() }
    fun go() {
        val need = neededPermissions(c, cfg)
        if (need.isEmpty()) askScreen() else perms.launch(need.toTypedArray())
    }
    val running = RecorderState.running
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel(title = if (running) (if (RecorderState.clipMode) "Clip mode is on" else "Recording the screen") else "Record the screen") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    RecorderState.savingNow -> LoadingStatus("Saving your recording", detail = "Finishing the file and adding it to your library")
                    running && RecorderState.clipMode && !RecorderState.writing -> {
                        Text("Nothing is being saved. Cinema only keeps the last ${cfg.bufferSec} seconds in memory and throws older footage away.", color = p.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        WButton("Clip the last ${cfg.bufferSec} seconds and keep recording", { RecorderService.send(c, RecorderService.ACTION_CLIP) }, kind = ButtonKind.Primary, icon = WonderIcons.Videocam)
                        WButton("Turn clip mode off", { RecorderService.send(c, RecorderService.ACTION_STOP) }, icon = WonderIcons.Stop)
                    }
                    running && RecorderState.clipMode -> {
                        Text("Recording a clip that started ${cfg.bufferSec} seconds before you tapped. Stop when you have what you need. It saves at once and the silent buffer starts again.", color = p.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        WButton("Stop and save the clip", { RecorderService.send(c, RecorderService.ACTION_END_CLIP) }, kind = ButtonKind.Primary, icon = WonderIcons.Save)
                        WButton("Turn clip mode off", { RecorderService.send(c, RecorderService.ACTION_STOP) }, icon = WonderIcons.Stop)
                    }
                    running -> {
                        Text("Everything on the screen is being recorded. Use the notification or this button to stop.", color = p.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        WButton("Stop and save", { RecorderService.send(c, RecorderService.ACTION_STOP) }, kind = ButtonKind.Primary, icon = WonderIcons.Stop)
                    }
                    else -> {
                        Text(if (cfg.clipMode) "Clip mode watches the screen without saving anything. When something worth keeping happens, tap Clip and the last ${cfg.bufferSec} seconds are saved and recording continues until you stop."
                        else "Records everything on the screen, even other apps, calls and videos, at the quality you pick below.", color = p.secondaryText, style = MaterialTheme.typography.bodyMedium)
                        WButton(if (cfg.clipMode) "Start clip mode" else "Start recording", { go() }, kind = ButtonKind.Primary, icon = WonderIcons.Videocam)
                    }
                }
                if (RecorderState.message.isNotBlank()) Text(RecorderState.message, color = p.accent, style = MaterialTheme.typography.bodySmall)
                RecorderState.lastSaved?.let { u ->
                    Text("${RecorderState.saved} saved this session", color = p.secondaryText, style = MaterialTheme.typography.bodySmall)
                    WButton("Watch the last one", { c.startActivity(Intent(c, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(u, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }, icon = WonderIcons.Movie)
                }
            }
        }
        Panel(title = "Mode", footer = "Clip mode never saves anything on its own. It is always recording into a short memory buffer and only keeps what you clip.") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Record", !cfg.clipMode, { change(cfg.copy(clipMode = false)) }, WonderIcons.Videocam)
                Chip("Clip mode", cfg.clipMode, { change(cfg.copy(clipMode = true)) }, WonderIcons.ContentCut)
            }
            if (cfg.clipMode) {
                PanelDivider()
                Text("How far back a clip reaches", Modifier.padding(start = 16.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { s -> Chip("$s s", cfg.bufferSec == s, { change(cfg.copy(bufferSec = s)) }) }
                }
            }
        }
        Panel(title = "Quality") {
            Text("Picture size", Modifier.padding(start = 16.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100 to "Full", 75 to "Medium", 50 to "Small").forEach { (v, l) -> Chip(l, cfg.scalePct == v, { change(cfg.copy(scalePct = v)) }) }
            }
            PanelDivider()
            Text("Frames per second", Modifier.padding(start = 16.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 30, 60).forEach { f -> Chip("$f", cfg.fps == f, { change(cfg.copy(fps = f)) }) }
            }
            PanelDivider()
            Text("Detail (data per second)", Modifier.padding(start = 16.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4 to "Light", 8 to "Normal", 16 to "High", 32 to "Best").forEach { (v, l) -> Chip(l, cfg.mbps == v, { change(cfg.copy(mbps = v)) }) }
            }
        }
        Panel(title = "Sound", footer = "Phone sound records what is playing through the speaker, such as videos and calls. Apps that block it stay silent.") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("No sound", cfg.audio == 0, { change(cfg.copy(audio = 0)) })
                Chip("Phone sound", cfg.audio == 2, { change(cfg.copy(audio = 2)) }, WonderIcons.Movie)
                Chip("Microphone", cfg.audio == 1, { change(cfg.copy(audio = 1)) }, WonderIcons.Mic)
            }
        }
        Panel(title = "Cast to a TV", footer = "Shows your screen, or a video you are playing, on a TV, Chromecast or any screen your phone can find. Uses Android's own cast, so nothing is sent through Wonderdesk.") {
            Column(Modifier.padding(16.dp)) {
                WButton("Cast this screen", {
                    runCatching { c.startActivity(Intent(android.provider.Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { RecorderState.message = "This phone has no cast screen. Look for Cast or Screen mirroring in its quick settings." }
                }, icon = WonderIcons.Cast)
            }
        }
        Panel(title = "Quick start") {
            Text("Add the Record screen tile to your quick settings (swipe down twice, tap the pencil) to start and stop recording from anywhere with your last settings.",
                Modifier.padding(16.dp), color = p.secondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Invisible activity the quick tile opens: asks Android for the screen, then starts the service with the saved settings. */
class RecordActivity : ComponentActivity() {
    private val screen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        if (r.resultCode == Activity.RESULT_OK && d != null) RecorderService.start(this, r.resultCode, d)
        finish()
    }
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { askScreen() }
    private fun askScreen() = screen.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RecorderState.running) { RecorderService.send(this, RecorderService.ACTION_STOP); finish(); return }
        val need = neededPermissions(this, CaptureConfig.load(this))
        if (need.isEmpty()) askScreen() else perms.launch(need.toTypedArray())
    }
}

/** The "Record screen" quick settings tile. */
class RecordTile : TileService() {
    override fun onStartListening() = refresh()
    private fun refresh() {
        val t = qsTile ?: return
        val c = CaptureConfig.load(this)
        t.state = if (RecorderState.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = if (RecorderState.running) "Stop recording" else if (c.clipMode) "Clip mode" else "Record screen"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (RecorderState.running) "Tap to stop" else "Cinema"
        t.updateTile()
    }
    override fun onClick() {
        val i = Intent(this, RecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") startActivityAndCollapse(i)
    }
}
