package com.elevate.launcher.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.elevate.launcher.R
import com.elevate.launcher.databinding.ActivityHomeBinding
import com.elevate.launcher.ui.drawer.AppDrawerActivity
import com.elevate.launcher.ui.wellness.WellnessHubActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The launcher's home screen. Registered for both HOME and plain LAUNCHER intent-filters,
 * so it works whether or not the user has set it as their default Home app.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var gestureDetector: GestureDetectorCompat
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardAllApps.setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }
        binding.cardWellness.setOnClickListener { openWellnessHub() }
        binding.editSearch.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = textView.text?.toString().orEmpty()
                val intent = Intent(this, AppDrawerActivity::class.java)
                intent.putExtra(AppDrawerActivity.EXTRA_INITIAL_QUERY, query)
                startActivity(intent)
                textView.text = null
                true
            } else {
                false
            }
        }

        // Smart-Launcher-style page swipe: swipe right anywhere on the home screen
        // to open the Wellness Hub, like sliding to a side page.
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y
                val swipedRight = deltaX > SWIPE_DISTANCE_THRESHOLD &&
                    abs(deltaX) > abs(deltaY) &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                if (swipedRight) {
                    openWellnessHub()
                    return true
                }
                return false
            }
        })
        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // let taps still reach the buttons/search field underneath
        }
    }

    private fun openWellnessHub() {
        startActivity(Intent(this, WellnessHubActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    companion object {
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    @SuppressLint("SetTextI18n")
    private fun updateClock() {
        val now = Date()
        binding.textClock.text = clockFormat.format(now)
        binding.textDate.text = dateFormat.format(now)
    }

    /** As a Home app, the back button shouldn't close us — just behave like stock launchers. */
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
