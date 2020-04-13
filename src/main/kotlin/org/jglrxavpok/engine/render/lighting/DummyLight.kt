package org.jglrxavpok.engine.render.lighting

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

class DummyLight: Light() {
    override val type = LightType.Dummy

    override val color = Vector3f()
    override val position = Vector3f()
    override val direction = Vector3f()
}