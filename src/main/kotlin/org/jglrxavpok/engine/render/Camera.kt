package org.jglrxavpok.engine.render

import org.joml.Matrix4f
import org.joml.Vector3f

class Camera(swapchainWidth: Int, swapchainHeight: Int) {

    companion object {
        val UpAxis = Vector3f(0f, 0f, 1f)
    }

    private val view = Matrix4f().identity().lookAt(Vector3f(0f, 15f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 1f))
    val projection = Matrix4f().identity().perspective((Math.PI/4f).toFloat(), swapchainWidth / swapchainHeight.toFloat(), 0.01f, 10000000f)

    val position = Vector3f()
    var pitch = -(Math.PI/2f).toFloat()
    var roll = 0f
    var yaw = 0f

    /**
     * Prepares the UBO to render from this camera
     */
    fun updateUBO(ubo: UniformBufferObject) {
        view.identity().rotate(pitch, 1f, 0f, 0f).rotate(roll, 0f, 1f, 0f).rotate(yaw, 0f, 0f, 1f).translate(-position.x(), -position.y(), -position.z())

        ubo.view.set(view)
        ubo.proj.set(projection)

        ubo.proj.m11(ubo.proj.m11() * -1) // invert Y Axis (OpenGL -> Vulkan translation)
    }

}