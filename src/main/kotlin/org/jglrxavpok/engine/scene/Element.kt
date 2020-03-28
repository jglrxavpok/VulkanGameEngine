package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.Camera
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Element of the Scene. Possesses components to update the element and to render it.
 * Holds a position and a rotation which can be accessed in components.
 */
open class Element {

    val position = Vector3f()
    val rotation = Quaternionf()
    private val renderingComponents = mutableListOf<RenderingComponent>()
    private val updateComponents = mutableListOf<LogicComponent>()

    open fun tick(dt: Float) = synchronized(updateComponents) {
        updateComponents.forEach {
            it.tick(this, dt)
        }
    }

    /**
     * Adds a new rendering component to this element
     */
    fun render(component: RenderingComponent): Element = synchronized(renderingComponents) {
        renderingComponents += component
        return this
    }

    /**
     * Adds a new logic component to this element
     */
    fun update(component: LogicComponent): Element = synchronized(updateComponents) {
        updateComponents += component
        return this
    }

    /**
     * Prepares the rendering of this element to the given command buffer
     */
    fun recordCommandBuffer(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) = synchronized(renderingComponents) {
        renderingComponents.forEach {
            it.record(this, commandBuffer, commandBufferIndex)
        }
    }

    // TODO: one per component?
    fun preFrameRender(frameIndex: Int, camera: Camera) = synchronized(renderingComponents) {
        renderingComponents.forEach {
            it.preFrameRender(this, frameIndex, camera)
        }
    }

}
