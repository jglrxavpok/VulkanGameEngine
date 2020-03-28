package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.Camera
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

    fun recordCommandBuffer(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        synchronized(elements) {
            elements.forEach {
                it.recordCommandBuffer(commandBuffer, commandBufferIndex)
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

    fun preRenderFrame(frameIndex: Int, camera: Camera) {
        synchronized(elements) {
            elements.forEach {
                it.preFrameRender(frameIndex, camera)
            }
        }
    }

    fun refreshRendering() {
        VulkanRenderingEngine.requestCommandBufferRecreation()
    }

}