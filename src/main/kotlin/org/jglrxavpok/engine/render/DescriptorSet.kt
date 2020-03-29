package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDescriptorSet
import org.jglrxavpok.engine.VkSampler
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

/**
 * Represents a logical descriptor set
 *
 * 'sets' is the sets used by Vulkan, one per frame in flight
 */
class DescriptorSet(val sets: Array<VkDescriptorSet>) {

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
    class TextureBinding(val texture: Texture): Binding {
        // TODO: binding index
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)

            imageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            imageInfo.imageView(texture.imageView)
            imageInfo.sampler(0)

            target.dstBinding(1) // binding for our texture
            target.dstArrayElement(texture.textureID) // 0 because we are not writing to an array

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
            target.pImageInfo(imageInfo)
        }
    }

    /**
     * Uniform Buffer Object Binding
     */
    data class UBOBinding(val uboBuffers: List<VkBuffer>): Binding {
        // TODO: index
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
            bufferInfo.buffer(uboBuffers[frameIndex])
            bufferInfo.offset(0)
            bufferInfo.range(UniformBufferObject.SizeOf)

            target.dstBinding(0) // binding for our UBO
            target.dstArrayElement(0) // 0 because we are not writing to an array

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
            target.pBufferInfo(bufferInfo)
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
        }
    }

    /**
     * Adds a new texture binding
     */
    fun textureSampling(texture: Texture): DescriptorSetUpdateBuilder {
        bindings += TextureBinding(texture)
        return this
    }

    /**
     * Adds a new ubo binding
     */
    fun ubo(buffers: List<VkBuffer>): DescriptorSetUpdateBuilder {
        bindings += UBOBinding(buffers)
        return this
    }

    /**
     * Adds a new ubo binding
     */
    fun sampler(sampler: VkSampler): DescriptorSetUpdateBuilder {
        bindings += SamplerBinding(sampler)
        return this
    }
}