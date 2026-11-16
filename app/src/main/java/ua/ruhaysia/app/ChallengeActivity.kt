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
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The warm-up screen. The user picks one of the enabled exercises; the reminder
 * closes when the goal of any exercise is reached. Only the picked exercise counts.
 */
class ChallengeActivity : Activity(), SensorEventListener {
    private lateinit var store: Store
    private lateinit var sm: SensorManager
    private lateinit var exercises: List<Ex>
    private lateinit var active: Ex

    private val counts = IntArray(Ex.values().size)
    private var done = false
    private var stepsRegistered = false
    private var hasGyro = false
    private var stepsProblem: String? = null

    // Steps: two sources, the larger one wins.
    private var stepDetector = 0
    private var stepCounter = 0
    private var lastCounterValue = -1f

    // Accelerometer state.
    private var gravity = 9.81f
    private var smooth = 0f
    private var phase = 0
    private var downAt = 0L
    private var lastRepAt = 0L
    private var airborne = false
    private var airAt = 0L

    // Gyroscope state: rotation is summed up, every N radians is one swing.
    private var lastGyroNs = 0L
    private var angle = 0f

    private lateinit var title: TextView
    private lateinit var hint: TextView
    private lateinit var snooze: Button
    private val rows = HashMap<Ex, LinearLayout>()
    private val texts = HashMap<Ex, TextView>()
    private val bars = HashMap<Ex, ProgressBar>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        sm = getSystemService(SensorManager::class.java)
        exercises = store.enabledExercises()
        active = exercises[0]

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

        savedInstanceState?.let { b ->
            b.getIntArray("counts")?.let { if (it.size == counts.size) it.copyInto(counts) }
            stepDetector = b.getInt("stepDetector")
            stepCounter = b.getInt("stepCounter")
            val saved = Ex.values().getOrNull(b.getInt("active", -1))
            if (saved != null && saved in exercises) active = saved
        }

        buildUi()

        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            hasGyro = sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (Ex.STEPS in exercises) {
            if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
            } else {
                registerSteps()
            }
        }
        select(active)
    }

    private fun registerSteps() {
        if (stepsRegistered) return
        val detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val counter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        detector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        counter?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        stepsRegistered = true
        if (detector == null && counter == null) {
            stepsProblem = "У телефоні немає датчика кроків — обери іншу вправу."
            showHint()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            registerSteps()
        } else {
            stepsProblem = "Немає дозволу рахувати кроки — обери іншу вправу."
            showHint()
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putIntArray("counts", counts)
        out.putInt("stepDetector", stepDetector)
        out.putInt("stepCounter", stepCounter)
        out.putInt("active", active.ordinal)
    }

    override fun onDestroy() {
        sm.unregisterListener(this)
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        if (done) return
        val changed = when (e.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> onStepDetector()
            Sensor.TYPE_STEP_COUNTER -> onStepCounter(e.values[0])
            Sensor.TYPE_ACCELEROMETER -> onAccelerometer(e)
            Sensor.TYPE_GYROSCOPE -> onGyroscope(e)
            else -> false
        }
        if (changed) update()
    }

    private fun onStepDetector(): Boolean {
        if (active != Ex.STEPS) return false
        stepDetector += 1
        counts[Ex.STEPS.ordinal] = max(stepDetector, stepCounter)
        return true
    }

    private fun onStepCounter(v: Float): Boolean {
        val prev = lastCounterValue
        lastCounterValue = v
        if (active != Ex.STEPS || prev < 0f || v <= prev) return false
        stepCounter += (v - prev).toInt()
        counts[Ex.STEPS.ordinal] = max(stepDetector, stepCounter)
        return true
    }

    private fun onAccelerometer(e: SensorEvent): Boolean {
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val m = sqrt(x * x + y * y + z * z)
        gravity += 0.005f * (m - gravity)
        smooth += 0.25f * ((m - gravity) - smooth)
        val t = e.timestamp / 1_000_000L
        return when (active) {
            Ex.SQUATS -> detectSquat(t)
            Ex.JUMPS -> detectJump(m, t)
            Ex.ARMS, Ex.LEGS -> if (hasGyro) false else detectShake(t)
            else -> false
        }
    }

    /**
     * Squat with the phone at the chest: the phone gets "lighter" on the way down,
     * then "heavier" at the bottom when you push back up.
     */
    private fun detectSquat(t: Long): Boolean {
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
                    counts[Ex.SQUATS.ordinal] += 1
                    return true
                }
            }
            else -> if (abs(smooth) < 0.6f) phase = 0
        }
        return false
    }

    /** Jump: a moment of near weightlessness in the air, then a hard landing. */
    private fun detectJump(m: Float, t: Long): Boolean {
        if (m < 3.5f) {
            airborne = true
            airAt = t
        } else if (airborne) {
            if (t - airAt > 700L) {
                airborne = false
            } else if (m > 15f) {
                airborne = false
                if (t - lastRepAt > 350L) {
                    lastRepAt = t
                    counts[Ex.JUMPS.ordinal] += 1
                    return true
                }
            }
        }
        return false
    }

    /** Swings on phones without a gyroscope: count strong shakes instead. */
    private fun detectShake(t: Long): Boolean {
        when (phase) {
            0 -> if (abs(smooth) > 3.5f) {
                phase = 2
                if (t - lastRepAt > 450L) {
                    lastRepAt = t
                    counts[active.ordinal] += 1
                    return true
                }
            }
            else -> if (abs(smooth) < 1.5f) phase = 0
        }
        return false
    }

    /** Swings: add up how far the phone has turned; every N radians is one swing. */
    private fun onGyroscope(e: SensorEvent): Boolean {
        val prev = lastGyroNs
        lastGyroNs = e.timestamp
        if (active != Ex.ARMS && active != Ex.LEGS) return false
        if (prev == 0L) return false
        val dt = ((e.timestamp - prev) / 1e9f).coerceIn(0f, 0.1f)
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val w = sqrt(x * x + y * y + z * z)
        val gate = if (active == Ex.ARMS) 1.2f else 1.0f
        val perRep = if (active == Ex.ARMS) 2.6f else 2.2f
        if (w < gate) return false
        angle += w * dt
        var added = false
        while (angle >= perRep) {
            angle -= perRep
            counts[active.ordinal] += 1
            added = true
        }
        return added
    }

    private fun select(ex: Ex) {
        if (done) return
        active = ex
        phase = 0
        airborne = false
        angle = 0f
        for (e in exercises) {
            val on = e == active
            rows[e]?.alpha = if (on) 1f else 0.45f
            texts[e]?.setTextColor(getColor(if (on) R.color.accent else android.R.color.white))
        }
        showHint()
        update()
    }

    private fun showHint() {
        if (done) return
        hint.text = if (active == Ex.STEPS && stepsProblem != null) stepsProblem else active.hint
    }

    private fun update() {
        var reached: Ex? = null
        for (e in exercises) {
            val goal = store.goal(e)
            val n = counts[e.ordinal]
            texts[e]?.text = "${e.title}: ${minOf(n, goal)} / $goal"
            bars[e]?.max = goal
            bars[e]?.progress = minOf(n, goal)
            if (n >= goal && reached == null) reached = e
        }
        if (!done && reached != null) complete()
    }

    private fun complete() {
        done = true
        store.pending = false
        store.snoozeUsed = false
        store.snoozeUntil = 0L
        store.addDoneToday()
        for (e in exercises) {
            val n = counts[e.ordinal]
            if (n > 0) store.addAmountToday(e, n)
        }
        Reminder.hideNotification(this)
        if (store.enabled && !Reminder.isPaused(store)) Reminder.scheduleNext(this)

        val today = store.doneOn(Date())
        val goalHit = today == store.dailyGoal
        val weekHit = goalHit && store.claimWeekDone()
        if (weekHit) {
            store.weeksDone = store.weeksDone + 1
            Sounds.playWeek(this, store.weeksDone)
        } else if (goalHit) {
            Sounds.playGoal(this)
        } else {
            Sounds.playDone(this)
        }
        val (goalDays, activeDays) = store.weekProgress()
        title.text = when {
            weekHit -> "Тижневу ціль виконано!"
            goalHit -> "Денну ціль виконано!"
            else -> "Молодець!"
        }
        hint.text = "Сьогодні: $today з ${store.dailyGoal}. " +
            "Тиждень: $goalDays з $activeDays днів.\nТаймер запущено знову."
        MoveWidget.refresh(this)
        snooze.visibility = View.GONE
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (ignored: Exception) {
        }
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 2500)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(32))
        }
        title = TextView(this).apply {
            text = "Час рухатись!"
            textSize = 34f
            setTextColor(getColor(R.color.accent))
        }
        val sub = TextView(this).apply {
            text = if (exercises.size > 1) "Обери вправу (натисни на неї) і виконай до кінця."
            else "Виконай вправу, і нагадування закриється."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(title)
        root.addView(sub)

        for (e in exercises) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                isClickable = true
                setOnClickListener { select(e) }
            }
            val text = TextView(this).apply { textSize = 22f }
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                progressTintList = ColorStateList.valueOf(getColor(R.color.accent))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
                ).apply { topMargin = dp(6) }
            }
            row.addView(text)
            row.addView(bar)
            rows[e] = row
            texts[e] = text
            bars[e] = bar
            root.addView(row)
        }

        hint = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(20), 0, 0)
        }
        snooze = Button(this).apply {
            text = "Відкласти на 10 хв"
            visibility = if (store.pending && !store.snoozeUsed) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
            setOnClickListener {
                Sounds.playSnooze(this@ChallengeActivity)
                Reminder.snooze(this@ChallengeActivity)
                finish()
            }
        }
        root.addView(hint)
        root.addView(snooze)
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            isFillViewport = true
            addView(root)
        })
    }
}
