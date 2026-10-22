package com.elevate.launcher.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.elevate.launcher.databinding.ActivityHomeBinding
import com.elevate.launcher.ui.drawer.AppDrawerActivity
import com.elevate.launcher.ui.wellness.WellnessHubActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher's home screen. Registered for both HOME and plain LAUNCHER intent-filters,
 * so it works whether or not the user has set it as their default Home app.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
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
        binding.cardWellness.setOnClickListener {
            startActivity(Intent(this, WellnessHubActivity::class.java))
        }
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
