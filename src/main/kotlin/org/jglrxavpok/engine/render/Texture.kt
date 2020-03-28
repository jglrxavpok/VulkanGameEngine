package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkImage
import org.jglrxavpok.engine.VkImageView
import org.jglrxavpok.engine.VkSampler
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer
import org.lwjgl.vulkan.VK10.*

class Texture(private val image: VkImage, val imageView: VkImageView, val sampler: VkSampler) {
    fun free(logicalDevice: VkDevice) {
        vkDestroyImageView(logicalDevice, imageView, VulkanRenderingEngine.Allocator)
        vkDestroyImage(logicalDevice, image, VulkanRenderingEngine.Allocator)
    }

}