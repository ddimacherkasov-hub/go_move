package ua.ruhaysia.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** The warm-up screen: closes only after enough steps or squats. */
class ChallengeActivity : Activity(), SensorEventListener {
    private lateinit var store: Store
    private lateinit var sm: SensorManager

    private var detectorSteps = 0
    private var counterBase = -1f
    private var counterSteps = 0
    private var squats = 0
    private var done = false
    private var stepsRegistered = false

    // Squat detection state (accelerometer).
    private var gravity = 9.81f
    private var smooth = 0f
    private var phase = 0
    private var downAt = 0L
    private var lastRepAt = 0L

    private lateinit var title: TextView
    private lateinit var stepsText: TextView
    private lateinit var stepsBar: ProgressBar
    private lateinit var squatsText: TextView
    private lateinit var squatsBar: ProgressBar
    private lateinit var hint: TextView
    private lateinit var snooze: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        sm = getSystemService(SensorManager::class.java)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        savedInstanceState?.let {
            detectorSteps = it.getInt("detectorSteps")
            counterBase = it.getFloat("counterBase", -1f)
            counterSteps = it.getInt("counterSteps")
            squats = it.getInt("squats")
        }

        buildUi()
        update()

        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        } else {
            registerSteps()
        }
    }

    private fun registerSteps() {
        if (stepsRegistered) return
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        detector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        counter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        stepsRegistered = true
        if (detector == null && counter == null) {
            hint.text = "У телефоні немає датчика кроків — роби присідання."
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) registerSteps()
        else hint.text = "Немає дозволу рахувати кроки — роби присідання."
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("detectorSteps", detectorSteps)
        out.putFloat("counterBase", counterBase)
        out.putInt("counterSteps", counterSteps)
        out.putInt("squats", squats)
    }

    override fun onDestroy() {
        sm.unregisterListener(this)
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        if (done) return
        when (e.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> detectorSteps += 1
            Sensor.TYPE_STEP_COUNTER -> {
                val v = e.values[0]
                if (counterBase < 0f || v < counterBase) counterBase = v
                counterSteps = (v - counterBase).toInt()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!detectSquat(e)) return
            }
            else -> return
        }
        update()
    }

    /**
     * A squat with the phone held at the chest looks like: the phone gets "lighter"
     * while you go down, then "heavier" at the bottom when you push back up.
     * Returns true when a new squat was counted.
     */
    private fun detectSquat(e: SensorEvent): Boolean {
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val m = sqrt(x * x + y * y + z * z)
        gravity += 0.005f * (m - gravity)
        smooth += 0.25f * ((m - gravity) - smooth)
        val t = e.timestamp / 1_000_000L
        when (phase) {
            0 -> if (smooth < -1.5f) {
                phase = 1
                downAt = t
            }
            1 -> if (t - downAt > 2500L) {
                phase = 0
            } else if (smooth > 1.5f && t - downAt > 200L) {
                phase = 2
                if (t - lastRepAt > 1200L) {
                    lastRepAt = t
                    squats += 1
                    return true
                }
            }
            else -> if (abs(smooth) < 0.6f) phase = 0
        }
        return false
    }

    private fun update() {
        val steps = max(detectorSteps, counterSteps)
        val stepsGoal = store.stepsGoal
        val squatsGoal = store.squatsGoal
        stepsText.text = "Кроки: ${minOf(steps, stepsGoal)} / $stepsGoal"
        stepsBar.max = stepsGoal
        stepsBar.progress = minOf(steps, stepsGoal)
        squatsText.text = "Присідання: ${minOf(squats, squatsGoal)} / $squatsGoal"
        squatsBar.max = squatsGoal
        squatsBar.progress = minOf(squats, squatsGoal)
        if (!done && (steps >= stepsGoal || squats >= squatsGoal)) complete()
    }

    private fun complete() {
        done = true
        store.pending = false
        store.snoozeUsed = false
        store.addDoneToday()
        Reminder.hideNotification(this)
        if (store.enabled) Reminder.scheduleNext(this)

        title.text = "Молодець!"
        hint.text = "Розминку зараховано. Таймер запущено знову."
        snooze.visibility = View.GONE
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (ignored: Exception) {
        }
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 2500)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(getColor(R.color.accent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(14)
            ).apply { topMargin = dp(8); bottomMargin = dp(28) }
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(48), dp(28), dp(32))
            fitsSystemWindows = true
        }
        title = TextView(this).apply {
            text = "Час рухатись!"
            textSize = 34f
            setTextColor(getColor(R.color.accent))
        }
        val sub = TextView(this).apply {
            text = "Зроби одне з двох, і нагадування закриється."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(36))
        }
        stepsText = TextView(this).apply { textSize = 24f }
        stepsBar = bar()
        squatsText = TextView(this).apply { textSize = 24f }
        squatsBar = bar()
        hint = TextView(this).apply {
            text = "Кроки: просто пройдись із телефоном.\n" +
                "Присідання: тримай телефон двома руками біля грудей."
            textSize = 14f
            alpha = 0.7f
        }
        snooze = Button(this).apply {
            text = "Відкласти на 10 хв (один раз)"
            visibility = if (store.pending && !store.snoozeUsed) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(36) }
            setOnClickListener {
                store.snoozeUsed = true
                Reminder.hideNotification(this@ChallengeActivity)
                Reminder.scheduleNag(this@ChallengeActivity, 10)
                finish()
            }
        }
        root.addView(title)
        root.addView(sub)
        root.addView(stepsText)
        root.addView(stepsBar)
        root.addView(squatsText)
        root.addView(squatsBar)
        root.addView(hint)
        root.addView(snooze)
        setContentView(root)
    }
}
