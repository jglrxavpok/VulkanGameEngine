package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import org.lwjgl.vulkan.VK10
import java.nio.ByteBuffer

class UniformBufferObject: ShaderResource() {

    companion object {
        val SizeOf = sizeof<Matrix4f>() * 3L
    }

    val model = Matrix4f()
    val view = Matrix4f()
    val proj = Matrix4f()

    override fun write(buffer: ByteBuffer): ByteBuffer {
        model.get(buffer)
        buffer.skip(16*4)
        view.get(buffer)
        buffer.skip(16*4)
        proj.get(buffer)
        buffer.skip(16*4)
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        model.set(from)
        from.skip(16*4)
        view.set(from)
        from.skip(16*4)
        proj.set(from)
        from.skip(16*4)
        return this
    }
}