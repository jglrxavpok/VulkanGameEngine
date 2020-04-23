package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.useStack
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.EXTDebugMarker
import org.lwjgl.vulkan.EXTDebugReport.VK_DEBUG_REPORT_OBJECT_TYPE_UNKNOWN_EXT
import org.lwjgl.vulkan.VkDebugMarkerObjectNameInfoEXT

internal object VulkanDebug {

    internal var enabled = false
    private val stack = MemoryStack.create(1024*64)

    fun name(objectHandle: Long, name: String, type: Int = VK_DEBUG_REPORT_OBJECT_TYPE_UNKNOWN_EXT) {
        if(!enabled)
            return
        stack.useStack {
            val marker = VkDebugMarkerObjectNameInfoEXT.callocStack(this)
            marker.sType(EXTDebugMarker.VK_STRUCTURE_TYPE_DEBUG_MARKER_OBJECT_NAME_INFO_EXT)
            marker.pObjectName(UTF8(name))
            marker.`object`(objectHandle)
            marker.objectType(type)

            EXTDebugMarker.vkDebugMarkerSetObjectNameEXT(VulkanRenderingEngine.logicalDevice, marker)
        }
    }
}