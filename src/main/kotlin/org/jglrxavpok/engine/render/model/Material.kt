package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.render.*
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Represents a material (eg textures, lighting, etc.)
 */
class Material(val diffuseTexture: TextureDescriptor?, val specularTexture: TextureDescriptor?): Descriptor {
    companion object {
        val None = Material(null, null)
    }

    val batch = "material_base/${diffuseTexture?.path ?: "no_texture"}"

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

    fun prepareDescriptors(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        // TODO: descriptor set for material information?
        // TODO: could be useful to swap textures
        VulkanRenderingEngine.useDescriptorSets(commandBuffer, commandBufferIndex, VulkanRenderingEngine.gBufferShaderDescriptor)

        // TODO: change depending on shader
        val tex = diffuseTexture?.texture ?: VulkanRenderingEngine.WhiteTexture
        val specular = specularTexture?.texture ?: VulkanRenderingEngine.WhiteTexture
        VulkanRenderingEngine.bindTexture(commandBuffer, TextureUsage.Diffuse, tex)
        VulkanRenderingEngine.bindTexture(commandBuffer, TextureUsage.Specular, specular)
    }
}

/**
 * Used to build a material
 */
class MaterialBuilder {

    private var diffuseTexture: TextureDescriptor? = null
    private var specularTexture: TextureDescriptor? = null

    /**
     * Sets the diffuse texture to use for this material
     */
    fun diffuseTexture(path: String): MaterialBuilder {
        diffuseTexture = TextureDescriptor(path, TextureUsage.Diffuse)
        return this
    }

    /**
     * Sets the specular texture to use for this material
     */
    fun specularTexture(path: String): MaterialBuilder {
        specularTexture = TextureDescriptor(path, TextureUsage.Specular)
        return this
    }

    /**
     * Creates the material with the properties given during building
     */
    fun build(): Material {
        return Material(diffuseTexture = diffuseTexture, specularTexture = specularTexture)
    }

}

/**
 * Represents a texture inside a material
 */
data class TextureDescriptor(val path: String, val usage: TextureUsage) {
    val texture by VulkanRenderingEngine.load({
        when(usage) {
            TextureUsage.Specular -> VulkanRenderingEngine.BlackSpecularTexture
            else -> VulkanRenderingEngine.WhiteTexture
        }

    }) { VulkanRenderingEngine.createTexture(path, usage) }
}

/**
 * Uses for textures
 */
enum class TextureUsage {
    None,
    Diffuse,
    Specular,
    // TODO
}