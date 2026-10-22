package com.elevate.launcher.ui.wellness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.elevate.launcher.R
import com.elevate.launcher.data.StreakPrefs
import com.elevate.launcher.data.WellnessContent
import com.elevate.launcher.databinding.FragmentWellnessListBinding
import com.elevate.launcher.model.WellnessSection

class WellnessListFragment : Fragment() {

    companion object {
        private const val ARG_SECTION = "arg_section"

        fun newInstance(section: WellnessSection): WellnessListFragment {
            val fragment = WellnessListFragment()
            fragment.arguments = Bundle().apply { putString(ARG_SECTION, section.name) }
            return fragment
        }
    }

    private var _binding: FragmentWellnessListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWellnessListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sectionName = arguments?.getString(ARG_SECTION) ?: WellnessSection.GROOMING.name
        val section = WellnessSection.valueOf(sectionName)

        if (section == WellnessSection.POSTURE) {
            binding.textDisclaimer.visibility = View.VISIBLE
            binding.textDisclaimer.text = getString(R.string.posture_disclaimer)
        } else {
            binding.textDisclaimer.visibility = View.VISIBLE
            binding.textDisclaimer.text = getString(R.string.general_disclaimer_wellness)
        }

        val items = WellnessContent.itemsFor(section)
        val streakPrefs = StreakPrefs(requireContext())

        binding.recyclerItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerItems.adapter = WellnessAdapter(items, streakPrefs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
