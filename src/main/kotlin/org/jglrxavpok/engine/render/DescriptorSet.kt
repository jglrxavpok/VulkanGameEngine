package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkDescriptorSet
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
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

interface Descriptor {
    val descriptorSet: DescriptorSet
}

class DescriptorSetBuilder {

    internal val bindings = mutableListOf<Binding>()

    interface Binding {
        fun describe(memoryStack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int)
    }

    data class TextureBinding(val texture: Texture): Binding {
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
            imageInfo.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            imageInfo.imageView(texture.imageView)
            imageInfo.sampler(texture.sampler)

            target.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            target.dstSet(targetSet)
            target.dstBinding(0) // binding for our texture
            target.dstArrayElement(0) // 0 because we are not writing to an array

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            target.pImageInfo(imageInfo)
        }
    }

    data class UBOBinding(val ubo: UniformBufferObject): Binding {
        // TODO: index
        override fun describe(stack: MemoryStack, target: VkWriteDescriptorSet, targetSet: VkDescriptorSet, frameIndex: Int) {
            val bufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
            bufferInfo.buffer(ubo.buffers[frameIndex])
            bufferInfo.offset(0)
            bufferInfo.range(UniformBufferObject.SizeOf)

            target.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            target.dstSet(targetSet)
            target.dstBinding(0) // binding for our UBO
            target.dstArrayElement(0) // 0 because we are not writing to an array

            target.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            target.pBufferInfo(bufferInfo)
        }
    }

    fun textureSampling(texture: Texture): DescriptorSetBuilder {
        bindings += TextureBinding(texture)
        return this
    }

    fun ubo(uniformBufferObject: UniformBufferObject): DescriptorSetBuilder {
        bindings += UBOBinding(uniformBufferObject)
        return this
    }
}