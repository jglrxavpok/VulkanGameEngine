package org.jglrxavpok.engine

abstract class Game {
    /**
     * Called each time the engine wants to update the game (basically every frame)
     */
    abstract fun tick(dt: Float)

    /**
     * Sets up the game
     */
    abstract fun init()

    open fun onMouseButton(button: Int, action: Int) {
        // nop
    }

    open fun onKeyEvent(key: Int, scancode: Int, action: Int, mods: Int) {
        // nop
    }

    open fun onMouseMoveEvent(xpos: Double, ypos: Double, dx: Double, dy: Double) {
        // nop
    }
}
