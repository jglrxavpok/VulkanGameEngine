package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

class DirectionalLight: Light {
    override val type = LightType.Directional

    val direction = Vector3f()
    val color = Vector4f()

    override val sizeOf: Long = sizeof<Vector3f>().toLong()

    override fun write(buffer: ByteBuffer) {
        val buffer = buffer.asFloatBuffer()
        direction.get(buffer)
        buffer.skip(sizeof<Vector3f>())
        color.get(buffer)
        buffer.skip(sizeof<Vector4f>())
    }
}