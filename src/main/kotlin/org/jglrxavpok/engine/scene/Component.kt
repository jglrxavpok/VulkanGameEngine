package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.RenderBatches

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
    val castsShadows: Boolean

    /**
     * Prepares the rendering of this component to the given buffer
     */
    fun record(element: Element, batches: RenderBatches)

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
