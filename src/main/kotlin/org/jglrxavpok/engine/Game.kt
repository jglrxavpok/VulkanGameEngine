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
}
