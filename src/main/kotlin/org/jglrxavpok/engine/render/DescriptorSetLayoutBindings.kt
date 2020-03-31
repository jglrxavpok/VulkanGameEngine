package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkPipelineStageFlags
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VK10.*

/**
 * Represents a high-level view of a VkDescriptorSetLayout
 */
class DescriptorSetLayoutBindings {

    data class Binding(val descriptorType: Int, val descriptorCount: Int, val stageFlags: Int)

    private val bindings = mutableListOf<Binding>()

    fun calloc(stack: MemoryStack): VkDescriptorSetLayoutBinding.Buffer {
        val bindingBuffer = VkDescriptorSetLayoutBinding.callocStack(bindings.size, stack)
        for (i in bindings.indices) {
            val binding = bindings[i]
            bindingBuffer.get(i).binding(i)
            bindingBuffer.get(i).descriptorType(binding.descriptorType)
            bindingBuffer.get(i).descriptorCount(binding.descriptorCount)
            bindingBuffer.get(i).stageFlags(binding.stageFlags)
        }

        return bindingBuffer
    }

    fun bind(binding: Binding): DescriptorSetLayoutBindings {
        bindings += binding
        return this
    }

    fun uniformBuffer(dynamic: Boolean): DescriptorSetLayoutBindings {
        bind(Binding(if(dynamic) VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC else VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, VK_SHADER_STAGE_VERTEX_BIT))
        return this
    }

    fun textures(count: Int): DescriptorSetLayoutBindings {
        bind(Binding(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, count, VK_SHADER_STAGE_FRAGMENT_BIT))
        return this
    }

    fun sampler(): DescriptorSetLayoutBindings {
        bind(Binding(VK_DESCRIPTOR_TYPE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT))
        return this
    }

    fun combinedImageSampler(): DescriptorSetLayoutBindings {
        bind(Binding(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT))
        return this
    }

    fun subpassSampler(): DescriptorSetLayoutBindings {
        bind(Binding(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT, 1, VK_SHADER_STAGE_FRAGMENT_BIT))
        return this
    }

}