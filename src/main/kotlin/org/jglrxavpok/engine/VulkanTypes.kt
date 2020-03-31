package org.jglrxavpok.engine

import org.joml.Vector2i
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VkExtent2D

typealias VkDebugUtilsMessengerEXT = Long
typealias VkSurfaceKHR = Long
typealias VkSwapchainKHR = Long
typealias VkImage = Long
typealias VkImageView = Long
typealias VkShaderModule = Long
typealias VkPipelineLayout = Long
typealias VkRenderPass = Long
typealias VkPipeline = Long
typealias VkFramebuffer = Long
typealias VkCommandPool = Long
typealias VkBuffer = Long
typealias VkSemaphore = Long
typealias VkFence = Long
typealias VkDeviceMemory = Long
typealias VkDeviceSize = Long
typealias VkDescriptorSetLayout = Long
typealias VkDescriptorPool = Long
typealias VkDescriptorSet = Long
typealias VkSampler = Long

typealias VkImageAspectFlags = Int
typealias VkFormatFeatureFlags = Int
typealias VkImageTiling = Int
typealias VkPipelineStageFlags = Int
typealias VkFormat = Int
typealias VkImageLayout = Int
typealias VkImageUsageFlags = Int
typealias VkBufferUsageFlags = Int
typealias VkMemoryPropertyFlags = Int
typealias VkPresentModeKHR = Int

fun Vector2i.toExtent(memoryStack: MemoryStack): VkExtent2D {
    return VkExtent2D.callocStack(memoryStack).set(x(), y())
}