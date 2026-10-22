package com.elevate.launcher.ui.wellness

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.elevate.launcher.R
import com.elevate.launcher.databinding.ActivityWellnessHubBinding

class WellnessHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWellnessHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWellnessHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = WellnessPagerAdapter(this)

        val titles = listOf(
            getString(R.string.tab_grooming),
            getString(R.string.tab_posture),
            getString(R.string.tab_workout)
        )

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()
    }

    /** Mirrors the slide-in transition used to open this screen, for a consistent "page" feel. */
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
