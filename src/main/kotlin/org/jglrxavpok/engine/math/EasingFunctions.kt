package org.jglrxavpok.engine.math

fun LinearEase(a: Float, b: Float, t: Float): Float {
    return a + t * (b - a)
}