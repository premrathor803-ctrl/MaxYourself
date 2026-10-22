package com.elevate.launcher.data

import com.elevate.launcher.model.WellnessItem
import com.elevate.launcher.model.WellnessSection

/**
 * Static, evidence-based content. Deliberately excludes:
 *  - any photo-based "attractiveness" or facial scoring
 *  - any claim that exercise/supplements add height after growth plates close
 * Posture/height content focuses on what's actually real: posture, mobility, and looking
 * (and standing) taller — not altering bone growth.
 */
object WellnessContent {

    fun itemsFor(section: WellnessSection): List<WellnessItem> = when (section) {
        WellnessSection.GROOMING -> grooming
        WellnessSection.POSTURE -> posture
        WellnessSection.WORKOUT -> workout
    }

    private val grooming = listOf(
        WellnessItem(
            id = "groom_skincare_basics",
            title = "A simple daily skincare routine",
            summary = "The basics that actually matter, morning and night.",
            steps = listOf(
                "Morning: gentle cleanser, light moisturizer, SPF 30+ if you'll be outside.",
                "Night: cleanser to remove the day's dirt/oil, then moisturizer.",
                "Add one active (like a salicylic or niacinamide product) only after your skin tolerates the basics — don't stack five new products at once.",
                "See a dermatologist for acne, persistent redness, or anything that isn't improving in 6-8 weeks."
            )
        ),
        WellnessItem(
            id = "groom_haircare",
            title = "Haircare that fits your routine",
            summary = "Wash frequency, product basics, and keeping a cut you can maintain.",
            steps = listOf(
                "Wash 2-4x/week with a shampoo suited to your scalp (oily vs dry) — daily washing isn't necessary for most people.",
                "Get a cut from a barber/stylist you trust, and bring a reference photo.",
                "A small amount of pomade/cream can control texture — more isn't better.",
                "Trim every 3-6 weeks to keep the shape clean."
            )
        ),
        WellnessItem(
            id = "groom_eyebrows",
            title = "Clean, natural eyebrow grooming",
            summary = "Tidy without over-plucking.",
            steps = listOf(
                "Only remove clearly stray hairs outside your natural brow shape.",
                "Trim overly long hairs with small scissors rather than shaving the whole brow.",
                "When in doubt, get it done once by a professional threading/waxing salon so you know your natural shape."
            )
        ),
        WellnessItem(
            id = "groom_dressing",
            title = "Dressing for your body, not a trend",
            summary = "Fit beats brand, every time.",
            steps = listOf(
                "Prioritise correct fit over logos — a well-fitted basic tee beats an ill-fitting expensive one.",
                "Know 2-3 colours that work well together and build around them.",
                "Get key pieces (trousers, one jacket) tailored if they're slightly off — it's cheap and makes a big difference.",
                "Clean, well-maintained shoes matter more than people expect."
            )
        ),
        WellnessItem(
            id = "groom_hygiene",
            title = "Hygiene fundamentals",
            summary = "The unglamorous stuff that makes the biggest difference.",
            steps = listOf(
                "Shower daily, deodorant after.",
                "Trim nails weekly.",
                "Brush twice a day, floss once — fresh breath and healthy teeth matter a lot in how people perceive you.",
                "Wash pillowcases/bedsheets weekly — helps skin too."
            )
        ),
        WellnessItem(
            id = "groom_confidence",
            title = "Confidence is a practiced skill",
            summary = "The single highest-leverage \"look better\" tip.",
            steps = listOf(
                "Make eye contact and stand with your shoulders back (see the Posture tab for exercises).",
                "Smile naturally in photos and conversation instead of a tense, neutral face.",
                "Remember: grooming and style help, but no app score defines your worth — this app is about healthy habits, not comparison."
            )
        )
    )

    private val posture = listOf(
        WellnessItem(
            id = "posture_morning_mobility",
            title = "Morning spinal mobility (5 min)",
            summary = "Loosens up your back and sets better posture for the day.",
            steps = listOf(
                "Cat-Cow: on hands and knees, arch and round your spine slowly — 10 reps.",
                "Child's Pose: sit back on your heels, arms stretched forward — hold 30s.",
                "Standing forward fold: let your spine round, hang for 20-30s, come up slowly.",
                "Gentle side bends: reach one arm overhead and lean sideways — 5 per side."
            )
        ),
        WellnessItem(
            id = "posture_chin_tucks",
            title = "Chin tucks for forward-head posture",
            summary = "Counters \"tech neck\" from phone/laptop use.",
            steps = listOf(
                "Sit or stand tall. Gently draw your chin straight back (like making a double chin), without tilting up or down.",
                "Hold 3-5 seconds, release. Do 10 reps.",
                "Repeat 2-3x through the day, especially after long screen sessions."
            )
        ),
        WellnessItem(
            id = "posture_doorway_stretch",
            title = "Doorway chest stretch",
            summary = "Opens up rounded shoulders from sitting/phone use.",
            steps = listOf(
                "Stand in a doorway, forearms on the frame at shoulder height.",
                "Step one foot forward and gently lean through until you feel a stretch across your chest.",
                "Hold 20-30s, breathe normally, repeat 2-3 times."
            )
        ),
        WellnessItem(
            id = "posture_wall_stand",
            title = "Wall posture check",
            summary = "A 2-minute daily check-in for standing tall.",
            steps = listOf(
                "Stand with your head, upper back, and glutes touching a wall, heels a few cm out.",
                "Flatten your lower back gently toward the wall by bracing your core — don't overarch.",
                "Hold this feeling for 60-90 seconds, then walk away trying to keep it.",
                "Doing this daily retrains your default standing posture over weeks."
            )
        ),
        WellnessItem(
            id = "posture_dead_hang",
            title = "Dead hang (if you have a bar)",
            summary = "Decompresses the spine and builds grip/shoulder strength.",
            steps = listOf(
                "Hang from a pull-up bar or sturdy ledge, arms fully extended, shoulders relaxed.",
                "Hold for as long as comfortable (start with 10-20s), rest, repeat 2-3 times.",
                "Skip this one if you have shoulder pain or an existing injury — check with a doctor first."
            )
        )
    )

    private val workout = listOf(
        WellnessItem(
            id = "workout_full_body",
            title = "Full-body bodyweight circuit (no equipment)",
            summary = "15-20 minutes, do 3 rounds with 60-90s rest between rounds.",
            steps = listOf(
                "Jumping jacks — 30 seconds",
                "Bodyweight squats — 15 reps",
                "Push-ups (knees down is fine) — 10-15 reps",
                "Reverse lunges — 10 per leg",
                "Plank — 30 seconds"
            )
        ),
        WellnessItem(
            id = "workout_core",
            title = "Core & abs (10 minutes)",
            summary = "A short, focused core session.",
            steps = listOf(
                "Plank — 30-45 seconds",
                "Bicycle crunches — 15 per side",
                "Leg raises — 12 reps",
                "Side plank — 20 seconds per side",
                "Dead bug — 10 per side"
            )
        ),
        WellnessItem(
            id = "workout_upper",
            title = "Upper body toning",
            summary = "Push-pull balance using just bodyweight.",
            steps = listOf(
                "Push-ups — 3 sets of 10-15",
                "Pike push-ups (shoulders) — 3 sets of 8-10",
                "Tricep dips off a sturdy chair — 3 sets of 10",
                "Superman holds (back) — 3 sets of 20 seconds"
            )
        ),
        WellnessItem(
            id = "workout_lower",
            title = "Lower body toning",
            summary = "Legs and glutes, zero equipment.",
            steps = listOf(
                "Squats — 3 sets of 15-20",
                "Walking lunges — 3 sets of 10 per leg",
                "Glute bridges — 3 sets of 15",
                "Calf raises — 3 sets of 20"
            )
        ),
        WellnessItem(
            id = "workout_hiit",
            title = "Quick HIIT fat-burn (12 min)",
            summary = "High intensity, 40s on / 20s rest, repeat the circuit twice.",
            steps = listOf(
                "High knees — 40s",
                "Mountain climbers — 40s",
                "Jump squats — 40s",
                "Burpees (step-back version is fine) — 40s",
                "Rest 2 minutes, then repeat the full circuit once more"
            )
        ),
        WellnessItem(
            id = "workout_beginner_note",
            title = "New to working out? Start here",
            summary = "How to begin safely.",
            steps = listOf(
                "Warm up for 3-5 minutes (light jogging in place, arm circles) before any routine above.",
                "It's fine to do fewer reps than listed when starting — consistency matters more than intensity.",
                "Rest at least one day between intense sessions, especially when starting out.",
                "Stop and consult a doctor if you feel sharp pain (different from normal muscle fatigue)."
            )
        )
    )
}
