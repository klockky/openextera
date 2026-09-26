package com.exteragram.messenger.math

import java.util.Locale

object MathExpression {

    private const val MAX_EXPRESSION_LOOKBEHIND = 64
    private const val MAX_DEPTH = 32

    private const val TOKEN_NUMBER = 0
    private const val TOKEN_OPERATOR = 1
    private const val TOKEN_LEFT_PAREN = 2
    private const val TOKEN_RIGHT_PAREN = 3
    private const val TOKEN_IDENTIFIER = 4
    private const val TOKEN_SEPARATOR = 5
    private const val TOKEN_END = 6

    private val error = MathParseError()

    class MathParseError : RuntimeException(null, null, false, false)

    class Token(val type: Int, val number: Double = 0.0, val text: String = "")

    class Operand(val value: Double, val isPercent: Boolean)

    private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t' || c == ' '

    fun evaluate(text: CharSequence, options: MathOptions): MathResult? = try {
        Parser(text, options).parse()
    } catch (e: MathParseError) {
        null
    }

    /**
     * Returns a suggestion when the character right before [cursor] is `=` and the text before it
     * is a math expression, e.g. `2+2=` suggests `4`.
     */
    fun suggestionAt(text: CharSequence, cursor: Int, options: MathOptions): MathSuggestion? {
        if (cursor <= 0 || cursor > text.length) {
            return null
        }
        val equalsIndex = cursor - 1
        if (text[equalsIndex] != '=') {
            return null
        }
        var after = cursor
        while (after < text.length && isBlank(text[after])) {
            after++
        }
        if (after < text.length) {
            val c = text[after]
            if (Character.isDigit(c) || c == '.' || c == ',') {
                return null
            }
        }
        var start = expressionStart(text, equalsIndex) ?: return null
        while (true) {
            if (!looksLikeDate(text, start, equalsIndex)) {
                val result = evaluate(StringBuilder(equalsIndex - start).append(text, start, equalsIndex), options)
                if (result != null) {
                    if (!result.hasOperation) {
                        return null
                    }
                    val value = MathFormat.format(result.value, result.decimalSeparator) ?: return null
                    val insertText = if (isBlank(text[cursor - 2])) " $value" else value
                    return MathSuggestion(cursor, insertText, value)
                }
            }
            start = nextCandidate(text, start, equalsIndex) ?: return null
        }
    }

    private fun looksLikeDate(text: CharSequence, from: Int, to: Int): Boolean {
        var start = from
        var end = to
        while (start < end && isBlank(text[start])) {
            start++
        }
        while (end > start && isBlank(text[end - 1])) {
            end--
        }
        if (end - start < 6) {
            return false
        }
        val groups = ArrayList<Int>(4)
        var digits = 0
        var delimiter = ' '
        for (i in start until end) {
            val c = text[i]
            if (Character.isDigit(c)) {
                digits++
                continue
            }
            if (c != '/' && c != '.' && c != '-') {
                return false
            }
            if (delimiter == ' ') {
                delimiter = c
            } else if (delimiter != c) {
                return false
            }
            if (digits == 0 || groups.size == 2) {
                return false
            }
            groups.add(digits)
            digits = 0
        }
        if (digits == 0 || groups.size != 2) {
            return false
        }
        groups.add(digits)
        return when (delimiter) {
            '-' -> groups[0] == 4 && groups[1] <= 2 && groups[2] <= 2
            '.', '/' -> groups[0] <= 2 && groups[1] <= 2 && (groups[2] == 2 || groups[2] == 4)
            else -> false
        }
    }

    private fun nextCandidate(text: CharSequence, from: Int, end: Int): Int? {
        var i = from
        while (i < end) {
            val c = text[i++]
            if (c == '(') {
                return i
            }
            if (isBlank(c)) {
                while (i < end && isBlank(text[i])) {
                    i++
                }
                return if (i < end) i else null
            }
        }
        return null
    }

    private fun expressionStart(text: CharSequence, end: Int): Int? {
        val limit = maxOf(0, end - MAX_EXPRESSION_LOOKBEHIND)
        var i = end - 1
        while (i >= limit && isExpressionChar(text[i])) {
            i--
        }
        do {
            i++
        } while (i < end && isBlank(text[i]))
        if (i >= end) {
            return null
        }
        if (i > 0) {
            val previous = text[i - 1]
            if (Character.isLetterOrDigit(previous) || previous == '_') {
                return null
            }
        }
        return i
    }

    private fun isExpressionChar(c: Char): Boolean =
        Character.isDigit(c) || Character.isLetter(c) || c == '.' || c == ',' || isBlank(c) || c in MathOperators.symbolChars

    class Parser(private val source: CharSequence, private val options: MathOptions) {

        private val tokens = ArrayList<Token>()
        private var position = 0
        private var depth = 0
        private var hasOperation = false
        private var separator: Char? = null

        fun parse(): MathResult {
            tokenize()
            val result = parseExpression(0)
            expect(TOKEN_END)
            return MathResult(result.value, hasOperation, separator ?: options.decimalSeparator)
        }

        private fun tokenize() {
            var i = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    isBlank(c) -> i++
                    Character.isDigit(c) || ((c == '.' || c == ',') && i + 1 < source.length && Character.isDigit(source[i + 1])) -> {
                        i = readNumber(i)
                    }
                    c == '(' -> {
                        tokens.add(Token(TOKEN_LEFT_PAREN))
                        i++
                    }
                    c == ')' -> {
                        tokens.add(Token(TOKEN_RIGHT_PAREN))
                        i++
                    }
                    c == ';' -> {
                        tokens.add(Token(TOKEN_SEPARATOR))
                        i++
                    }
                    Character.isLetter(c) -> {
                        var end = i
                        while (end < source.length && (Character.isLetter(source[end]) || Character.isDigit(source[end]))) {
                            end++
                        }
                        tokens.add(Token(TOKEN_IDENTIFIER, text = source.subSequence(i, end).toString().lowercase(Locale.ROOT)))
                        i = end
                    }
                    else -> {
                        val symbol = MathOperators.matchSymbol(source, i) ?: throw error
                        tokens.add(Token(TOKEN_OPERATOR, text = symbol))
                        i += symbol.length
                    }
                }
            }
            tokens.add(Token(TOKEN_END))
        }

        private fun readNumber(start: Int): Int {
            var i = start
            while (i < source.length && Character.isDigit(source[i])) {
                i++
            }
            val integerDigits = i - start
            val c = if (i < source.length) source[i] else ' '
            var fractionDigits = 0
            if ((c == '.' || c == ',') && i + 1 < source.length && Character.isDigit(source[i + 1])) {
                val fractionStart = i + 1
                i = fractionStart
                while (i < source.length && Character.isDigit(source[i])) {
                    i++
                }
                fractionDigits = i - fractionStart
                // "1,000" is a thousands separator rather than a decimal one
                if (c == ',' && fractionDigits == 3 && integerDigits in 1..3 && source[start] != '0') {
                    throw error
                }
                if (separator == null) {
                    separator = c
                }
            }
            if (integerDigits == 0 && fractionDigits == 0) {
                throw error
            }
            val value = source.subSequence(start, i).toString().replace(',', '.').toDoubleOrNull() ?: throw error
            tokens.add(Token(TOKEN_NUMBER, number = value))
            return i
        }

        private fun peek(): Token = tokens[position]

        private fun next(): Token = tokens[position++]

        private fun expect(type: Int): Token {
            val token = next()
            if (token.type != type) {
                throw error
            }
            return token
        }

        private fun parseExpression(minPrecedence: Int): Operand {
            if (++depth > MAX_DEPTH) {
                throw error
            }
            try {
                var left = parseUnary()
                while (true) {
                    val token = peek()
                    if (token.type != TOKEN_OPERATOR) {
                        break
                    }
                    val operator = MathOperators.infixFor(token.text) ?: break
                    if (operator.precedence < minPrecedence) {
                        break
                    }
                    next()
                    val right = parseExpression(if (operator.rightAssociative) operator.precedence else operator.precedence + 1)
                    hasOperation = true
                    left = Operand(operator.apply(left.value, right.value, right.isPercent), false)
                }
                return left
            } finally {
                depth--
            }
        }

        private fun parseUnary(): Operand {
            val token = peek()
            if (token.type == TOKEN_OPERATOR) {
                val operator = MathOperators.prefixFor(token.text)
                if (operator != null) {
                    next()
                    val operand = parseExpression(operator.precedence)
                    if (operator.operation) {
                        hasOperation = true
                    }
                    return Operand(operator.apply(operand.value), operand.isPercent)
                }
            }
            return parsePostfix()
        }

        private fun parsePostfix(): Operand {
            var value = parsePrimary()
            var percent = false
            while (true) {
                val token = peek()
                if (token.type != TOKEN_OPERATOR) {
                    break
                }
                val operator = MathOperators.postfixFor(token.text) ?: break
                next()
                value = operator.apply(value)
                hasOperation = true
                percent = operator.percent
            }
            return Operand(value, percent)
        }

        private fun parsePrimary(): Double {
            val token = next()
            return when (token.type) {
                TOKEN_NUMBER -> token.number
                TOKEN_LEFT_PAREN -> {
                    val value = parseExpression(0).value
                    expect(TOKEN_RIGHT_PAREN)
                    value
                }
                TOKEN_IDENTIFIER -> parseIdentifier(token.text)
                else -> throw error
            }
        }

        private fun parseIdentifier(name: String): Double {
            MathOperators.constants[name]?.let { return it }
            val function = MathOperators.functionFor(name) ?: throw error
            expect(TOKEN_LEFT_PAREN)
            val args = ArrayList<Double>(2)
            if (peek().type != TOKEN_RIGHT_PAREN) {
                args.add(parseExpression(0).value)
                while (peek().type == TOKEN_SEPARATOR) {
                    next()
                    args.add(parseExpression(0).value)
                }
            }
            expect(TOKEN_RIGHT_PAREN)
            if (args.size !in function.arity) {
                throw error
            }
            hasOperation = true
            return function.apply(args.toDoubleArray())
        }
    }
}
