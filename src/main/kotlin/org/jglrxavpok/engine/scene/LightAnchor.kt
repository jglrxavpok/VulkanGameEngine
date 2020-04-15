package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.lighting.PositionableLight

/**
 * Component that set the position of the given light to the element's position
 */
class LightAnchor(val light: PositionableLight): LogicComponent {
    override fun tick(element: Element, dt: Float) {
        light.position.set(element.position)
    }
}