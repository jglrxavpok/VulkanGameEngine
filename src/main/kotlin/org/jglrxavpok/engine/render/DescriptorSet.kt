package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

/**
 * Represents a logical descriptor set
 *
 * 'sets' is the sets used by Vulkan, one per frame in flight
 * 'dynamicCount' is the number of bindings which require a dynamic offset
 */
class DescriptorSet(val sets: Array<VkDescriptorSet>, val dynamicCount: Int) {

    operator fun get(index: Int): VkDescriptorSet {
        return sets[index]
    }
}

/**
 * Interface for objects that are part of a descriptor set
 */
interface Descriptor {
    /**
     * Descriptor set to represent this object
     */
   // val descriptorSet: DescriptorSet
}

/**
 * Allows to update a descriptor
 */
class DescriptorSetUpdateBuilder {
    // TODO: selectable binding index

    private var currentIndex = 0
    internal val bindings = mutableListOf<Binding>()

    /**
     * Binding used for in the set
     */
    interface Binding {
        /**
         * Writes to target to describe the binding to Vulkan.
         * frameIndex is the index of the frame in flight; this method will be called for each frame in flight
         */
        fun describe(memoryStack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int)
    }

    /**
     * Texture binding
     */
    class TextureBinding(val texture: Texture, val bindingIndex: Int): Binding {
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)

            imageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            imageInfo.imageView(texture.imageView)
            imageInfo.sampler(0)

            target.dstBinding(bindingIndex) // binding for our texture
            target.dstArrayElement(texture.textureID) // we are writing to an array

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
            target.pImageInfo(imageInfo)
            target.descriptorCount(1)
        }
    }

    /**
     * Uniform Buffer Object Binding
     */
    data class UniformBufferBinding(val bindingIndex: Int, val size: Long, val buffers: (Int) -> VkBuffer, val dynamic: Boolean): Binding {
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
            bufferInfo.buffer(buffers(frameIndex))
            bufferInfo.offset(0)
            bufferInfo.range(size)

            target.dstBinding(bindingIndex) // binding for our UBO
            target.dstArrayElement(0) // 0 because we are not writing to an array

            target.descriptorType(if(dynamic) VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC else VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            target.pBufferInfo(bufferInfo)
            target.descriptorCount(1)
        }
    }

    data class SamplerBinding(val sampler: VkSampler): Binding {
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
            imageInfo.sampler(sampler)

            target.dstBinding(2) // binding for our sampler
            target.dstArrayElement(0)

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLER)
            target.pImageInfo(imageInfo)
            target.descriptorCount(1)
        }
    }

    /**
     * frame index -> VkImageView
     */
    class SubpassSamplerBinding(val bindingIndex: Int, val samplerView: (Int) -> VkImageView): Binding {
        override fun describe(memoryStack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, memoryStack)
            imageInfo.imageView(samplerView(frameIndex))
            imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            imageInfo.sampler(VK_NULL_HANDLE)

            target.dstBinding(bindingIndex) // binding for our sampler
            target.dstArrayElement(0)

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT)
            target.pImageInfo(imageInfo)
            target.descriptorCount(1)
        }
    }

    class FrameDependentCombinedImageSamplerBinding(val bindingIndex: Int, val views: (Int) -> VkImageView, val sampler: VkSampler, val layout: VkImageLayout): Binding {
        override fun describe(memoryStack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, memoryStack)
            imageInfo.imageView(views(frameIndex))
            imageInfo.imageLayout(layout)
            imageInfo.sampler(sampler)

            target.dstBinding(bindingIndex) // binding for our sampler
            target.dstArrayElement(0)

            target.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            target.pImageInfo(imageInfo)
            target.descriptorCount(1)
        }
    }

    /**
     * Adds a new texture binding
     */
    fun textureSampling(texture: Texture, bindingIndex: Int): DescriptorSetUpdateBuilder {
        bindings += TextureBinding(texture, bindingIndex)
        return this
    }

    /**
     * Adds a new ubo binding
     */
    fun cameraBuffer(buffers: List<VkBuffer>): DescriptorSetUpdateBuilder {
        return uniformBuffer(CameraObject.SizeOf, buffers::get, true)
    }

    fun uniformBuffer(size: Long, buffers: (Int) -> VkBuffer, dynamic: Boolean): DescriptorSetUpdateBuilder {
        bindings += UniformBufferBinding(nextBindingIndex(), size, buffers, dynamic)
        return this
    }

    /**
     * Adds a new ubo binding
     */
    fun sampler(sampler: VkSampler): DescriptorSetUpdateBuilder {
        bindings += SamplerBinding(sampler)
        return this
    }

    fun subpassSampler(samplerViews: (Int) -> VkImageView): DescriptorSetUpdateBuilder {
        bindings += SubpassSamplerBinding(nextBindingIndex(), samplerViews)
        return this
    }

    fun frameDependentCombinedImageSampler(samplerViews: (Int) -> VkImageView, sampler: VkSampler, layout: VkImageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL): DescriptorSetUpdateBuilder {
        bindings += FrameDependentCombinedImageSamplerBinding(nextBindingIndex(), samplerViews, sampler, layout)
        return this
    }

    fun combinedImageSampler(texture: Texture, sampler: VkSampler, layout: VkImageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL): DescriptorSetUpdateBuilder {
        bindings += FrameDependentCombinedImageSamplerBinding(nextBindingIndex(), { _ -> texture.imageView }, sampler, layout)
        return this
    }

    private fun nextBindingIndex() = currentIndex++
}