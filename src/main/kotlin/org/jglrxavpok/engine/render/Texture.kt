package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkImage
import org.jglrxavpok.engine.VkImageView
import org.jglrxavpok.engine.VkSampler
import org.lwjgl.vulkan.VK10.vkDestroyImage
import org.lwjgl.vulkan.VK10.vkDestroyImageView
import org.lwjgl.vulkan.VkDevice

/**
 * Wrapper around an image+image view pair and the sampler to sample the image.
 * sampler can be reused for other textures
 */
class Texture(val textureID: Int, private val image: VkImage, val imageView: VkImageView, val source: String? = null) {

    /**
     * Releases the image view and the image objects
     */
    fun free(logicalDevice: VkDevice) {
        vkDestroyImageView(logicalDevice, imageView, VulkanRenderingEngine.Allocator)
        vkDestroyImage(logicalDevice, image, VulkanRenderingEngine.Allocator)
    }

}