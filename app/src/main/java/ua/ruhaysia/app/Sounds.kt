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

    /** Phrases found by file name: done1.mp3, done2.mp3 and so on, in order. */
    private fun named(ctx: Context, prefix: String): List<Int> =
        (1..30).map { ctx.resources.getIdentifier(prefix + it, "raw", ctx.packageName) }
            .filter { it != 0 }

    /** Phrase after an ordinary warm-up: a random one. */
    fun playDone(ctx: Context) = playRandom(ctx, "done")

    /** Phrase when the daily goal is reached: a random one. */
    fun playGoal(ctx: Context) {
        if (named(ctx, "goal").isEmpty()) playDone(ctx) else playRandom(ctx, "goal")
    }

    /**
     * Phrase for a completed week. These go in a circle: week1, week2, week3, week1...
     * [weekNumber] is 1 for the first completed week.
     */
    fun playWeek(ctx: Context, weekNumber: Int) {
        val ids = named(ctx, "week")
        if (ids.isEmpty()) {
            playGoal(ctx)
            return
        }
        play(ctx, ids[(weekNumber - 1).coerceAtLeast(0) % ids.size])
    }

    private fun playRandom(ctx: Context, prefix: String) {
        val ids = named(ctx, prefix)
        if (ids.isNotEmpty()) play(ctx, ids[pick(ctx, prefix, ids.size)])
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
