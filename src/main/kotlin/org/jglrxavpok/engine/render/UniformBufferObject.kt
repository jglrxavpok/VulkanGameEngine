package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.*
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class UniformBufferObject: ShaderResource(), Descriptor {

    companion object {
        val SizeOf = sizeof<Matrix4f>() * 3L
    }

    val model = Matrix4f()
    val view = Matrix4f()
    val proj = Matrix4f()

    internal val buffers = mutableListOf<VkBuffer>()
    private val memories = mutableListOf<VkDeviceMemory>()

    override val descriptorSet by VulkanRenderingEngine.load {
        val preparation = VulkanRenderingEngine.prepareUniformBuffer()
        buffers.addAll(preparation.first)
        memories.addAll(preparation.second)

        VulkanRenderingEngine.createDescriptorSetFromBuilder(VulkanRenderingEngine.descriptorLayoutUBO, DescriptorSetBuilder().ubo(this))
    }

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

    /**
     * Writes this UBO to the correct memory, depending on the frame in flight index
     */
    fun update(logicalDevice: VkDevice, stack: MemoryStack, frameIndex: Int) {
        if(buffers.size < frameIndex || memories.size < frameIndex) { // don't try to update memory we don't have yet
            return
        }
        val bufferSize = SizeOf
        val ppData = stack.mallocPointer(1)
        val data = write(stack.malloc(bufferSize.toInt()))
        VK10.vkMapMemory(
            logicalDevice,
            memories[frameIndex],
            0,
            bufferSize,
            0,
            ppData
        )
        data.position(0)
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), !ppData, bufferSize)
        VK10.vkUnmapMemory(
            logicalDevice,
            memories[frameIndex]
        )
    }

    /**
     * Releases buffers and memory used by this object
     */
    fun free() {
        buffers.forEach {
            VK10.vkDestroyBuffer(VulkanRenderingEngine.logicalDevice, it, VulkanRenderingEngine.Allocator)
        }
        memories.forEach {
            VK10.vkFreeMemory(VulkanRenderingEngine.logicalDevice, it, VulkanRenderingEngine.Allocator)
        }
    }
}