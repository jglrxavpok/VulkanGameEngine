package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

abstract class Light {
    abstract val type: LightType
    internal abstract val color: Vector3f
    internal abstract val position: Vector3f
    internal abstract val direction: Vector3f
    var intensity: Float = 1f

}