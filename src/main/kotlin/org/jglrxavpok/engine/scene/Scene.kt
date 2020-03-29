package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.RenderGroup
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.lwjgl.vulkan.VkCommandBuffer
import java.util.*

/**
 * A scene is a collection of Element, it is responsible of dispatching ticks and rendering the elements
 */
class Scene {

    private var futureActions = mutableListOf<() -> Unit>()

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

    fun recordCommandBuffer(group: RenderGroup, commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        synchronized(elements) {
            elements.forEach {
                it.recordCommandBuffer(group, commandBuffer, commandBufferIndex)
            }
        }
    }

    fun tickAll(dt: Float) {
        synchronized(elements) {
            synchronized(futureActions) {
                futureActions.forEach {
                    it()
                }
                futureActions.clear()
            }
            if(isRenderingDirty) {
                refreshRendering()
                isRenderingDirty = false
            }

            elements.forEach {
                it.tick(dt)
            }
        }
    }

    fun preRenderFrame(frameIndex: Int) {
        synchronized(elements) {
            elements.forEach {
                it.preFrameRender(frameIndex)
            }
        }
    }

    fun refreshRendering() {
        VulkanRenderingEngine.requestCommandBufferRecreation()
    }

    fun nextTick(function: () -> Unit) {
        synchronized(futureActions) {
            futureActions.add(function)
        }
    }

}