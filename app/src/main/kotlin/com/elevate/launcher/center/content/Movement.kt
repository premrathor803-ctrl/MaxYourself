package com.elevate.launcher.center.content

/**
 * The posture and mobility library: the existing exercises, grouped by purpose. Nothing here is a height exercise.
 * These are gentle movement and awareness activities. They do not lengthen bones or treat spine conditions.
 */
object Movement {
    class Group(val key: String, val title: String, val blurb: String, val keys: List<String>)

    val groups = listOf(
        Group("awareness", "Posture awareness", "Notice how you stand and sit, then reset. Small and quick.",
            listOf("standing_posture_check", "chin_tuck", "shoulder_rolls")),
        Group("shoulders", "Shoulders and upper back", "Open the chest and wake up the muscles between the shoulder blades.",
            listOf("scap_squeeze", "wall_angel", "doorway_chest_stretch", "neck_side_stretch", "seated_back_extension")),
        Group("hips", "Hip mobility", "Gentle moves for hips and the backs and fronts of the legs.",
            listOf("hip_circles", "hip_flexor_stretch", "hamstring_stretch", "quad_stretch", "figure4_stretch")),
        Group("core", "Core stability", "Slow, controlled work for the muscles around the middle.",
            listOf("dead_bug", "bird_dog", "knee_plank", "side_plank_knee", "curl_up")),
        Group("gentle", "Gentle mobility and reset", "Easy, flowing moves for a stiff day or the end of one.",
            listOf("cat_cow", "childs_pose", "calf_stretch_wall", "knees_to_chest", "supine_twist", "deep_breathing")),
        Group("breaks", "Breaks for long sitting", "Short resets for a desk or a chair. Try one every 30 to 60 minutes.",
            listOf("shoulder_rolls", "neck_side_stretch", "seated_back_extension", "chin_tuck", "march_in_place", "calf_stretch_wall"))
    )

    /** Every exercise that is part of the movement library. */
    val keys: Set<String> = groups.flatMap { it.keys }.toSet()

    fun isMovement(key: String): Boolean = key in keys

    /** A common, cautious starting point. Not a prescription. */
    fun suggestion(ex: Exercise): String {
        val side = if (ex.perSide) " on each side" else ""
        return if (ex.timed) {
            if (ex.category == Exercises.CORE) "Hold for 10 to 30 seconds$side, rest, and repeat 2 or 3 times."
            else "Hold for about 20 to 30 seconds$side, easing in gently. Do it once or twice."
        } else {
            "About 8 to 12 slow repetitions$side. Once or twice through is plenty to start."
        }
    }
}
