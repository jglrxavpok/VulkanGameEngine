package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.math.sizeof
import org.jglrxavpok.engine.math.skip
import org.joml.Matrix4f
import org.joml.Vector3f
import java.nio.ByteBuffer

open class PointLight: Light(), PositionableLight {
    override val type = LightType.Point

    override val position = Vector3f().set(0f)
    open val color = Vector3f().set(1f)

    var attenuationConstant = 1f
    var attenuationLinear = 1f
    var attenuationQuadratic = 1f

    override fun write(buffer: ByteBuffer, viewMatrix: Matrix4f) {
        val tmp by lazy { Vector3f() }
        // put direction in view-space, as every computation is done in that space
        // do it on the CPU once to avoid doing it for each pixel on the GPU
        viewMatrix.transformPosition(position, tmp)
        tmp.get(buffer)
        buffer.skip(sizeof<Vector3f>() + sizeof<Float>())

        color.get(buffer)
        buffer.skip(sizeof<Vector3f>())

        buffer.putFloat(intensity)

        buffer.putFloat(attenuationConstant)
        buffer.putFloat(attenuationLinear)
        buffer.putFloat(attenuationQuadratic)

        buffer.putInt(shadowMapIndex)
    }

    override fun updateCameraForShadowMapping(camera: Camera, shadowMapIndex: Int) {
        assert(shadowMapIndex >= 0 && shadowMapIndex < type.shadowMapCount) { "Point lights can only produce ${type.shadowMapCount} shadow maps" }

        TODO("Not yet implemented")
    }

    object None: PointLight() {
        override val position: Vector3f
            get() = Vector3f()
        override val color: Vector3f
            get() = Vector3f()

        override var shadowMapIndex: Int
            get() = -1
            set(value) {}

        override var intensity: Float
            get() = 0f
            set(value) {}
    }

    companion object {
        val SizeOf =
            sizeof<Vector3f>() + // position
                    sizeof<Float>() + // padding
                    sizeof<Vector3f>() + // color
                    sizeof<Float>() + // intensity
                    3* sizeof<Float>() +// attenuation model
                    sizeof<Int>() // shadow map index
    }
}