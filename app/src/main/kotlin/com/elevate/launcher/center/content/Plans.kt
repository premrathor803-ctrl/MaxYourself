package com.elevate.launcher.center.content

import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days

/** One exercise inside a plan. [amount] is reps or seconds depending on the exercise. */
class PlanStep(val key: String, val sets: Int, val amount: Int, val rest: Int) {
    val ex: Exercise get() = Exercises.get(key)!!

    /** Rough time for this block in seconds, used only for the "about N min" estimate. */
    fun seconds(): Int {
        val e = ex
        val side = if (e.perSide) 2 else 1
        val work = if (e.timed) amount else amount * 3
        return sets * side * work + (sets - 1).coerceAtLeast(0) * rest + 10
    }

    fun describe(): String {
        val e = ex
        val unit = if (e.timed) "sec" else "reps"
        val side = if (e.perSide) " each side" else ""
        return if (sets > 1) "$sets × $amount $unit$side" else "$amount $unit$side"
    }
}

class Plan(
    val key: String, val name: String, val category: String, val level: Int, val blurb: String,
    val warm: List<PlanStep>, val main: List<PlanStep>, val cool: List<PlanStep>
) {
    val steps: List<PlanStep> get() = warm + main + cool
    val minutes: Int get() = Math.max(5, Math.round(steps.sumOf { it.seconds() } / 60.0f))
    val levelName: String get() = if (level <= 1) "Beginner" else "Intermediate"
    val equipment: String get() = "No equipment. A wall, a sturdy chair and a mat or towel are enough."
}

object Plans {
    private fun s(key: String, sets: Int, amount: Int, rest: Int = 30) = PlanStep(key, sets, amount, rest)

    val all: List<Plan> = listOf(
        Plan("beginner_full_a", "Beginner full body A", "Full body", 1,
            "A gentle all-over session built on the basic movements: squat, push, bridge and core control.",
            listOf(s("march_in_place", 1, 40), s("arm_circles", 1, 10), s("cat_cow", 1, 8)),
            listOf(s("chair_squat", 2, 8, 45), s("wall_pushup", 2, 8, 45), s("glute_bridge", 2, 10, 45), s("dead_bug", 2, 6, 45)),
            listOf(s("hamstring_stretch", 1, 30), s("childs_pose", 1, 30), s("deep_breathing", 1, 40))),
        Plan("beginner_full_b", "Beginner full body B", "Full body", 1,
            "A second beginner session with a different mix, so your week does not repeat itself.",
            listOf(s("march_in_place", 1, 40), s("hip_circles", 1, 8), s("cat_cow", 1, 8)),
            listOf(s("incline_pushup", 2, 6, 45), s("calf_raise", 2, 10, 45), s("bird_dog", 2, 6, 45), s("superman", 2, 8, 45), s("knee_plank", 2, 20, 45)),
            listOf(s("quad_stretch", 1, 25), s("childs_pose", 1, 30), s("deep_breathing", 1, 40))),
        Plan("bodyweight_strength", "Bodyweight strength", "Strength", 2,
            "A step up for people comfortable with the basics. Squats, push-ups, lunges and a plank.",
            listOf(s("step_jack", 1, 40), s("arm_circles", 1, 10), s("hip_circles", 1, 8)),
            listOf(s("bodyweight_squat", 3, 12, 45), s("pushup", 3, 8, 45), s("reverse_lunge", 3, 8, 45), s("glute_bridge", 3, 12, 45), s("plank", 3, 30, 45)),
            listOf(s("hip_flexor_stretch", 1, 30), s("doorway_chest_stretch", 1, 30), s("childs_pose", 1, 30))),
        Plan("upper_body", "Upper body basics", "Strength", 1,
            "Chest, shoulders, arms and upper back, using movements you can scale with a wall or table.",
            listOf(s("arm_circles", 1, 10), s("shoulder_rolls", 1, 10), s("cat_cow", 1, 8)),
            listOf(s("incline_pushup", 3, 8, 45), s("superman", 3, 8, 45), s("scap_squeeze", 3, 10, 30), s("wall_angel", 2, 8, 30), s("knee_plank", 2, 25, 45)),
            listOf(s("doorway_chest_stretch", 1, 30), s("neck_side_stretch", 1, 20), s("deep_breathing", 1, 40))),
        Plan("lower_body", "Lower body", "Strength", 2,
            "Thighs, glutes and calves. Takes about as long as a TV episode break.",
            listOf(s("march_in_place", 1, 40), s("hip_circles", 1, 8)),
            listOf(s("bodyweight_squat", 3, 12, 45), s("reverse_lunge", 3, 8, 45), s("glute_bridge", 3, 12, 45), s("calf_raise", 3, 12, 30), s("wall_sit", 2, 30, 45)),
            listOf(s("quad_stretch", 1, 25), s("hamstring_stretch", 1, 30), s("calf_stretch_wall", 1, 25))),
        Plan("core_basic", "Core basics", "Core", 1,
            "Slow, controlled core work that is easy on the lower back.",
            listOf(s("cat_cow", 1, 8), s("march_in_place", 1, 30)),
            listOf(s("dead_bug", 2, 6, 45), s("bird_dog", 2, 6, 45), s("curl_up", 2, 10, 30), s("knee_plank", 2, 20, 45), s("side_plank_knee", 2, 15, 30)),
            listOf(s("childs_pose", 1, 30), s("supine_twist", 1, 25), s("deep_breathing", 1, 40))),
        Plan("mobility_flex", "Mobility and flexibility", "Mobility", 1,
            "Stretching for tight hips, legs, chest and neck. Nothing here should hurt.",
            listOf(s("cat_cow", 1, 8), s("shoulder_rolls", 1, 10), s("hip_circles", 1, 8)),
            listOf(s("hip_flexor_stretch", 1, 30), s("hamstring_stretch", 1, 30), s("quad_stretch", 1, 25), s("doorway_chest_stretch", 1, 30), s("neck_side_stretch", 1, 20), s("calf_stretch_wall", 1, 25)),
            listOf(s("childs_pose", 1, 40), s("deep_breathing", 1, 40))),
        Plan("low_impact_cardio", "Low-impact cardio", "Cardio", 1,
            "Gets your heart rate up with no jumping, so it is kinder on knees and neighbours.",
            listOf(s("march_in_place", 1, 40)),
            listOf(s("step_jack", 3, 40, 20), s("shadow_boxing", 3, 40, 20), s("knee_to_elbow", 3, 10, 20)),
            listOf(s("quad_stretch", 1, 25), s("deep_breathing", 1, 40))),
        Plan("recovery_gentle", "Gentle recovery", "Recovery", 1,
            "A calm session for rest days or when you feel stiff. Easy stretches and slow breathing.",
            listOf(s("cat_cow", 1, 8)),
            listOf(s("knees_to_chest", 1, 40), s("supine_twist", 1, 30), s("figure4_stretch", 1, 30), s("childs_pose", 1, 40)),
            listOf(s("deep_breathing", 1, 60))),
        Plan("posture_reset", "Posture reset", "Posture", 1,
            "A short break for your upper back, neck and chest. Good after a long time sitting or on your phone.",
            listOf(s("shoulder_rolls", 1, 10), s("cat_cow", 1, 8)),
            listOf(s("chin_tuck", 2, 8, 20), s("scap_squeeze", 2, 10, 20), s("wall_angel", 2, 8, 30), s("standing_posture_check", 1, 30), s("doorway_chest_stretch", 1, 30), s("seated_back_extension", 1, 8)),
            listOf(s("deep_breathing", 1, 40)))
    )

    private val byKey = all.associateBy { it.key }
    fun get(key: String): Plan? = byKey[key]

    val categories: List<String> get() = all.map { it.category }.distinct()

    private val beginnerRotation = listOf("beginner_full_a", "beginner_full_b", "core_basic", "mobility_flex")
    private val intermediateRotation = listOf("bodyweight_strength", "lower_body", "upper_body", "core_basic")

    fun rotation(): List<String> = if (Center.level == "intermediate") intermediateRotation else beginnerRotation

    fun isTrainingDay(day: String): Boolean = (Center.trainingDays shr Days.dow(day)) and 1 == 1

    /**
     * The suggested plan for [day], or null on a rest day. The suggestion is only a convenience: any plan can be
     * started at any time from the workout screen.
     */
    fun suggestedFor(day: String): Plan? {
        if (!isTrainingDay(day)) return null
        val rot = rotation()
        val mask = Center.trainingDays
        val trainDow = (0..6).filter { (mask shr it) and 1 == 1 }
        if (trainDow.isEmpty()) return null
        val weekNum = Days.diff("2024-01-01", Days.weekStart(day)) / 7
        val pos = trainDow.indexOf(Days.dow(day)).coerceAtLeast(0)
        val idx = Math.floorMod(weekNum * trainDow.size + pos, rot.size)
        return get(rot[idx])
    }

    fun restDayPlan(): Plan = get("recovery_gentle")!!
}
