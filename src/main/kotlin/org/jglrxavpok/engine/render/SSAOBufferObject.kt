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
import kotlin.math.log

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class SSAOBufferObject(val kernelSize: Int): ShaderResource(), Descriptor {

    // TODO: Split into samples and projection matrix buffers

    companion object {
        fun SizeOf(kernelSize: Int) = (sizeof<Matrix4f>() + sizeof<Vector2f>() * kernelSize).toLong()
    }

    val sizeOf = SizeOf(kernelSize)
    val proj = Matrix4f()
    val noiseSamples = Array(kernelSize) { Vector2f() }

    override fun write(buffer: ByteBuffer): ByteBuffer {
        proj.get(buffer)
        buffer.skip(16*4)
        for(sample in noiseSamples) {
            sample.get(buffer)
            buffer.skip(2*4)
        }
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        proj.set(from)
        from.skip(16*4)
        for(sample in noiseSamples) {
            sample.set(from)
            from.skip(2*4)
        }
        return this
    }

    /**
     * Writes this UBO to the correct memory, depending on the frame in flight index
     */
    fun update(logicalDevice: VkDevice, stack: MemoryStack, frameIndex: Int) {
        val bufferSize = sizeOf
        val ppData = stack.mallocPointer(1)
        val data = write(stack.malloc(bufferSize.toInt()))
        val memory = VulkanRenderingEngine.getSSAOMemory(frameIndex)
        VK10.vkMapMemory(
            logicalDevice,
            memory,
            0,
            bufferSize,
            0,
            ppData
        )
        data.position(0)
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), !ppData, bufferSize)

        val memoryRange = VkMappedMemoryRange.callocStack(stack);
        memoryRange.sType(VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE)
        memoryRange.memory(memory)
        memoryRange.offset(0)
        memoryRange.size(sizeOf)
        vkFlushMappedMemoryRanges(logicalDevice, memoryRange)

        VK10.vkUnmapMemory(
            logicalDevice,
            VulkanRenderingEngine.getSSAOMemory(frameIndex)
        )
    }

}