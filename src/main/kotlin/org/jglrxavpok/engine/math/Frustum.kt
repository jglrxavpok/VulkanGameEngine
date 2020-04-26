package org.jglrxavpok.engine.math

import org.joml.Vector3f
import kotlin.math.tan

/**
 * Upper-Left \--------/ Upper-Right
 *             \      /
 *              \    /
 *    Lower-Left \__/ Lower-Right
 * (2D projection). Angle is fov for horizontal plane, fov*aspectRatio for vertical plane
 */
class Frustum(var fovx: Float, var aspectRatio: Float, var near: Float, var far: Float) {


    /**
     * X coordinate of lower-right point
     */
    private fun xn(fov: Float): Float {
        return tan(fov/2) * near
    }

    /**
     * X coordinate of upper-right point
     */
    private fun xf(fov: Float): Float {
        return tan(fov/2) * far
    }

    /**
     * Returns the 8 points corresponding to the 8 corners of this frustum, up and down, clockwise, starting from upper-left.
     * Returns new vectors each call
     */
    fun get3DPoints(): Array<Vector3f> {
        val fovy = fovx*aspectRatio
        return arrayOf(
            Vector3f(-xf(fovx), -xf(fovy), far),
            Vector3f(xf(fovx), -xf(fovy), far),
            Vector3f(xn(fovx), -xn(fovy), near),
            Vector3f(-xn(fovx), -xn(fovy), near),

            Vector3f(-xf(fovx), xf(fovy), far),
            Vector3f(xf(fovx), xf(fovy), far),
            Vector3f(xn(fovx), xn(fovy), near),
            Vector3f(-xn(fovx), xn(fovy), near)
        )
    }

}