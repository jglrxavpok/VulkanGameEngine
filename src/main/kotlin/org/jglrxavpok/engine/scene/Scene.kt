package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.UniformBufferObject
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.lwjgl.vulkan.VkCommandBuffer
import java.util.*

class Scene {

    /**
     * Used to determine if the graphics engine needs to rebuild command buffers, textures, etc.
     */
    private var isRenderingDirty: Boolean = false

    private val elements = LinkedList<Element>()

    fun addElement(element: Element) {
        synchronized(elements) {
            elements.add(element)
            isRenderingDirty = true
        }
    }

    fun recordCommandBuffer(commandBuffer: VkCommandBuffer) {
        synchronized(elements) {
            elements.forEach {
                it.recordCommandBuffer(commandBuffer)
            }
        }
    }

    fun tickAll(dt: Float) {
        synchronized(elements) {
            if(isRenderingDirty) {
                refreshRendering()
                isRenderingDirty = false
            }

            elements.forEach {
                it.tick(dt)
            }
        }
    }

    // TODO: TMP, one per object later
    fun updateUniformBuffer(ubo: UniformBufferObject) {
        // TODO
        synchronized(elements) {
            elements.forEach {
                it.updateUniformBufferObject(ubo)
            }
        }
    }

    fun refreshRendering() {
        VulkanRenderingEngine.requestCommandBufferRecreation()
    }

}