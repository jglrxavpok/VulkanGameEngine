package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.joml.Vector3f
import java.nio.ByteBuffer

open class DirectionalLight: Light() {
    override val type = LightType.Directional

    open val direction = Vector3f().set(1f)
    open val color = Vector3f().set(1f)

    override fun write(buffer: ByteBuffer, viewMatrix: Matrix4f) {
        val tmp by lazy { Vector3f() }
        // put direction in view-space, as every computation is done in that space
        // do it on the CPU once to avoid doing it for each pixel on the GPU
        viewMatrix.transformDirection(direction, tmp)
        tmp.get(buffer)
        buffer.skip(sizeof<Vector3f>()+ sizeof<Float>())

        color.get(buffer)
        buffer.skip(sizeof<Vector3f>())

        buffer.putFloat(intensity)
    }

    object None: DirectionalLight() {
        override val direction: Vector3f
            get() = Vector3f()
        override val color: Vector3f
            get() = Vector3f()
    }

    companion object {
        val SizeOf =
            sizeof<Vector3f>() + // direction
                    sizeof<Float>() + // padding
                    sizeof<Vector3f>() + // color
                    sizeof<Float>() // intensity
    }

}