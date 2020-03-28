package org.jglrxavpok.engine.render

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Camera used to render
 */
class Camera(swapchainWidth: Int, swapchainHeight: Int) {

    companion object {
        val UpAxis = Vector3f(0f, 0f, 1f)
        val AxisY = Vector3f(0f, 1f, 0f)
    }

    private val view = Matrix4f().identity().lookAt(Vector3f(0f, 15f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 1f))
    private val renderView = Matrix4f(view)
    val projection = Matrix4f().identity().perspective((Math.PI/4f).toFloat(), swapchainWidth / swapchainHeight.toFloat(), 0.01f, 10000000f)
    val forward: Vector3fc get()= view.transformDirection(AxisY, Vector3f())

    val position = Vector3f()
    var pitch = 0f
    var roll = 0f
    var yaw = 0f

    /**
     * Prepares the UBO to render from this camera
     */
    fun updateUBO(ubo: UniformBufferObject) {
        val rot by lazy { Quaternionf() }
        rot.identity().rotateY(roll).rotateX(-pitch).rotateZ(-yaw).conjugate()
        view.identity().rotate(rot).translate(-position.x(), -position.y(), -position.z())

        // no idea why renderView and view matrices are not the same
        rot.identity().rotateZ(yaw).rotateX((pitch+Math.PI/2f).toFloat()).rotateY(roll).conjugate()
        renderView.identity().rotate(rot).translate(-position.x(), -position.y(), -position.z())

        ubo.view.set(renderView)
        ubo.proj.set(projection)

        ubo.proj.m11(ubo.proj.m11() * -1) // invert Y Axis (OpenGL -> Vulkan translation)
    }

}