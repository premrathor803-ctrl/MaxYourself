package com.elevate.launcher.privacy

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A working calculator. If an access code has been set, typing exactly that code and pressing "=" opens the
 * private hidden-apps list. Any other entry is just a calculation, and nothing on screen says a code exists.
 */
class CalculatorActivity : BaseActivity() {
    private val expr = StringBuilder()
    private var history = ""
    private lateinit var exprView: TextView
    private lateinit var histView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Auth.init(this)
        // Test hook used only by the automated emulator test (the activity is not exported).
        intent.getStringExtra("ci_setcode")?.let { if (!Auth.hasCode(this)) Auth.setCode(this, it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        histView = UiKit.text(this, "", 15f, Palette.dim).apply { gravity = Gravity.END; isSingleLine = true }
        exprView = UiKit.text(this, "0", 48f, Palette.text, false).apply {
            gravity = Gravity.END
            maxLines = 1
        }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(exprView, 24, 56, 2, TypedValue.COMPLEX_UNIT_SP)
        display.addView(histView, lp(MATCH, WRAP))
        display.addView(exprView, lp(MATCH, WRAP))
        // Long-pressing the numbers asks for fingerprint or face, only if that was switched on in the privacy settings.
        display.setOnLongClickListener {
            if (Auth.bioOn(this)) { bioUnlock(); true } else false
        }
        root.addView(display, lp(MATCH, 0, 2f))

        val rows = listOf(
            listOf("C", "+/-", "%", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("DEL", "0", ".", "=")
        )
        val pad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) row.addView(key(k), lp(0, MATCH, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            pad.addView(row, lp(MATCH, 0, 1f))
        }
        root.addView(pad, lp(MATCH, 0, 5f))
        setContentView(root)
        edgeToEdge(root)
        render()
    }

    private fun bioUnlock() {
        Bio.prompt(this, "Confirm it is you", "Opens your private list") {
            Auth.unlockSession()
            val i = Intent(this, HiddenAppsActivity::class.java)
            intent.getStringExtra("ci_nosecure")?.let { i.putExtra("ci_nosecure", it) }
            startActivity(i)
        }
    }

    private fun isOp(c: Char) = c == '+' || c == '-' || c == '*' || c == '/'

    private fun key(k: String): TextView {
        val op = k == "/" || k == "*" || k == "-" || k == "+"
        val eq = k == "="
        val label = when (k) {
            "/" -> "÷"
            "*" -> "×"
            "-" -> "−"
            else -> k
        }
        val desc = when (k) {
            "/" -> "divide"
            "*" -> "multiply"
            "-" -> "minus"
            "+" -> "plus"
            "=" -> "equals"
            "." -> "point"
            "C" -> "clear"
            "DEL" -> "delete"
            "+/-" -> "change sign"
            "%" -> "percent"
            else -> k
        }
        val bg = when {
            eq -> Palette.accent
            op -> Palette.surface
            else -> Palette.surface2
        }
        val fg = when {
            eq -> Palette.onAccent
            op -> Palette.accent
            else -> Palette.text
        }
        return UiKit.text(this, label, if (k == "DEL" || k == "+/-") 18f else 26f, fg, op || eq).apply {
            gravity = Gravity.CENTER
            background = UiKit.pressable(bg, dpf(20f))
            contentDescription = desc
            setOnClickListener { press(k) }
        }
    }

    // ------------------------------------------------------------ input handling
    private fun currentNumberStart(): Int {
        var i = expr.length
        while (i > 0 && (expr[i - 1].isDigit() || expr[i - 1] == '.')) i--
        // a leading unary minus belongs to the number
        if (i > 0 && expr[i - 1] == '-' && (i - 1 == 0 || isOp(expr[i - 2]))) i--
        return i
    }

    private fun press(k: String) {
        when (k) {
            "C" -> { expr.setLength(0); history = "" }
            "DEL" -> if (expr.isNotEmpty()) expr.setLength(expr.length - 1)
            "+/-" -> {
                val s = currentNumberStart()
                if (s < expr.length || expr.isEmpty()) {
                    if (s < expr.length && expr[s] == '-') expr.deleteCharAt(s) else expr.insert(s, '-')
                }
            }
            "%" -> {
                val s = currentNumberStart()
                val n = expr.substring(s).toBigDecimalOrNull()
                if (n != null) {
                    expr.setLength(s)
                    expr.append(format(n.divide(BigDecimal(100), MathContext.DECIMAL64)))
                }
            }
            "." -> {
                val s = currentNumberStart()
                val cur = expr.substring(s)
                if (!cur.contains('.') && expr.length < MAX_INPUT) {
                    if (cur.isEmpty() || cur == "-") expr.append("0")
                    expr.append('.')
                }
            }
            "+", "-", "*", "/" -> {
                val c = k[0]
                if (expr.isEmpty()) {
                    if (c == '-') expr.append('-')
                } else if (isOp(expr.last())) {
                    if (expr.length > 1 || c == '-') expr.setCharAt(expr.length - 1, c)
                } else if (expr.last() != '-' && expr.length < MAX_INPUT) {
                    expr.append(c)
                }
            }
            "=" -> equals()
            else -> if (k.length == 1 && k[0].isDigit() && expr.length < MAX_INPUT) {
                val s = currentNumberStart()
                if (expr.substring(s) == "0") expr.setLength(expr.length - 1)
                else if (expr.substring(s) == "-0") expr.setLength(expr.length - 1)
                expr.append(k)
            }
        }
        render()
    }

    private fun equals() {
        val text = expr.toString()
        if (text.isEmpty()) return
        if (Auth.hasCode(this) && text.all { it.isDigit() } && text.length == Auth.codeLength(this)) {
            if (Auth.verify(this, text) == Auth.Result.OK) {
                expr.setLength(0)
                history = ""
                render()
                Auth.unlockSession()
                val i = Intent(this, HiddenAppsActivity::class.java)
                intent.getStringExtra("ci_nosecure")?.let { i.putExtra("ci_nosecure", it) }
                startActivity(i)
                return
            }
            // A wrong code (or a lock-out) falls through and is shown as a plain number, like any calculation.
        }
        val result = evaluate(text)
        history = show(text) + " ="
        expr.setLength(0)
        if (result != null) expr.append(result)
    }

    private var exprError: String? = null

    private fun render() {
        val err = exprError
        if (err != null && expr.isEmpty()) {
            exprView.text = err
            exprError = null
        } else {
            exprView.text = if (expr.isEmpty()) "0" else show(expr.toString())
        }
        histView.text = history
    }

    private fun show(s: String): String = s.replace('*', '×').replace('/', '÷').replace('-', '−')

    // ------------------------------------------------------------ arithmetic
    private fun format(n: BigDecimal): String {
        val r = n.round(MathContext(12, RoundingMode.HALF_UP))
        val s = r.stripTrailingZeros().toPlainString()
        return if (s == "-0") "0" else s
    }

    /** Evaluates +, -, *, / with the usual precedence. Returns null on division by zero or bad input. */
    private fun evaluate(input: String): String? {
        var s = input
        while (s.isNotEmpty() && (isOp(s.last()) || s.last() == '.')) s = s.substring(0, s.length - 1)
        if (s.isEmpty()) return "0"
        val tokens = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val unary = c == '-' && (i == 0 || isOp(s[i - 1]))
            if (c.isDigit() || c == '.' || unary) {
                val st = i
                i++
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                tokens.add(s.substring(st, i))
            } else {
                tokens.add(c.toString())
                i++
            }
        }
        return try {
            var pos = 0
            fun num(): BigDecimal = BigDecimal(tokens[pos++])
            fun term(): BigDecimal {
                var v = num()
                while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                    val op = tokens[pos++]
                    val r = num()
                    if (op == "*") v = v.multiply(r, MathContext.DECIMAL64)
                    else {
                        if (r.signum() == 0) throw ArithmeticException()
                        v = v.divide(r, MathContext.DECIMAL64)
                    }
                }
                return v
            }
            var v = term()
            while (pos < tokens.size) {
                val op = tokens[pos++]
                val r = term()
                v = if (op == "+") v.add(r) else v.subtract(r)
            }
            format(v)
        } catch (e: ArithmeticException) {
            exprError = "Can't divide by zero"
            null
        } catch (e: Exception) {
            exprError = "Error"
            null
        }
    }

    companion object {
        private const val MAX_INPUT = 24
    }
}
