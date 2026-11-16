package ua.ruhaysia.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/** Voice phrases built into the app. Each event has several; one is picked at random. */
object Sounds {
    /** Played by the system as the notification sound (file names in res/raw). */
    val REMIND = listOf("remind1", "remind2", "remind3")
    val NAG = listOf("nag1", "nag2", "nag3")

    /** Played by the app itself. */
    private val DONE = intArrayOf(R.raw.done1, R.raw.done2, R.raw.done3)
    private val SNOOZE = intArrayOf(R.raw.snooze1, R.raw.snooze2, R.raw.snooze3)

    private var player: MediaPlayer? = null

    fun uri(ctx: Context, name: String): Uri =
        Uri.parse("android.resource://" + ctx.packageName + "/raw/" + name)

    /** Random index that is not the same as the previous one for this event. */
    fun pick(ctx: Context, kind: String, size: Int): Int {
        val s = Store(ctx)
        val last = s.lastSound(kind)
        var i = (0 until size).random()
        if (size > 1 && i == last) i = (i + 1) % size
        s.setLastSound(kind, i)
        return i
    }

    /** Extra phrases found by file name, e.g. goal1.mp3, done_squats2.mp3; may be empty. */
    private fun named(ctx: Context, prefix: String): List<Int> =
        (1..9).map { ctx.resources.getIdentifier(prefix + it, "raw", ctx.packageName) }
            .filter { it != 0 }

    /**
     * Phrase after a finished warm-up. Most special one wins: [special] ("streak" or
     * "goal"), then one for this exercise, then a general one.
     */
    fun playDone(ctx: Context, ex: Ex?, special: String?) {
        val options = listOfNotNull(special, ex?.let { "done_" + it.key })
        for (prefix in options) {
            val ids = named(ctx, prefix)
            if (ids.isNotEmpty()) {
                play(ctx, ids[pick(ctx, prefix, ids.size)])
                return
            }
        }
        play(ctx, DONE[pick(ctx, "done", DONE.size)])
    }

    fun playSnooze(ctx: Context) = play(ctx, SNOOZE[pick(ctx, "snooze", SNOOZE.size)])

    private fun play(ctx: Context, resId: Int) {
        if (!Store(ctx).appSound) return
        try {
            player?.release()
            player = null
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val afd = ctx.applicationContext.resources.openRawResourceFd(resId)
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
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
}
