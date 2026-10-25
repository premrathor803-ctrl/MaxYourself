package com.elevate.launcher.center.ui

import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.elevate.launcher.center.content.Article
import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Starters
import com.elevate.launcher.center.data.CareRepo
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.Habit
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

// =============================================================================================
class LearnScreen(host: CenterActivity) : Screen(host) {
    override val title = "Learn"
    private var query = ""

    override fun build(): View {
        val col = host.vbox()
        col.para("Short guides that work offline. They are general information, not medical advice.", sp = 13.5f)
        val search = host.field("Search guides and exercises")
        search.setText(query)
        col.addView(search, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
        val box = host.vbox()
        col.addView(box, lp(MATCH, WRAP))
        fun fill() {
            box.removeAllViews()
            if (query.isBlank()) {
                for (g in Articles.groups) {
                    val list = Articles.inGroup(g)
                    if (list.isEmpty()) continue
                    box.heading(g, 10)
                    for (a in list) box.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
                }
                box.heading("Reference", 14)
                box.tapCard("Ingredient glossary", "What common skincare ingredients do") { host.push(GlossaryScreen(host)) }
                box.tapCard("Exercise library", "Written instructions for ${Exercises.all.size} exercises") { host.push(ExerciseLibraryScreen(host, null)) }
            } else {
                val arts = Articles.search(query)
                val exs = Exercises.search(query)
                if (arts.isEmpty() && exs.isEmpty()) box.emptyState("Nothing found", "Try a different word, like \"sleep\" or \"squat\".")
                if (arts.isNotEmpty()) box.heading("Guides", 6)
                for (a in arts) box.tapCard(a.title, a.group) { host.push(ArticleScreen(host, a)) }
                if (exs.isNotEmpty()) box.heading("Exercises", 10)
                for (e in exs) box.tapCard(e.name, e.category + " · " + e.levelName) { host.push(ExerciseDetailScreen(host, e)) }
            }
        }
        search.doAfterTextChanged { query = it?.toString() ?: ""; fill() }
        fill()
        return col
    }
}

// =============================================================================================
class ArticleScreen(host: CenterActivity, private val article: Article) : Screen(host) {
    override val title = article.title

    override fun build(): View {
        val col = host.vbox()
        col.para(article.group, sp = 12.5f, bottom = 2)
        col.para(article.summary, dim = false, sp = 15f, bottom = 6)
        for (s in article.sections) {
            col.heading(s.heading, 10)
            for (p in s.paras) col.para(p, dim = false, sp = 14.5f, bottom = 6)
        }
        col.gap(8)
        col.noteCard(Articles.DISCLAIMER)
        return col
    }
}

// =============================================================================================
class GlossaryScreen(host: CenterActivity) : Screen(host) {
    override val title = "Ingredient glossary"

    override fun build(): View {
        val col = host.vbox()
        col.para("A short plain-language guide to ingredients you will see on labels. Reactions are personal, so patch test new products.", sp = 13.5f)
        for (i in Articles.glossary) {
            col.card(8) {
                cardTitle(i.name)
                para(i.what, dim = false, sp = 13.5f, bottom = 4)
                para("Watch out: " + i.watch, sp = 12.5f, bottom = 0)
            }
        }
        col.tapCard("How to patch test", "A simple way to try a product safely") { Articles.get("patch_test")?.let { host.push(ArticleScreen(host, it)) } }
        col.noteCard(Articles.DISCLAIMER)
        return col
    }
}

// =============================================================================================
class GroomingScreen(host: CenterActivity) : Screen(host) {
    override val title = "Grooming and skincare"

    override fun build(): View {
        val col = host.vbox()
        col.noteCard("This section is about routines, hygiene and learning. The app does not rate faces or looks and does not take or analyse photos.")

        col.heading("Your routine", 6)
        val today = Days.today()
        val routine = HabitRepo.all().filter { (it.category == "Skincare" || it.category == "Grooming") && HabitRepo.scheduled(it, today) }
        if (routine.isEmpty()) {
            col.emptyState(
                "No routine yet",
                "Add morning and evening skincare as habits, then tick them off here or on your dashboard.",
                "Add starter skincare routine"
            ) {
                for (s in Starters.all.filter { it.category == "Skincare" }) {
                    if (!HabitRepo.hasName(s.name)) {
                        HabitRepo.insert(Habit(0, s.name, s.descr, s.category, s.slot, s.freq, s.days, Days.today(), s.target, s.method))
                    }
                }
                host.refresh()
            }
        } else {
            for ((slot, label) in listOf("morning" to "Morning", "anytime" to "Any time", "evening" to "Evening")) {
                val list = routine.filter { it.slot == slot }
                if (list.isEmpty()) continue
                col.para(label, sp = 13f, bottom = 4)
                for (h in list) col.habitRow(h, today, { host.refresh() }) { host.push(HabitDetailScreen(host, h.id)) }
            }
        }

        for (g in listOf("Skincare", "Hair", "Hygiene")) {
            col.heading(g, 12)
            for (a in Articles.inGroup(g)) col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
        }
        col.heading("Tools", 12)
        col.tapCard("Ingredient glossary", "${Articles.glossary.size} common ingredients explained") { host.push(GlossaryScreen(host)) }
        val n = CareRepo.list().size
        col.tapCard("Product reaction log", if (n == 0) "Note how new products treat your skin" else pluralize(n, "entry", "entries")) { host.push(CareLogScreen(host)) }
        col.noteCard(Articles.DISCLAIMER)
        return col
    }
}

// =============================================================================================
class CareLogScreen(host: CenterActivity) : Screen(host) {
    override val title = "Product reactions"
    override val actionLabel: String? = "Add"
    override fun onAction() { addDialog() }

    override fun build(): View {
        val col = host.vbox()
        col.para("A private note of how products worked for you. It helps you spot what irritates your skin.", sp = 13.5f)
        val list = CareRepo.list()
        if (list.isEmpty()) {
            col.emptyState("Nothing logged", "When you try a new product, note it here along with any reaction.", "Add an entry") { addDialog() }
        }
        for (e in list) {
            val reaction = CareRepo.reactions.firstOrNull { it.first == e.reaction }?.second ?: e.reaction
            col.tapCard(e.product, Days.pretty(e.day) + " · " + reaction + (if (e.note.isNotEmpty()) "\n" + e.note else ""), "") {
                Dialogs.confirm(host, "Delete this entry?", e.product, "Delete") {
                    CareRepo.delete(e.id)
                    host.refresh()
                }
            }
        }
        if (list.isNotEmpty()) col.para("Tap an entry to delete it.", sp = 12f)
        col.noteCard("If a product causes swelling, blistering, a spreading rash or trouble breathing, stop using it and get medical help.")
        return col
    }

    private fun addDialog() {
        val box = host.vbox()
        val product = host.field("Product name")
        var reaction = "none"
        val note = host.field("Note (optional)")
        box.fieldLabel("Product")
        box.addView(product, lp(MATCH, WRAP))
        box.fieldLabel("Reaction")
        box.addView(host.chips(listOf("none" to "None", "mild" to "Mild", "irritated" to "Irritated"), reaction) { reaction = it }, lp(MATCH, WRAP))
        box.fieldLabel("Note")
        box.addView(note, lp(MATCH, WRAP))
        host.formDialog("Add a reaction note", box) {
            val p = product.text.toString().trim()
            if (p.isEmpty()) { host.toast("Enter the product name"); false }
            else {
                CareRepo.add(Days.today(), p, reaction, note.text.toString().trim())
                host.refresh()
                true
            }
        }
    }
}
