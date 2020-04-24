package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.Camera.Companion.AxisY
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sqrt

open class SpotLight: Light(), PositionableLight {
    override val type = LightType.Spot

    override val position = Vector3f().set(0f)
    open val color = Vector3f().set(1f)
    open val direction = Vector3f().set(1f)
    open var angle = Math.PI/2f

    var attenuationConstant = 1f
    var attenuationLinear = 1f
    var attenuationQuadratic = 1f

    override fun write(buffer: ByteBuffer, viewMatrix: Matrix4f) {
        val tmp by lazy { Vector3f() }
        // put direction in view-space, as every computation is done in that space
        // do it on the CPU once to avoid doing it for each pixel on the GPU
        viewMatrix.transformPosition(position, tmp)
        tmp.get(buffer)
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(cos(angle/2f).toFloat()) // coscutoff

        viewMatrix.transformDirection(direction, tmp)
        tmp.get(buffer)
        buffer.skip(sizeof<Vector3f>()+ sizeof<Float>())

        color.get(buffer)
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(intensity)

        buffer.putFloat(attenuationConstant)
        buffer.putFloat(attenuationLinear)
        buffer.putFloat(attenuationQuadratic)

        buffer.putFloat(-1f) // padding
    }

    override fun updateCameraForShadowMapping(camera: Camera) {
        val rot by lazy { Quaternionf() }
        val angles by lazy { Vector3f() }

        // 256 = c + l * d + q*d*d
        // 0 = c-att + l *d + q * d²
        // delta = 4q(c-att)
        // d1 = (l-2*Math.sqrt(q*(c-att))) / (2q)
        // d2 = (l+2*Math.sqrt(q*(c-att))) / (2q)
        val fullAttenuation = 256f
        val maxDistance = (attenuationLinear+2* sqrt(attenuationQuadratic*(-(attenuationConstant-fullAttenuation)))) / (2*attenuationQuadratic) * intensity
        camera.projection.setPerspective((angle).toFloat(), camera.aspectRatio, 0.01f, maxDistance, true)
        camera.position.set(position)

        rot.identity().lookAlong(direction, AxisY).getEulerAnglesXYZ(angles)

        camera.pitch = -angles.x()
        camera.yaw = -angles.y()
        camera.roll = angles.z()
    }

    object None: SpotLight() {
        override val position: Vector3f
            get() = Vector3f()
        override val color: Vector3f
            get() = Vector3f()
        override val direction: Vector3f
            get() = Vector3f()
        override var angle: Double
            get() = 1.0
            set(value) {}
    }

    companion object {
        val SizeOf =
            sizeof<Vector3f>() + // position
            sizeof<Float>() + // cos of angle (coscutoff)
            sizeof<Vector3f>() + // direction
            sizeof<Float>() + // padding
            sizeof<Vector3f>() + // color
            sizeof<Float>() + // intensity
            3*sizeof<Float>() +// attenuation model
            sizeof<Float>() // padding
    }
}