package org.jglrxavpok.engine

import org.joml.*
import java.nio.Buffer

/**
 * Returns the size in bytes of a given type.
 *
 * Not the JVM size, but the size used by the graphics card.
 * Probably incomplete, only used when communicating with the GPU
 */
inline fun <reified T> sizeof() =
    when(T::class) {
        Boolean::class -> 1
        Byte::class -> 1
        Short::class -> 2
        Float::class -> 4
        Int::class -> 4
        UInt::class -> 4
        Char::class -> 4
        Long::class -> 8
        Double::class -> 8

        Matrix4f::class -> 16 * 4
        Matrix3f::class -> 9 * 4
        Vector4f::class -> 4 * 4
        Vector3f::class -> 3 * 4
        Vector2f::class -> 2 * 4

        else -> {
            throw UnsupportedOperationException("Size of type ${T::class} is unknown")
        }
    }

inline fun Buffer.skip(amount: Int): Buffer {
    this.position(this.position() + amount)
    return this
}