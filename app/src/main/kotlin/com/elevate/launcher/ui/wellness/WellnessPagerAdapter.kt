package com.elevate.launcher.ui.wellness

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.elevate.launcher.model.WellnessSection

class WellnessPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val sections = listOf(
        WellnessSection.GROOMING,
        WellnessSection.POSTURE,
        WellnessSection.WORKOUT
    )

    override fun getItemCount(): Int = sections.size

    override fun createFragment(position: Int): Fragment =
        WellnessListFragment.newInstance(sections[position])
}
