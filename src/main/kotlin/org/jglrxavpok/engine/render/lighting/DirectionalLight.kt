package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.math.Frustum
import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.Camera.Companion.AxisY
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.math.sizeof
import org.jglrxavpok.engine.math.skip
import org.joml.*
import java.lang.Math
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
        buffer.skip(sizeof<Vector3f>() + sizeof<Float>())

        color.get(buffer)
        buffer.skip(sizeof<Vector3f>())

        buffer.putFloat(intensity)
        buffer.putInt(shadowMapIndex)

        // padding
        buffer.putInt(-1)
        buffer.putInt(-1)
        buffer.putInt(-1)
    }

    override fun updateCameraForShadowMapping(camera: Camera, shadowMapIndex: Int) {
        assert(shadowMapIndex >= 0 && shadowMapIndex < type.shadowMapCount) { "Directional lights can only produce ${type.shadowMapCount} shadow maps" }
        // prepare for Cascaded Shadow Maps
        val frustum: Frustum by lazy { Frustum(0f, 1f, 0f, 0f) }
        // default values from Camera
        frustum.fovx = (Math.PI/4f).toFloat()
        frustum.aspectRatio = VulkanRenderingEngine.defaultCamera.aspectRatio

        // TODO: change depending on index
        frustum.near = 0.01f
        frustum.far = 1000f

        val invertedView by lazy { Matrix4f() }
        VulkanRenderingEngine.defaultCamera.view.invert(invertedView)
        val frustumCorners = frustum.get3DPoints().map { Vector4f(it, 1f) }
        frustumCorners.forEach { invertedView.transform(it) } // transform to world space
        val rotation by lazy { Quaternionf() }
        rotation.identity().lookAlong(direction, AxisY)
        frustumCorners.forEach { rotation.transform(it) } // transform in light space

        val minX = frustumCorners.map { it.x() }.min()!!
        val minY = frustumCorners.map { it.y() }.min()!!
        val minZ = frustumCorners.map { it.z() }.min()!!
        val maxX = frustumCorners.map { it.x() }.max()!!
        val maxY = frustumCorners.map { it.y() }.max()!!
        val maxZ = frustumCorners.map { it.z() }.max()!!

        camera.projection.setOrtho(minX, maxX, minY, maxY, minZ, maxZ, true)
        camera.position.set(0f)

        camera.useEulerAngles = false
        camera.rotation.set(rotation)
    }

    object None: DirectionalLight() {
        override val direction: Vector3f
            get() = Vector3f()
        override val color: Vector3f
            get() = Vector3f()

        override var shadowMapIndex: Int
            get() = -1
            set(value) {}
    }

    companion object {
        val SizeOf =
            sizeof<Vector3f>() + // direction
                    sizeof<Float>() + // padding
                    sizeof<Vector3f>() + // color
                    sizeof<Float>() +// intensity
                    sizeof<Int>() +// shadow map index
                    3* sizeof<Int>()// padding



    }

}