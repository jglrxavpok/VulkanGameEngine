package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.render.Camera
import org.joml.Matrix4f
import java.nio.ByteBuffer

abstract class Light {
    abstract val type: LightType
    open var intensity: Float = 1f

    /**
     * Index into the shadow mapping lights array. -1 denotes that this light does not produce shadow maps
     */
    open var shadowMapIndex: Int = -1

    abstract fun write(buffer: ByteBuffer, viewMatrix: Matrix4f)

    /**
     *
     * @param shadowMapIndex the index inside this light shadow maps (starts at 0 and ends at type.shadowMapCount-1)
     */
    abstract fun updateCameraForShadowMapping(camera: Camera, shadowMapIndex: Int)
}