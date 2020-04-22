package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.render.Camera
import org.joml.Matrix4f
import java.nio.ByteBuffer

abstract class Light {
    abstract val type: LightType
    var intensity: Float = 1f

    abstract fun write(buffer: ByteBuffer, viewMatrix: Matrix4f)

    abstract fun updateCameraForShadowMapping(camera: Camera)
}