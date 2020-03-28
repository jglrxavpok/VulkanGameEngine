package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.render.*
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Represents a material (eg textures, lighting, etc.)
 */
class Material(val diffuseTexture: TextureDescriptor?): Descriptor {
    companion object {
        val None = Material(null)
    }

    override val descriptorSet by VulkanRenderingEngine.load {
        val builder = DescriptorSetBuilder()
        if(diffuseTexture != null) {
            builder.textureSampling(diffuseTexture.texture)
        } else {
            builder.textureSampling(VulkanRenderingEngine.WhiteTexture)
        }
        VulkanRenderingEngine.createDescriptorSetFromBuilder(VulkanRenderingEngine.descriptorLayoutTexture, builder)
    }

    fun prepareDescriptors(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, vararg additionalSets: DescriptorSet) {
        VulkanRenderingEngine.useDescriptorSets(commandBuffer, commandBufferIndex, descriptorSet, *additionalSets)
    }
}

class MaterialBuilder {

    private var diffuseTexture: TextureDescriptor? = null

    fun diffuseTexture(texture: TextureDescriptor): MaterialBuilder {
        diffuseTexture = texture
        return this
    }

    fun build(): Material {
        return Material(diffuseTexture = diffuseTexture)
    }

}

data class TextureDescriptor(val path: String, val usage: TextureUsage) {
    val texture by VulkanRenderingEngine.load { VulkanRenderingEngine.createTexture(path) }
}

enum class TextureUsage {
    None,
    Diffuse,
    // TODO
}