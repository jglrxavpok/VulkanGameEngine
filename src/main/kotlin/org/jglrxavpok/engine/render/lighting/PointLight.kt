package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

class PointLight: Light() {
    override val type = LightType.Point

    public override val position = Vector3f().set(0f)
    public override val color = Vector3f().set(1f)
    override val direction = Vector3f()

}