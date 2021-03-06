package org.jglrxavpok.engine.render

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc

/**
 * Camera used to render
 */
class Camera(val aspectRatio: Float) {

    companion object {
        val UpAxis = Vector3f(0f, 1f, 0f)
        val AxisY = Vector3f(0f, 1f, 0f)
        val AxisZ = Vector3f(0f, 0f, 1f)

        val FrustumCorners: Array<Vector3fc> = arrayOf(
            Vector3f(-1f, +1f, -1f),
            Vector3f(+1f, +1f, -1f),
            Vector3f(+1f, -1f, -1f),
            Vector3f(-1f, -1f, -1f),

            Vector3f(-1f, +1f, 1f),
            Vector3f(+1f, +1f, 1f),
            Vector3f(+1f, -1f, 1f),
            Vector3f(-1f, -1f, 1f)
        )

        val DefaultNear = 0.01f
        val DefaultFar = 50f
    }

    constructor(swapchainWidth: Int, swapchainHeight: Int): this(swapchainWidth.toFloat() / swapchainHeight)

    val view = Matrix4f().identity().lookAt(Vector3f(0f, 15f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0f, 1f, 0f))
    val projection = Matrix4f().identity().perspective((Math.PI/4f).toFloat(), aspectRatio, DefaultNear, DefaultFar, true)
    val forward: Vector3fc get()= Vector3f().set(-view.m02(), -view.m12(), -view.m22())

    val position = Vector3f()
    var pitch = 0f
    var roll = 0f
    var yaw = 0f
    val rotation = Quaternionf()
    var useEulerAngles = true

    fun computeRotation(): Quaternionf {
        if(useEulerAngles) {
            rotation.identity().rotateY(yaw).rotateX(pitch).rotateZ(roll).conjugate()
        }
        return rotation
    }

    fun updateMatrices() {
        val rotation = computeRotation()
        view.identity().rotate(rotation).translate(-position.x(), -position.y(), -position.z())
    }

    /**
     * Prepares the UBO to render from this camera
     */
    fun updateCameraObject(cameraObject: CameraObject) {
        updateMatrices()

        cameraObject.view.set(view)
        cameraObject.proj.set(projection)

        cameraObject.proj.m11(cameraObject.proj.m11() * -1) // invert Y Axis (OpenGL -> Vulkan translation)
    }

}