package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.*
import org.joml.Matrix4f
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
class UniformBufferObject(val uboID: Int): ShaderResource(), Descriptor {

    companion object {
        val SizeOf = sizeof<Matrix4f>() * 3L + 64/*padding*/
        val Padding = ByteArray(64) { 0 }
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

        buffer.put(Padding)
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

    override fun getMemory(frameIndex: Int): VkDeviceMemory {
        return VulkanRenderingEngine.getUBOMemory(frameIndex)
    }

    override fun sizeOf() = SizeOf

    /**
     * Writes this UBO to the correct memory, depending on the frame in flight index
     */
    override fun update(logicalDevice: VkDevice, stack: MemoryStack, frameIndex: Int) {
        val bufferSize = SizeOf
        val ppData = stack.mallocPointer(1)
        val data = write(stack.malloc(bufferSize.toInt()))
        val memory = getMemory(frameIndex)
        VK10.vkMapMemory(
            logicalDevice,
            memory,
            uboID* SizeOf,
            bufferSize,
            0,
            ppData
        )
        data.position(0)
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), !ppData, bufferSize)

        val memoryRange = VkMappedMemoryRange.callocStack(stack);
        memoryRange.sType(VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE)
        memoryRange.memory(memory)
        memoryRange.offset(uboID* SizeOf)
        memoryRange.size(SizeOf)
        vkFlushMappedMemoryRanges(logicalDevice, memoryRange)

        VK10.vkUnmapMemory(
            logicalDevice,
            memory
        )
    }

}