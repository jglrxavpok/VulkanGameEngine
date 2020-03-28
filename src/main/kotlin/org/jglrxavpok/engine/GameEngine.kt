package org.jglrxavpok.engine

import org.jglrxavpok.engine.physics.PhysicsEngine
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.goldstache.GoldstacheInfo
import org.lwjgl.glfw.GLFW.glfwGetTime
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object GameEngine {

    private lateinit var game: Game
    val time get()= glfwGetTime()

    /**
     * Starts up the game engine
     */
    fun launch(info: GameInformation, game: Game) {
        this.game = game

        openNursery {
            var stop = false
            start {
                VulkanRenderingEngine.init(info, game, true)
                VulkanRenderingEngine.changeThread()
                VulkanRenderingEngine.loop()
                VulkanRenderingEngine.cleanup()
                stop = true
            }

            start {
                PhysicsEngine.init()
                while(!stop) {
                    PhysicsEngine.tick()
                    TimeUnit.MILLISECONDS.sleep(10)
                }
            }
            start {
                PhysicsEngine.waitForInit()
                VulkanRenderingEngine.waitForInit()
                game.init()

                var lastStepTime = GameEngine.time
                while(!stop) {
                    val dt = GameEngine.time - lastStepTime
                    game.tick(dt.toFloat())
                    lastStepTime = GameEngine.time
                    TimeUnit.MILLISECONDS.sleep(10)
                }
            }
        }
    }

}
