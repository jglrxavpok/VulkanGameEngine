package org.jglrxavpok.engine.render

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Camera used to render
 */
class Camera(aspectRatio: Float) {

    companion object {
        val UpAxis = Vector3f(0f, 1f, 0f)
        val AxisY = Vector3f(0f, 1f, 0f)
        val AxisZ = Vector3f(0f, 0f, 1f)
    }

    constructor(swapchainWidth: Int, swapchainHeight: Int): this(swapchainWidth.toFloat() / swapchainHeight)

    internal val view = Matrix4f().identity().lookAt(Vector3f(0f, 15f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0f, 1f, 0f))
    val projection = Matrix4f().identity().perspective((Math.PI/4f).toFloat(), aspectRatio, 0.01f, 10000000f)
    val forward: Vector3fc get()= Vector3f().set(-view.m02(), -view.m12(), -view.m22())

    val position = Vector3f()
    var pitch = 0f
    var roll = 0f
    var yaw = 0f

    /**
     * Prepares the UBO to render from this camera
     */
    fun updateUBO(ubo: UniformBufferObject) {
        val rot by lazy { Quaternionf() }
        rot.identity().rotateY(yaw).rotateX(pitch).rotateZ(roll).conjugate()
        view.identity().rotate(rot).translate(-position.x(), -position.y(), -position.z())

        ubo.view.set(view)
        ubo.proj.set(projection)

        ubo.proj.m11(ubo.proj.m11() * -1) // invert Y Axis (OpenGL -> Vulkan translation)
    }

}