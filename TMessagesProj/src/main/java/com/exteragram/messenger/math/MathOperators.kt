package com.exteragram.messenger.math

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.roundToLong

object MathOperators {

    private val infix = listOf(
        InfixOperator(listOf("+"), 10) { a, b, percent -> if (percent) a + b * a else a + b },
        InfixOperator(listOf("-", "−"), 10) { a, b, percent -> if (percent) a - b * a else a - b },
        InfixOperator(listOf("*", "×"), 20) { a, b, _ -> a * b },
        InfixOperator(listOf("/", "÷"), 20) { a, b, _ -> a / b },
        InfixOperator(listOf("^"), 40, rightAssociative = true) { a, b, _ -> Math.pow(a, b) },
    )

    private val prefix = listOf(
        PrefixOperator(listOf("-", "−"), 30) { -it },
        PrefixOperator(listOf("+"), 30) { it },
        PrefixOperator(listOf("√"), 40, operation = true) { Math.sqrt(it) },
    )

    private val postfix = listOf(
        PostfixOperator(listOf("%"), percent = true) { it / 100.0 },
        PostfixOperator(listOf("!")) { factorial(it) },
    )

    private val functions = listOf(
        MathFunction("sqrt", 1..1) { Math.sqrt(it[0]) },
        MathFunction("cbrt", 1..1) { Math.cbrt(it[0]) },
        MathFunction("abs", 1..1) { Math.abs(it[0]) },
        MathFunction("sign", 1..1) { Math.signum(it[0]) },
        MathFunction("round", 1..1) { it[0].roundToLong().toDouble() },
        MathFunction("floor", 1..1) { Math.floor(it[0]) },
        MathFunction("ceil", 1..1) { Math.ceil(it[0]) },
        MathFunction("fact", 1..1) { factorial(it[0]) },
        MathFunction("min", 1..Int.MAX_VALUE) { it.min() },
        MathFunction("max", 1..Int.MAX_VALUE) { it.max() },
        MathFunction("log", 1..1) { Math.log10(it[0]) },
        MathFunction("log2", 1..1) { log2(it[0]) },
        MathFunction("ln", 1..1) { Math.log(it[0]) },
        MathFunction("exp", 1..1) { Math.exp(it[0]) },
        MathFunction("sin", 1..1) { Math.sin(it[0]) },
        MathFunction("cos", 1..1) { Math.cos(it[0]) },
        MathFunction("tan", 1..1) { Math.tan(it[0]) },
        MathFunction("asin", 1..1) { Math.asin(it[0]) },
        MathFunction("acos", 1..1) { Math.acos(it[0]) },
        MathFunction("atan", 1..1) { Math.atan(it[0]) },
        MathFunction("atan2", 2..2) { Math.atan2(it[0], it[1]) },
        MathFunction("sinh", 1..1) { Math.sinh(it[0]) },
        MathFunction("cosh", 1..1) { Math.cosh(it[0]) },
        MathFunction("tanh", 1..1) { Math.tanh(it[0]) },
    )

    val constants: Map<String, Double> = mapOf(
        "pi" to PI,
        "π" to PI,
        "e" to E,
    )

    private val symbols: List<String> = buildList {
        infix.forEach { addAll(it.symbols) }
        prefix.forEach { addAll(it.symbols) }
        postfix.forEach { addAll(it.symbols) }
    }.distinct().sortedByDescending { it.length }

    val symbolChars: Set<Char> = buildSet {
        symbols.forEach { symbol -> symbol.forEach { add(it) } }
        constants.keys.forEach { name -> name.filterNot { it.isLetter() }.forEach { add(it) } }
        add('(')
        add(')')
        add(';')
    }

    fun matchSymbol(text: CharSequence, index: Int): String? = symbols.firstOrNull { symbol ->
        index + symbol.length <= text.length && text.regionMatches(index, symbol, 0, symbol.length)
    }

    fun infixFor(symbol: String): InfixOperator? = infix.firstOrNull { symbol in it.symbols }

    fun prefixFor(symbol: String): PrefixOperator? = prefix.firstOrNull { symbol in it.symbols }

    fun postfixFor(symbol: String): PostfixOperator? = postfix.firstOrNull { symbol in it.symbols }

    fun functionFor(name: String): MathFunction? = functions.firstOrNull { it.name == name }

    private fun factorial(value: Double): Double {
        if (value < 0 || value != Math.floor(value) || value > 170) {
            return Double.NaN
        }
        var result = 1.0
        for (i in 2..value.toInt()) {
            result *= i
        }
        return result
    }
}
