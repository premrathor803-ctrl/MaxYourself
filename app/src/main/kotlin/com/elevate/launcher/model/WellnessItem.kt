package com.elevate.launcher.model

/**
 * One tip or routine shown inside the Wellness Hub.
 * [steps] is empty for simple grooming tips, and populated for step-by-step routines.
 */
data class WellnessItem(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String> = emptyList()
)

enum class WellnessSection(val prefsKey: String) {
    GROOMING("grooming"),
    POSTURE("posture"),
    WORKOUT("workout")
}
