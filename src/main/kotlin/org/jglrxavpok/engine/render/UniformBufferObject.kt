package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.math.sizeof
import org.jglrxavpok.engine.math.skip
import org.joml.Matrix4f
import java.nio.ByteBuffer

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class UniformBufferObject(): Descriptor {

    companion object {
        val SizeOf = sizeof<Matrix4f>().toLong()
    }

    val model = Matrix4f()

    fun write(buffer: ByteBuffer): ByteBuffer {
        model.get(buffer)
        buffer.skip(16*4)
        return buffer
    }

}