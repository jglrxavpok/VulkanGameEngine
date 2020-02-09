package org.jglrxavpok.engine

/**
 * Returns the size in bytes of a given type.
 *
 * Not the JVM size, but the size used by the graphics card
 */
inline fun <reified T> sizeof() =
    when(T::class) {
        Boolean::class -> 1
        Byte::class -> 1
        Short::class -> 2
        Float::class -> 4
        Int::class -> 4
        Char::class -> 4
        Long::class -> 8
        Double::class -> 8

        else -> {
            throw UnsupportedOperationException("Size of type ${T::class} is unknown")
        }
    }