package ua.ruhaysia.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Voice phrases built into the app. Each event has several; one is picked at random. */
object Sounds {
    /** Played by the app itself. */
    private val SNOOZE = intArrayOf(R.raw.snooze1, R.raw.snooze2, R.raw.snooze3)

    private var player: MediaPlayer? = null
    private var alarmPlayer: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    /**
     * The reminder phrase. It is played on the alarm volume, like an alarm clock, so it is
     * heard even when the phone is on silent; a video or music is turned down meanwhile.
     * [onDone] is called when it has finished, failed or after 8 seconds at most.
     */
    fun playReminder(ctx: Context, nag: Boolean, onDone: () -> Unit) {
        val app = ctx.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                stopReminder(app)
                onDone()
            }
        }
        val prefix = if (nag) "nag" else "remind"
        val ids = named(app, prefix)
        if (!Store(app).remindSound || ids.isEmpty()) {
            onDone()
            return
        }
        try {
            stopReminder(app)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val am = app.getSystemService(AudioManager::class.java)
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            am?.requestAudioFocus(req)
            focus = req
            val p = MediaPlayer()
            p.setAudioAttributes(attrs)
            val afd = app.resources.openRawResourceFd(ids[pick(app, prefix, ids.size)])
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.setOnCompletionListener { finish() }
            p.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            p.prepare()
            p.start()
            alarmPlayer = p
            handler.postDelayed({ finish() }, 8000L)
        } catch (e: Exception) {
            finish()
        }
    }

    private fun stopReminder(ctx: Context) {
        try {
            alarmPlayer?.release()
        } catch (ignored: Exception) {
        }
        alarmPlayer = null
        focus?.let {
            try {
                ctx.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it)
            } catch (ignored: Exception) {
            }
        }
        focus = null
    }

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
