package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.*
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE
import org.lwjgl.vulkan.VK10.vkFlushMappedMemoryRanges
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkMappedMemoryRange
import java.nio.ByteBuffer

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class SSAOBufferObject(val sampleCount: Int): ShaderResource(), Descriptor {

    // TODO: Split into samples and projection matrix buffers

    companion object {
        fun SizeOf(sampleCount: Int) = (sizeof<Matrix4f>() + sizeof<Vector3f>() * sampleCount).toLong()
    }

    val sizeOf = SizeOf(sampleCount)
    val proj = Matrix4f()
    val noiseSamples = Array(sampleCount) { Vector3f() }

    override fun write(buffer: ByteBuffer): ByteBuffer {
        proj.get(buffer)
        buffer.skip(16*4)
        for(sample in noiseSamples) {
            sample.get(buffer)
            buffer.skip(3*4)
        }
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        proj.set(from)
        from.skip(16*4)
        for(sample in noiseSamples) {
            sample.set(from)
            from.skip(3*4)
        }
        return this
    }

    override fun getMemory(frameIndex: Int): VkDeviceMemory {
        return VulkanRenderingEngine.getSSAOMemory(frameIndex)
    }

    override fun sizeOf() = sizeOf
}