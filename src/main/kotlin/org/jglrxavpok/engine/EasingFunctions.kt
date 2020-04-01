package org.jglrxavpok.engine

fun LinearEase(a: Float, b: Float, t: Float): Float {
    return a + t * (b - a)
}