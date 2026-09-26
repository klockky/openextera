package com.exteragram.messenger.math

class PrefixOperator(
    val symbols: List<String>,
    val precedence: Int,
    val operation: Boolean = false,
    val apply: (Double) -> Double,
)
