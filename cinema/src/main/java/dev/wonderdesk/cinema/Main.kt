package dev.wonderdesk.cinema

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wonderdesk.shared.*

/**
 * Cinema's last release. Everything Cinema did (the video player and editor, the screen recorder with clip mode, casting and network
 * streams) now lives inside Memories. This screen says so and takes you there.
 */
class MainActivity : WonderActivity() {
    @Composable override fun Content() = Moved()
}

@Composable
private fun Moved() {
    val context = LocalContext.current
    val p = wonderPalette()
    val memories = remember { context.packageManager.getLaunchIntentForPackage("dev.wonderdesk.memories") }
    WonderApp(appName = "Cinema", about = "Cinema is now part of Memories.", title = "Cinema has moved") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            context.suiteArt("dev.wonderdesk.memories", 256)?.let { androidx.compose.foundation.Image(it, null, Modifier.size(120.dp)) }
            Text("Cinema is now part of Memories", style = MaterialTheme.typography.headlineSmall, color = p.text, textAlign = TextAlign.Center)
            Text("Everything Cinema did is now inside Memories, together with all your photos: the video player and editor (the Videos tab), the screen recorder with clip mode (the Record tab), network streams and casting.", style = MaterialTheme.typography.bodyMedium, color = p.secondaryText, textAlign = TextAlign.Center)
            Text("Your videos and recordings are untouched. Cinema will not be updated any more, so you can remove it once you have tried Memories.", style = MaterialTheme.typography.bodyMedium, color = p.secondaryText, textAlign = TextAlign.Center)
            if (memories != null) WButton("Open Memories", { context.startActivity(memories.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); (context as? android.app.Activity)?.finish() }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
            else {
                Text("Memories isn't installed yet. Get it from Wonderdesk App Hub.", style = MaterialTheme.typography.bodyMedium, color = p.text, textAlign = TextAlign.Center)
                WButton("Open Wonderdesk App Hub", {
                    val hub = context.packageManager.getLaunchIntentForPackage("dev.wonderdesk.apphub")
                    if (hub != null) context.startActivity(hub.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thoroftroy/Memories/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
            }
            WButton("Remove Cinema from this phone", { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:dev.wonderdesk.cinema")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth(), kind = ButtonKind.Plain)
        }
    }
}
