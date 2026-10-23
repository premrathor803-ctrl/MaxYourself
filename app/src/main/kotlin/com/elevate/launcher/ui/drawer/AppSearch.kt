package com.elevate.launcher.ui.drawer

import com.elevate.launcher.data.AppEntry

/** App search: prefix / word-start / contains / package / alias / fuzzy matching. */
object AppSearch {
    /** Each entry: search words -> label or package fragments they should also find. */
    private val aliases: List<Pair<List<String>, List<String>>> = listOf(
        listOf("gallery", "photos", "pictures", "pics", "images") to listOf("gallery", "photos", "photo"),
        listOf("browser", "internet", "web", "net") to listOf("chrome", "firefox", "browser", "brave", "edge", "opera", "internet"),
        listOf("message", "messages", "sms", "text", "chat") to listOf("messages", "messaging", "sms", "mms"),
        listOf("call", "dialer", "phone", "dial") to listOf("phone", "dialer", "contacts"),
        listOf("mail", "email", "inbox") to listOf("gmail", "mail", "outlook", "email"),
        listOf("music", "songs", "audio") to listOf("music", "spotify", "player", "audio"),
        listOf("video", "movies", "tv") to listOf("youtube", "video", "vlc", "netflix", "prime"),
        listOf("map", "maps", "navigate", "directions") to listOf("maps", "navigation", "waze"),
        listOf("clock", "alarm", "timer", "stopwatch") to listOf("clock", "alarm", "timer"),
        listOf("calculator", "calc", "math") to listOf("calculator", "calc"),
        listOf("settings", "config", "preferences", "setup") to listOf("settings"),
        listOf("files", "file", "storage", "documents", "downloads") to listOf("files", "file manager", "documents", "downloads"),
        listOf("store", "market", "install", "download apps") to listOf("play store", "store", "market", "vending"),
        listOf("camera", "selfie", "photo") to listOf("camera"),
        listOf("notes", "memo", "todo", "notepad") to listOf("notes", "keep", "memo", "notepad"),
        listOf("pay", "wallet", "upi", "money") to listOf("pay", "wallet", "gpay", "phonepe", "paytm")
    )

    fun rank(query: String, apps: List<AppEntry>): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val targets = if (q.length >= 2) aliases.filter { (keys, _) -> keys.any { it.startsWith(q) || q.startsWith(it) } }.flatMap { it.second } else emptyList()
        val scored = ArrayList<Pair<Int, AppEntry>>()
        for (e in apps) {
            val s = score(q, e, targets)
            if (s > 0) scored.add(s to e)
        }
        return scored.sortedWith(compareByDescending<Pair<Int, AppEntry>> { it.first }.thenBy { it.second.displayLabel.lowercase() }).map { it.second }
    }

    private fun score(q: String, e: AppEntry, aliasTargets: List<String>): Int {
        val label = e.displayLabel.lowercase()
        val original = e.label.lowercase()
        if (label == q || original == q) return 100
        if (label.startsWith(q) || original.startsWith(q)) return 90
        val words = label.split(' ', '-', '_', '.')
        if (words.any { it.startsWith(q) }) return 80
        if (aliasTargets.isNotEmpty() && aliasTargets.any { label.contains(it) || e.pkg.lowercase().contains(it.replace(" ", "")) }) return 70
        if (label.contains(q) || original.contains(q)) return 60
        if (q.length >= 3 && e.pkg.lowercase().contains(q)) return 40
        if (q.length >= 2 && subsequence(q, label)) return 30
        return 0
    }

    /** "fb" matches "facebook": all characters appear in order. */
    private fun subsequence(q: String, s: String): Boolean {
        var i = 0
        for (ch in s) if (i < q.length && ch == q[i]) i++
        return i == q.length
    }
}
