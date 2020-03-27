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

    fun launch(info: GameInformation, game: Game) {
        this.game = game

        // TODO: fixed fps
        openNursery {
            start {
                VulkanRenderingEngine.init(info, true)
                VulkanRenderingEngine.changeThread()
                VulkanRenderingEngine.loop()
                VulkanRenderingEngine.cleanup()
            }

            start {
                PhysicsEngine.init()
                while(true) {
                    PhysicsEngine.tick()
                    TimeUnit.MILLISECONDS.sleep(10)
                }
            }
            start {
                PhysicsEngine.waitForInit()
                VulkanRenderingEngine.waitForInit()
                game.init()

                var lastStepTime = GameEngine.time
                while(true) {
                    val dt = GameEngine.time - lastStepTime
                    game.tick(dt.toFloat())
                    lastStepTime = GameEngine.time
                    TimeUnit.MILLISECONDS.sleep(10)
                }
            }
        }
    }

}
