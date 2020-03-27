package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.UniformBufferObject
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.vulkan.VkCommandBuffer

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
    fun recordCommandBuffer(commandBuffer: VkCommandBuffer)= synchronized(renderingComponents) {
        renderingComponents.forEach {
            it.record(this, commandBuffer)
        }
    }

    // TODO: one per component?
    fun updateUniformBufferObject(ubo: UniformBufferObject) = synchronized(renderingComponents) {
        renderingComponents.forEach {
            it.updateUniformBufferObject(this, ubo)
        }
    }

}
