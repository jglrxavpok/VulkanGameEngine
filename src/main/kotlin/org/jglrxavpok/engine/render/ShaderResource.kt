package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDescriptorSetLayout
import org.jglrxavpok.engine.not
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer

abstract class ShaderResource() {

    /**
     * Serialize to a buffer. Must return the given buffer to allow for chaining
     */
    abstract fun write(buffer: ByteBuffer): ByteBuffer

    /**
     * Deserialize from a buffer. Must return this for chaining
     */
    abstract fun read(from: ByteBuffer): ShaderResource

    companion object {
        fun createDescriptorSetLayout(bindingIndex: Int, stack: MemoryStack, logicalDevice: VkDevice, bindingType: Int, stageFlags: Int): VkDescriptorSetLayout {
            val binding = VkDescriptorSetLayoutBinding.callocStack(1, stack)
            binding.binding(bindingIndex)
            binding.descriptorType(bindingType)
            binding.descriptorCount(1)
            binding.stageFlags(stageFlags)

            val createInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
            createInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            createInfo.pBindings(binding)

            val pBuffer = stack.mallocLong(1)
            if(vkCreateDescriptorSetLayout(logicalDevice, createInfo, null, pBuffer) != VK_SUCCESS) {
                error("Failed to create descriptor set layout")
            }

            return !pBuffer
        }
    }
}
