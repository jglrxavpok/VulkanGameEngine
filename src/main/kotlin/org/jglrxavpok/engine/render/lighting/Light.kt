package org.jglrxavpok.engine.render.lighting

import java.nio.ByteBuffer

interface Light {
    val sizeOf: Long
    val type: LightType

    fun write(buffer: ByteBuffer)
}