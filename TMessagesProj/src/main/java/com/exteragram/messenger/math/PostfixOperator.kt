package com.exteragram.messenger.math

class PostfixOperator(
    val symbols: List<String>,
    val percent: Boolean = false,
    val apply: (Double) -> Double,
)
