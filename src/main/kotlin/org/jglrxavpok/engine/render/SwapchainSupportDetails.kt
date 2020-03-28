package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkPresentModeKHR
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR

/**
 * Represents what a swapchain can support
 */
class SwapchainSupportDetails {
    lateinit var capabilities: VkSurfaceCapabilitiesKHR
    val formats = mutableListOf<VkSurfaceFormatKHR>()
    val presentModes = mutableListOf<VkPresentModeKHR>()
}