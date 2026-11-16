package ua.ruhaysia.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** The user's own sounds. A picked file is copied into the app's private storage. */
object Sounds {
    const val REMIND = "remind"
    const val DONE = "done"
    private const val AUTHORITY = "ua.ruhaysia.app.sounds"
    private var player: MediaPlayer? = null

    fun file(ctx: Context, kind: String) = File(ctx.filesDir, "sound_$kind")

    fun has(ctx: Context, kind: String) = file(ctx, kind).length() > 0L

    /** Address the system uses to read the reminder sound. */
    fun uri(kind: String, version: Int): Uri =
        Uri.parse("content://$AUTHORITY/$kind?v=$version")

    /** Copy the picked audio file; returns its display name, or null on failure. */
    fun import(ctx: Context, kind: String, src: Uri): String? {
        return try {
            val input = ctx.contentResolver.openInputStream(src) ?: return null
            input.use { i -> file(ctx, kind).outputStream().use { o -> i.copyTo(o) } }
            var name = "свій звук"
            ctx.contentResolver.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } }
            name
        } catch (e: Exception) {
            file(ctx, kind).delete()
            null
        }
    }

    fun clear(ctx: Context, kind: String) {
        file(ctx, kind).delete()
    }

    fun play(ctx: Context, kind: String) {
        if (!has(ctx, kind)) return
        try {
            stop()
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(file(ctx, kind).absolutePath)
            p.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            p.prepare()
            p.start()
            player = p
        } catch (ignored: Exception) {
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (ignored: Exception) {
        }
        player = null
    }
}

/** Lets the system notification player read the two sound files, read-only. */
class SoundProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val kind = uri.lastPathSegment
        val ctx = context
        if (ctx == null || (kind != Sounds.REMIND && kind != Sounds.DONE)) {
            throw FileNotFoundException()
        }
        val f = Sounds.file(ctx, kind)
        if (!f.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "audio/mpeg"

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ) = 0
}
