package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkDeviceMemory
import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import java.nio.ByteBuffer

class CameraObject(val memories: List<VkDeviceMemory>): ShaderResource() {

    companion object {
        val SizeOf = sizeof<Matrix4f>() *2L
    }

    val view = Matrix4f()
    val proj = Matrix4f()

    override fun sizeOf() = SizeOf

    override fun write(buffer: ByteBuffer): ByteBuffer {
        view.get(buffer)
        buffer.skip(sizeof<Matrix4f>())
        proj.get(buffer)
        buffer.skip(sizeof<Matrix4f>())
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        view.set(from)
        from.skip(sizeof<Matrix4f>())
        proj.set(from)
        from.skip(sizeof<Matrix4f>())
        return this
    }

    override fun getMemory(frameIndex: Int): VkDeviceMemory {
        return memories[frameIndex]
    }
}