package org.jglrxavpok.engine.render.lighting

import java.nio.ByteBuffer

class DummyLight: Light {
    override val sizeOf = 0L
    override val type = LightType.Dummy

    override fun write(buffer: ByteBuffer) { }
}