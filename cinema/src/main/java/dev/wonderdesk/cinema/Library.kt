package dev.wonderdesk.cinema

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import org.json.JSONObject
import java.io.File

data class Video(val id: Long, val uri: Uri, val title: String, val duration: Long, val size: Long, val width: Int, val height: Int,
                 val folder: String, val added: Long, val path: String)

object Videos {
    fun all(c: Context): List<Video> {
        val out = mutableListOf<Video>()
        val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.RELATIVE_PATH)
        runCatching {
            c.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    out += Video(id, ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), cur.getString(1) ?: "Video", cur.getLong(2), cur.getLong(3),
                        cur.getInt(4), cur.getInt(5), cur.getString(6) ?: "Videos", cur.getLong(7) * 1000, cur.getString(8) ?: "")
                }
            }
        }
        return out
    }

    private val thumbs = object : LruCache<Long, Bitmap>(40 * 1024 * 1024) { override fun sizeOf(key: Long, value: Bitmap) = value.byteCount }
    fun thumb(c: Context, v: Video): Bitmap? = thumbs.get(v.id) ?: runCatching { c.contentResolver.loadThumbnail(v.uri, Size(384, 216), null) }.getOrNull()?.also { thumbs.put(v.id, it) }

    fun frame(c: Context, uri: Uri, atMs: Long): Bitmap? = runCatching {
        MediaMetadataRetriever().use { r -> r.setDataSource(c, uri); r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) }
    }.getOrNull()

    /** Saves into Movies/Cinema or Pictures/Cinema through MediaStore and returns the new item's uri. */
    fun publish(c: Context, src: File, name: String, image: Boolean): Uri {
        val coll = if (image) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (image) "image/png" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/Cinema")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = c.contentResolver.insert(coll, values) ?: error("Couldn't save to your library")
        c.contentResolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
        c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }
}

/** Remembers where you stopped each video. */
object Resume {
    private fun prefs(c: Context) = c.getSharedPreferences("cinema-resume", Context.MODE_PRIVATE)
    fun get(c: Context, uri: Uri): Long = prefs(c).getString(uri.toString(), null)?.let { runCatching { JSONObject(it).getLong("pos") }.getOrNull() } ?: 0L
    fun set(c: Context, uri: Uri, pos: Long, duration: Long) {
        val e = prefs(c).edit()
        if (duration > 0 && (pos < 5_000 || pos > duration - 10_000)) e.remove(uri.toString())
        else e.putString(uri.toString(), JSONObject().put("pos", pos).put("dur", duration).put("at", System.currentTimeMillis()).toString())
        e.apply()
    }
    fun recent(c: Context): List<Triple<Uri, Long, Long>> = prefs(c).all.mapNotNull { (k, v) ->
        runCatching { val o = JSONObject(v as String); Triple(Uri.parse(k), o.getLong("pos"), o.getLong("at")) to o.getLong("dur") }.getOrNull()
    }.sortedByDescending { it.first.third }.map { it.first }
    fun progress(c: Context, uri: Uri): Float = prefs(c).getString(uri.toString(), null)?.let { runCatching { JSONObject(it).let { o -> o.getLong("pos").toFloat() / o.getLong("dur").coerceAtLeast(1) } }.getOrNull() } ?: 0f
}

data class EditSpec(val startMs: Long, val endMs: Long, val mute: Boolean, val rotate: Int, val height: Int?)

/** Video edits with Media3 Transformer. Must be started on the main thread. */
@UnstableApi
object Editor {
    private var transformer: Transformer? = null

    fun export(c: Context, src: Uri, spec: EditSpec, out: File, onDone: (Result<File>) -> Unit): () -> Int {
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(spec.startMs).setEndPositionMs(spec.endMs).build()
        val item = MediaItem.Builder().setUri(src).setClippingConfiguration(clip).build()
        val effects = buildList<androidx.media3.common.Effect> {
            if (spec.rotate % 360 != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(spec.rotate.toFloat()).build())
            spec.height?.let { add(Presentation.createForHeight(it)) }
        }
        val edited = EditedMediaItem.Builder(item).setRemoveAudio(spec.mute).setEffects(Effects(emptyList(), effects)).build()
        val t = Transformer.Builder(c).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { transformer = null; onDone(Result.success(out)) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { transformer = null; onDone(Result.failure(exportException)) }
        }).build()
        transformer = t
        out.delete()
        t.start(edited, out.path)
        val holder = ProgressHolder()
        return { if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1 }
    }

    fun cancel() { transformer?.cancel(); transformer = null }
}
