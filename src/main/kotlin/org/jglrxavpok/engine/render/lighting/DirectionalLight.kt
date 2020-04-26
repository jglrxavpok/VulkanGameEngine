package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.Camera.Companion.AxisY
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.math.sizeof
import org.jglrxavpok.engine.math.skip
import org.joml.*
import java.lang.Math
import java.lang.Math.pow
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.pow

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
        buffer.skip(sizeof<Vector3f>())
        buffer.putInt(shadowMapIndex)

        color.get(buffer)
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(intensity)

        val splits = Vector4f(calculateSplit(0), calculateSplit(1), calculateSplit(2), calculateSplit(3))
        splits.mul(Camera.DefaultFar - Camera.DefaultNear).add(Camera.DefaultNear, Camera.DefaultNear, Camera.DefaultNear, Camera.DefaultNear).mul(-1f)
        println(splits)
        buffer.putFloat(splits[0])
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(splits[1])
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(splits[2])
        buffer.skip(sizeof<Vector3f>())
        buffer.putFloat(splits[3])
        buffer.skip(sizeof<Vector3f>())
    }

    override fun updateCameraForShadowMapping(camera: Camera, shadowMapIndex: Int) {
        assert(shadowMapIndex >= 0 && shadowMapIndex < type.shadowMapCount) { "Directional lights can only produce ${type.shadowMapCount} shadow maps" }
        // prepare for Cascaded Shadow Maps
        // based on https://github.com/SaschaWillems/Vulkan/blob/master/examples/shadowmappingcascade/shadowmappingcascade.cpp

        val invProjView by lazy { Matrix4f() }
        VulkanRenderingEngine.defaultCamera.projection.mul(VulkanRenderingEngine.defaultCamera.view, invProjView)
        invProjView.invert()
        val frustumCorners4 = Camera.FrustumCorners.map { Vector4f(it, 1f) }
        val frustumCorners = frustumCorners4.map {
            invProjView.transform(it)
            it.x = it.x / it.w
            it.y = it.y / it.w
            it.z = it.z / it.w
            Vector3f(it.x, it.y, it.z)
        } // transform to world space

        val splitDist = calculateSplit(shadowMapIndex)
        val lastSplitDist = if(shadowMapIndex == 0) 0.0f else calculateSplit(shadowMapIndex-1)
        // separate according to split distance
        for(i in 0 until 4) {
            val distanceBetweenNearAndFar = frustumCorners[i+4].sub(frustumCorners[i], Vector3f())
            frustumCorners[i].fma(splitDist, distanceBetweenNearAndFar, frustumCorners[i+4])
            frustumCorners[i].fma(lastSplitDist, distanceBetweenNearAndFar, frustumCorners[i])
        }

        val frustumCenter = frustumCorners.fold(Vector3f()) { acc, vec -> acc.add(vec) }
            .div(8f)

        var radius = frustumCorners.fold(0f) { acc, vec ->
            val distance = vec.distance(frustumCenter)
            maxOf(distance, acc)
        }
        radius = (ceil((radius*16f).toDouble()) / 16f).toFloat()

        camera.projection.setOrtho(-radius, radius, -radius, radius, -radius, radius, true)
        camera.position.set(frustumCenter.x, frustumCenter.y, frustumCenter.z)

        val rotation by lazy { Quaternionf() }
        rotation.identity().lookAlong(direction, AxisY)
        camera.useEulerAngles = false
        camera.rotation.set(rotation)
    }

    private fun calculateSplit(shadowMapIndex: Int): Float {
        val minZ = Camera.DefaultNear
        val maxZ = Camera.DefaultNear+Camera.DefaultFar
        val clipRange = Camera.DefaultFar-Camera.DefaultNear
        val range = maxZ-minZ
        val ratio = maxZ/minZ

        val power = (shadowMapIndex+1) / type.shadowMapCount.toFloat()
        val log = minZ * ratio.pow(power)
        val uniform = minZ + range * power

        val lambda = 0.95f // TODO: customizable?
        val d = lambda * (log - uniform) + uniform
        return (d-Camera.DefaultNear) / clipRange
    }

    object None: DirectionalLight() {
        override val direction: Vector3f
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
            sizeof<Vector3f>() + // direction
                    sizeof<Int>() +// shadow map index
                    sizeof<Vector3f>() + // color
                    sizeof<Float>() +// intensity

                    4*(sizeof<Float>()+ 3*sizeof<Int>())// cascade splits+paddings

    }

}