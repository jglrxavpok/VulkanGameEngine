package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkDeviceMemory
import org.jglrxavpok.engine.VkImage
import org.jglrxavpok.engine.VkImageView

data class ImageInfo(val image: VkImage, val memory: VkDeviceMemory, val view: VkImageView) {
}