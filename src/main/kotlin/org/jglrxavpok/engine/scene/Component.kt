package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.RenderGroup
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Abstract component
 */
interface Component {
    // TODO: requirements?
}

/**
 * Component that will render to framebuffer
 */
interface RenderingComponent: Component {
    val renderGroup: RenderGroup
    val castsShadows: Boolean

    /**
     * Prepares the rendering of this component to the given buffer
     */
    fun record(element: Element, commandBuffer: VkCommandBuffer, commandBufferIndex: Int)

    /**
     * Updates information about the render matrices
     */
    fun preFrameRender(element: Element, frameIndex: Int)
}

/**
 * Component used to handle logic
 */
interface LogicComponent: Component {
    /**
     * React to a game tick
     */
    fun tick(element: Element, dt: Float)
}
