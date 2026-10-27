package com.elevate.launcher.privacy

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

/** Where the access code, the calculator icon and the hidden-apps list are managed. */
class PrivacyActivity : BaseActivity() {
    private lateinit var col: LinearLayout

    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            Auth.clearCode(this)
            toast("The old code is removed")
            askNewCode()
        } else {
            toast("Not confirmed. The code was not changed.")
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Auth.init(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        bar.addView(UiKit.text(this, "‹", 32f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, lp(dp(48), dp(48)))
        bar.addView(UiKit.text(this, "Hidden apps and privacy", 20f, Palette.text, true), lp(0, WRAP, 1f))
        root.addView(bar, lp(MATCH, WRAP))
        val sv = ScrollView(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(sv, lp(MATCH, 0, 1f))
        setContentView(root)
        edgeToEdge(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::col.isInitialized) render()
    }

    // ------------------------------------------------------------ building blocks
    private fun heading(t: String) {
        col.addView(UiKit.text(this, t, 17f, Palette.text, true), lp(MATCH, WRAP).apply { topMargin = dp(14); bottomMargin = dp(8) })
    }

    private fun note(t: String, dim: Boolean = true) {
        val v = UiKit.text(this, t, 13.5f, if (dim) Palette.dim else Palette.text)
        v.setLineSpacing(0f, 1.15f)
        v.background = UiKit.rounded(Palette.surface2, dpf(14f))
        v.setPadding(dp(14), dp(10), dp(14), dp(10))
        col.addView(v, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
    }

    private fun button(label: String, filled: Boolean = false, onClick: () -> Unit) {
        col.addView(UiKit.button(this, label, filled, onClick), lp(MATCH, WRAP).apply { bottomMargin = dp(8) })
    }

    private fun render() {
        col.removeAllViews()
        note("Hiding an app only removes it from Elevate's drawer, search and home screen. The app stays installed and still shows in Android Settings, other launchers and phone-wide search. This feature keeps casual eyes away. It is not strong security, and it does not protect the apps themselves.", dim = false)

        heading("Access code")
        if (Auth.hasCode(this)) {
            col.addView(UiKit.text(this, "A code is set. Type it into the Calculator and press = to open your hidden apps.", 14f, Palette.dim), lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
            button("Open hidden apps", true) { Auth.openHiddenApps(this) }
            button("Change code") { verifyThen { askNewCode(); render() } }
            button("Remove code") {
                verifyThen {
                    Auth.clearCode(this)
                    toast("Code removed. The hidden-apps list now opens without one.")
                    render()
                }
            }
            button("I forgot my code") { forgot() }
        } else {
            col.addView(UiKit.text(this, "No code is set, so anyone using Elevate can open the hidden-apps list. Set a code to put it behind the Calculator.", 14f, Palette.dim), lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
            button("Set access code", true) { askNewCode(); render() }
            button("Open hidden apps") { Auth.openHiddenApps(this) }
        }

        heading("Fingerprint or face (optional)")
        if (!Auth.hasCode(this)) {
            note("Set an access code first. Fingerprint or face is only an extra way in, and the code stays your main key.")
        } else {
            val why = Bio.unavailableReason(this)
            if (why != null) {
                note(why)
            } else if (Auth.bioOn(this)) {
                note("On. In the Calculator, press and hold the numbers at the top, then confirm with your fingerprint or face. Typing the code still works.")
                button("Turn off fingerprint / face") { Auth.setBio(this, false); render() }
            } else {
                note("Add a quicker way in. You still need the code to set it up, and the code always works. Anyone whose fingerprint or face is saved on this phone will be able to open the list.")
                button("Turn on fingerprint / face") {
                    Bio.prompt(this, "Confirm to turn on", "Use a fingerprint or face already saved on this phone") {
                        Auth.setBio(this, true)
                        toast("Turned on")
                        render()
                    }
                }
            }
        }

        heading("Calculator icon")
        note("The Calculator is a real, working calculator. Switch the icon on to get a separate \"Calculator\" app icon that opens it. The code is typed there. Elevate's own menu can also open it.")
        val on = Auth.calculatorIconOn(this)
        button(if (on) "Calculator icon: On (tap to turn off)" else "Calculator icon: Off (tap to turn on)") {
            Auth.setCalculatorIcon(this, !on)
            toast(if (!on) "Calculator icon added. It may take a moment to appear." else "Calculator icon removed")
            render()
        }

        heading("Good to know")
        note("• The code is never saved. Only a scrambled (hashed) version is kept on this phone.\n• After 5 wrong codes, unlocking pauses for a while, and the pause grows with more wrong tries. A wrong code just shows as a normal number in the calculator.\n• If you forget the code, \"I forgot my code\" asks for your phone's screen lock, then lets you set a new one. If your phone has no screen lock, anyone could do this, so set one.\n• If you clear Elevate's data or uninstall it, the code and the hidden list are lost and all apps show again.\n• Fingerprint or face unlock is optional and off until you turn it on. It uses the fingerprints or faces saved in Android, and Elevate never sees them.")
    }

    // ------------------------------------------------------------ code entry
    private fun codeBox(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(Auth.MAX_LEN))
        isSingleLine = true
    }

    /** Shows a numeric password dialog. [onCode] is called with the typed digits. */
    private fun askCode(title: String, message: String, onCode: (String) -> Unit) {
        val et = codeBox("Digits only")
        val frame = FrameLayout(this)
        frame.setPadding(dp(22), dp(8), dp(22), 0)
        frame.addView(et, FrameLayout.LayoutParams(MATCH, WRAP))
        val d = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(frame)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val v = et.text.toString()
            if (v.isEmpty()) { toast("Enter the code"); return@setOnClickListener }
            d.dismiss()
            onCode(v)
        }
    }

    private fun verifyThen(onOk: () -> Unit) {
        askCode("Enter your current code", "") { code ->
            when (Auth.verify(this, code)) {
                Auth.Result.OK -> onOk()
                Auth.Result.WRONG -> toast("That code is not right")
                Auth.Result.LOCKED -> toast("Too many wrong tries. Try again in ${Auth.lockedSeconds(this)} seconds.")
            }
        }
    }

    private fun askNewCode() {
        askCode("New access code", "Choose ${Auth.MIN_LEN} to ${Auth.MAX_LEN} digits. Pick something you will remember but others will not guess.") { first ->
            if (!Auth.validCode(first)) {
                toast("The code must be ${Auth.MIN_LEN} to ${Auth.MAX_LEN} digits")
                return@askCode
            }
            askCode("Repeat the code", "") { second ->
                if (second != first) {
                    toast("The two codes did not match")
                } else {
                    Auth.setCode(this, first)
                    toast("Code saved")
                    render()
                }
            }
        }
    }

    private fun forgot() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Reset the hidden-apps code", "Confirm your phone's screen lock to remove the old code.") else null
        if (intent != null) {
            credential.launch(intent)
            return
        }
        Dialogs.confirm(
            this, "Remove the code?",
            "Your phone has no screen lock, so Elevate cannot check that it is really you. If you continue, the old code is removed and you can set a new one. Your hidden apps stay hidden.",
            "Remove code"
        ) {
            Auth.clearCode(this)
            askNewCode()
            render()
        }
    }
}
