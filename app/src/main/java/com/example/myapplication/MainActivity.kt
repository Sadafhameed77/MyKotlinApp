package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class MainActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var calculatorGrid: GridLayout

    // The expression the user is typing (uses display symbols: × ÷ − π √)
    private var expression = ""

    // True right after pressing "=" so the next digit starts a fresh calculation
    private var justEvaluated = false

    // Angle mode. Step 3 will add a DEG/RAD toggle button for this.
    private var isDegree = true

    private val operators = setOf('+', '−', '-', '×', '÷', '*', '/', '^')
    private val functionTokens = listOf("sin(", "cos(", "tan(", "log(", "ln(", "√(")

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)
        calculatorGrid = findViewById(R.id.calculatorGrid)

        // One loop wires every button, using its text as the key
        for (i in 0 until calculatorGrid.childCount) {
            val child = calculatorGrid.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener { onButtonClick(child.text.toString()) }
            }
        }

        savedInstanceState?.let {
            expression = it.getString("expression") ?: ""
            justEvaluated = it.getBoolean("justEvaluated", false)
            isDegree = it.getBoolean("isDegree", true)
            tvResult.text = it.getString("result") ?: "0"
            tvExpression.text = it.getString("expressionText") ?: expression
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expression", expression)
        outState.putBoolean("justEvaluated", justEvaluated)
        outState.putBoolean("isDegree", isDegree)
        outState.putString("result", tvResult.text.toString())
        outState.putString("expressionText", tvExpression.text.toString())
    }

    // ---------------------------------------------------------------------
    // Button handling
    // ---------------------------------------------------------------------

    private fun onButtonClick(label: String) {
        // After an error, any button except AC/DEL starts fresh
        if (tvResult.text.toString() == "Error" && label != "AC" && label != "DEL") {
            clearAll()
        }

        when (label) {
            "AC" -> clearAll()
            "DEL" -> deleteLast()
            "=" -> calculateFinal()
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> appendDigit(label)
            "." -> appendDot()
            "+", "−", "×", "÷" -> appendOperator(label[0])
            "sin", "cos", "tan", "log", "ln" -> appendOpening("$label(")
            "√" -> appendOpening("√(")
            "(" -> appendOpening("(")
            ")" -> appendCloseBracket()
            "π" -> appendConstant("π")
            "x²" -> appendPostfix("^2")
            "xʸ" -> appendPostfix("^")
        }
    }

    private fun startFreshIfNeeded() {
        if (justEvaluated) {
            expression = ""
            justEvaluated = false
        }
    }

    private fun appendDigit(d: String) {
        startFreshIfNeeded()
        expression += d
        refresh()
    }

    private fun appendDot() {
        startFreshIfNeeded()
        // Find the current number and make sure it has no dot yet
        var i = expression.length - 1
        while (i >= 0 && (expression[i].isDigit() || expression[i] == '.')) i--
        val currentNumber = expression.substring(i + 1)
        if (currentNumber.contains('.')) return
        expression += if (currentNumber.isEmpty()) "0." else "."
        refresh()
    }

    private fun appendOperator(op: Char) {
        justEvaluated = false
        if (expression.isEmpty()) {
            // Only a minus sign may start an expression
            if (op == '−') {
                expression = "−"
                refresh()
            }
            return
        }
        val last = expression.last()
        if (last == '(' && op != '−') return          // nothing to operate on
        if (last in operators) {
            // Replace the previous operator
            expression = expression.dropLast(1) + op
        } else {
            expression += op
        }
        refresh()
    }

    private fun appendOpening(token: String) {
        startFreshIfNeeded()
        expression += token
        refresh()
    }

    private fun appendCloseBracket() {
        val open = expression.count { it == '(' }
        val close = expression.count { it == ')' }
        if (open <= close) return
        val last = expression.lastOrNull() ?: return
        if (last == '(' || last in operators) return
        expression += ")"
        refresh()
    }

    private fun appendConstant(c: String) {
        startFreshIfNeeded()
        expression += c
        refresh()
    }

    // For x² and xʸ: only valid after a number, ")" or π
    private fun appendPostfix(token: String) {
        val last = expression.lastOrNull() ?: return
        if (!(last.isDigit() || last == ')' || last == 'π')) return
        justEvaluated = false
        expression += token
        refresh()
    }

    private fun deleteLast() {
        if (justEvaluated) {
            clearAll()
            return
        }
        if (expression.isEmpty()) return
        val token = functionTokens.firstOrNull { expression.endsWith(it) }
        expression = if (token != null) {
            expression.dropLast(token.length)
        } else {
            expression.dropLast(1)
        }
        if (expression.isEmpty()) {
            tvExpression.text = ""
            tvResult.text = "0"
        } else {
            refresh()
        }
    }

    private fun clearAll() {
        expression = ""
        justEvaluated = false
        tvExpression.text = ""
        tvResult.text = "0"
    }

    // ---------------------------------------------------------------------
    // Calculation
    // ---------------------------------------------------------------------

    // Updates the expression line and shows a live result while typing
    private fun refresh() {
        tvExpression.text = expression
        if (expression.isEmpty()) {
            tvResult.text = "0"
            return
        }
        try {
            tvResult.text = formatResult(evaluate(expression))
        } catch (e: Exception) {
            // Incomplete expression (e.g. "5+"): keep showing the last good result
        }
    }

    private fun calculateFinal() {
        if (expression.isEmpty()) return
        try {
            val result = formatResult(evaluate(expression))
            tvExpression.text = "$expression ="
            tvResult.text = result
            expression = result
            justEvaluated = true
        } catch (e: Exception) {
            tvResult.text = "Error"
        }
    }

    private fun evaluate(expr: String): Double {
        val value = ExpressionParser(expr, isDegree).parse()
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("Invalid result")
        return value
    }

    private fun formatResult(value: Double): String {
        if (value == 0.0) return "0"
        val bd = BigDecimal(value).round(MathContext(12)).stripTrailingZeros()
        if (bd.signum() == 0) return "0"
        val absValue = abs(value)
        return if (absValue >= 1e12 || absValue < 1e-6) {
            bd.toString()          // scientific, e.g. 1E+15
        } else {
            bd.toPlainString()
        }
    }

    // ---------------------------------------------------------------------
    // Expression parser (recursive descent)
    //
    //   expression = term   { ("+" | "−") term }
    //   term       = factor { ("×" | "÷" | implicit multiply) factor }
    //   factor     = ["+" | "−"] power
    //   power      = primary [ "^" factor ]
    //   primary    = number | "π" | "(" expression ")" | "√" primary
    //              | function "(" expression ")"
    //
    // This gives correct precedence: 2+3×4 = 14, −2^2 = −4, 2^3^2 = 512
    // ---------------------------------------------------------------------

    private class ExpressionParser(source: String, private val degrees: Boolean) {

        private val s = source.replace(" ", "")
        private var pos = 0

        fun parse(): Double {
            val value = parseExpression()
            if (pos < s.length) throw IllegalArgumentException("Unexpected '${s[pos]}'")
            return value
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '−', '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                val c = peek()
                when {
                    c == '×' || c == '*' -> { pos++; value *= parseFactor() }
                    c == '÷' || c == '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Divide by zero")
                        value /= divisor
                    }
                    // Implicit multiplication: 2π, 3(4+1), 2sin(30), (1+2)(3+4)
                    c != null && (c.isDigit() || c == '.' || c == '(' || c == 'π' ||
                            c == '√' || c.isLetter()) -> {
                        value *= parsePower()
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            return when (peek()) {
                '+' -> { pos++; parseFactor() }
                '−', '-' -> { pos++; -parseFactor() }
                else -> parsePower()
            }
        }

        private fun parsePower(): Double {
            val base = parsePrimary()
            if (peek() == '^') {
                pos++
                val exponent = parseFactor()   // right-associative
                return base.pow(exponent)
            }
            return base
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw IllegalArgumentException("Unexpected end")

            // Number
            if (c.isDigit() || c == '.') return parseNumber()

            // Bracket (a missing ")" at the very end is allowed, e.g. "2×(3+4")
            if (c == '(') {
                pos++
                val value = parseExpression()
                if (peek() == ')') pos++
                else if (pos < s.length) throw IllegalArgumentException("Expected ')'")
                return value
            }

            // Constant
            if (c == 'π') { pos++; return Math.PI }

            // Square root
            if (c == '√') {
                pos++
                val x = parsePrimary()
                if (x < 0) throw ArithmeticException("Negative root")
                return sqrt(x)
            }

            // Functions: sin cos tan log ln
            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos)
                if (peek() != '(') throw IllegalArgumentException("Expected '(' after $name")
                pos++
                val arg = parseExpression()
                if (peek() == ')') pos++
                else if (pos < s.length) throw IllegalArgumentException("Expected ')'")
                return applyFunction(name, arg)
            }

            throw IllegalArgumentException("Unexpected '$c'")
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            // Optional exponent like 1E+15 (produced by large results)
            if (pos < s.length && s[pos] == 'E') {
                var p = pos + 1
                if (p < s.length && (s[p] == '+' || s[p] == '-')) p++
                if (p < s.length && s[p].isDigit()) {
                    while (p < s.length && s[p].isDigit()) p++
                    pos = p
                }
            }
            return s.substring(start, pos).toDouble()
        }

        private fun applyFunction(name: String, x: Double): Double {
            val angle = if (degrees) Math.toRadians(x) else x
            return when (name) {
                "sin" -> cleanTrig(sin(angle))
                "cos" -> cleanTrig(cos(angle))
                "tan" -> {
                    if (abs(cos(angle)) < 1e-12) throw ArithmeticException("tan undefined")
                    cleanTrig(tan(angle))
                }
                "log" -> {
                    if (x <= 0) throw ArithmeticException("log domain")
                    log10(x)
                }
                "ln" -> {
                    if (x <= 0) throw ArithmeticException("ln domain")
                    ln(x)
                }
                else -> throw IllegalArgumentException("Unknown function $name")
            }
        }

        // Turns tiny floating-point noise (like sin(180°) = 1.2e-16) into 0
        private fun cleanTrig(v: Double): Double = if (abs(v) < 1e-12) 0.0 else v
    }
}