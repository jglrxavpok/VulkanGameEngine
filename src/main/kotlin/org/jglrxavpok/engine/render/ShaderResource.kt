package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDescriptorSetLayout
import org.jglrxavpok.engine.VkDeviceMemory
import org.jglrxavpok.engine.not
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.ByteBuffer

/**
 * Represents a resource that can be written to and read from a byte buffer
 */
abstract class ShaderResource() {

    /**
     * Serialize to a buffer. Must return the given buffer to allow for chaining
     */
    abstract fun write(buffer: ByteBuffer): ByteBuffer

    /**
     * Deserialize from a buffer. Must return this for chaining
     */
    abstract fun read(from: ByteBuffer): ShaderResource

    abstract fun getMemory(frameIndex: Int): VkDeviceMemory

    abstract fun sizeOf(): Long

    /**
     * Writes this UBO to the correct memory, depending on the frame in flight index
     */
    open fun update(logicalDevice: VkDevice, stack: MemoryStack, frameIndex: Int) {
        val bufferSize = sizeOf()
        val ppData = stack.mallocPointer(1)
        val data = write(stack.malloc(bufferSize.toInt()))
        val memory = getMemory(frameIndex)
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
        //memoryRange.size(sizeOf())
        memoryRange.size(VK_WHOLE_SIZE)
        vkFlushMappedMemoryRanges(logicalDevice, memoryRange)

        VK10.vkUnmapMemory(
            logicalDevice,
            memory
        )
    }

    companion object {

    }
}
