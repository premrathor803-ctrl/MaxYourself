package com.elevate.launcher.center.data

/** Which dashboard cards are shown and in what order. Pure, so the rules can be tested. */
object DashLayout {
    const val HERO = "hero"
    const val HABITS = "habits"
    const val WORKOUT = "workout"
    const val GROWTH = "growth"
    const val GAMING = "gaming"
    const val ACHIEVEMENTS = "achievements"
    const val QUICK = "quick"
    const val EXPLORE = "explore"

    /** Every card, in the order the Explorer layout shows them. */
    val all = listOf(HERO, QUICK, HABITS, WORKOUT, GROWTH, GAMING, ACHIEVEMENTS, EXPLORE)

    val names = mapOf(
        HERO to "Today and level", QUICK to "Quick actions", HABITS to "Habits", WORKOUT to "Workout",
        GROWTH to "Height and posture", GAMING to "Gaming mode", ACHIEVEMENTS to "Achievements", EXPLORE to "Explore"
    )

    /** The cards a layout shows, in order. Every layout keeps Explore, which is how the other sections are reached. */
    fun preset(layout: String): List<String> = when (layout) {
        "focus" -> listOf(HERO, HABITS, WORKOUT, QUICK, EXPLORE)
        "explorer" -> listOf(HERO, EXPLORE, QUICK, GROWTH, GAMING, ACHIEVEMENTS, HABITS, WORKOUT)
        else -> all
    }

    private fun parse(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() && it in all }.distinct()

    /**
     * The final list. Choosing a layout clears the person's own order and hidden cards, so [order] is empty until they
     * rearrange something. An empty [order] means "use the layout"; otherwise it is the full order they chose. [hidden]
     * lists the cards they turned off. Unknown ids are ignored, cards missing from a saved order are added at the end, and
     * if everything were hidden the first card still shows, so the screen is never empty.
     */
    fun resolve(layout: String, order: String, hidden: String): List<String> {
        val saved = parse(order)
        val ordered = if (saved.isEmpty()) preset(layout) else saved + all.filter { it !in saved }
        val off = parse(hidden).toSet()
        val shown = ordered.filter { it !in off }
        return if (shown.isEmpty()) listOf(HERO) else shown
    }

    /** All eight cards in the order the editor shows them: the saved order, or the layout's cards followed by the rest. */
    fun fullOrder(layout: String, order: String): List<String> {
        val saved = parse(order)
        return if (saved.isEmpty()) preset(layout) + all.filter { it !in preset(layout) } else saved + all.filter { it !in saved }
    }

    /** The cards that are switched off: those the layout leaves out (when nothing was customised) plus the hidden list. */
    fun hiddenSet(layout: String, order: String, hidden: String): Set<String> {
        val base = if (parse(order).isEmpty()) all.filter { it !in preset(layout) }.toSet() else emptySet()
        return base + parse(hidden)
    }

    /**
     * What to store for an edited [order] and [hidden] set under [layout]. If they equal the layout's own, nothing needs
     * storing (two empty strings), so a later change of layout is not held back by an old customisation.
     */
    fun toPrefs(layout: String, order: List<String>, hidden: Set<String>): Pair<String, String> {
        val own = preset(layout) + all.filter { it !in preset(layout) }
        val ownHidden = all.filter { it !in preset(layout) }.toSet()
        if (order == own && hidden == ownHidden) return "" to ""
        return order.joinToString(",") to hidden.filter { it in all }.joinToString(",")
    }

    /** Moves [id] one place up (-1) or down (+1) in [current] and returns the new order as comma separated text. */
    fun move(current: List<String>, id: String, by: Int): String {
        val list = current.toMutableList()
        val i = list.indexOf(id)
        if (i < 0) return list.joinToString(",")
        val j = (i + by).coerceIn(0, list.lastIndex)
        list.removeAt(i)
        list.add(j, id)
        return list.joinToString(",")
    }
}
