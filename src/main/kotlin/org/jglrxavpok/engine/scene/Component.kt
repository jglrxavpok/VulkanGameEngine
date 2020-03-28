package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.Camera
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
interface RenderingComponent {
    /**
     * Prepares the rendering of this component to the given buffer
     */
    fun record(element: Element, commandBuffer: VkCommandBuffer, commandBufferIndex: Int)

    /**
     * Updates information about the render matrices
     */
    fun preFrameRender(
        element: Element,
        frameIndex: Int,
        camera: Camera
    )
    // TODO
}

/**
 * Component used to handle logic
 */
interface LogicComponent {
    // TODO

    fun tick(element: Element, dt: Float)
}
