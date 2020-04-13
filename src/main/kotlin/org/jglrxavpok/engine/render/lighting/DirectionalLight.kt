package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

class DirectionalLight: Light() {
    override val type = LightType.Directional

    public override val direction = Vector3f().set(1f)
    public override val color = Vector3f().set(1f)
    override val position = Vector3f()

}