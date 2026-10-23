package com.elevate.launcher.core

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog

/** Small reusable dialogs. */
object Dialogs {
    fun prompt(ctx: Context, title: String, initial: String, hint: String = "", number: Boolean = false, onOk: (String) -> Unit) {
        val et = EditText(ctx).apply {
            setText(initial)
            setSelection(text.length)
            this.hint = hint
            isSingleLine = true
            inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val frame = FrameLayout(ctx).apply {
            setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(22), 0)
            addView(et, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirm(ctx: Context, title: String, message: String, okLabel: String = "OK", onOk: () -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(okLabel) { _, _ -> onOk() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun info(ctx: Context, title: String, message: String) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    fun list(ctx: Context, title: String?, items: List<String>, onPick: (Int) -> Unit) {
        val b = AlertDialog.Builder(ctx)
        if (title != null) b.setTitle(title)
        b.setItems(items.toTypedArray()) { _, which -> onPick(which) }.show()
    }
}
