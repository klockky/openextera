package com.exteragram.messenger.math

/**
 * [apply] receives the left operand, the right operand and whether the right operand is a percentage.
 */
class InfixOperator(
    val symbols: List<String>,
    val precedence: Int,
    val rightAssociative: Boolean = false,
    val apply: (Double, Double, Boolean) -> Double,
)
