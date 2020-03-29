package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.render.*
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Represents a material (eg textures, lighting, etc.)
 */
class Material(val diffuseTexture: TextureDescriptor?): Descriptor {
    companion object {
        val None = Material(null)
    }

    /**
     * Used by the rendering engine to know what texture to use
     */
    /*override val descriptorSet by VulkanRenderingEngine.load(DescriptorSet.Companion::Empty) {
        val builder = DescriptorSetBuilder()
        if(diffuseTexture != null) {
            builder.textureSampling(diffuseTexture.texture)
        } else {
            builder.textureSampling(VulkanRenderingEngine.WhiteTexture)
        }
        VulkanRenderingEngine.createDescriptorSetFromBuilder(VulkanRenderingEngine.descriptorLayoutTexture, builder)
    } = VulkanRenderingEngine.descriptorLayoutTexture*/

    fun prepareDescriptors(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, uboID: Int) {
        // TODO: change depending on shader
        val tex = diffuseTexture?.texture ?: VulkanRenderingEngine.WhiteTexture
        VulkanRenderingEngine.bindTexture(commandBuffer, tex)
        VulkanRenderingEngine.useDescriptorSets(commandBuffer, commandBufferIndex, uboID, VulkanRenderingEngine.defaultShaderDescriptor)
    }
}

/**
 * Used to build a material
 */
class MaterialBuilder {

    private var diffuseTexture: TextureDescriptor? = null

    /**
     * Sets the diffuse texture to use for this material
     */
    fun diffuseTexture(texture: TextureDescriptor): MaterialBuilder {
        diffuseTexture = texture
        return this
    }

    /**
     * Creates the material with the properties given during building
     */
    fun build(): Material {
        return Material(diffuseTexture = diffuseTexture)
    }

}

/**
 * Represents a texture inside a material
 */
data class TextureDescriptor(val path: String, val usage: TextureUsage) {
    val texture by VulkanRenderingEngine.load({ VulkanRenderingEngine.WhiteTexture }) { VulkanRenderingEngine.createTexture(path) }
}

/**
 * Uses for textures
 */
enum class TextureUsage {
    None,
    Diffuse,
    // TODO
}