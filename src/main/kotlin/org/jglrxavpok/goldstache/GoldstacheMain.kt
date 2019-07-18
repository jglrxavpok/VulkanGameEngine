package org.jglrxavpok.goldstache

import org.jglrxavpok.engine.GameInformation
import org.jglrxavpok.engine.Version
import org.jglrxavpok.engine.openNursery
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import javax.swing.JButton
import javax.swing.JFrame
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    if("--waitRenderdoc" in args) {
        val frame = JFrame("Waiting for Renderdoc...")
        frame.add(JButton("Confirm that Renderdoc has been attached").apply {
            addActionListener {
                frame.dispose()
                start()
            }
        })
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    } else {
        start()
    }
}

val GoldstacheInfo = GameInformation("Goldstache RPG", Version(1, 0, 0))

private fun start() {
    openNursery {
        start {
            VulkanRenderingEngine.init(GoldstacheInfo, enableValidationLayers = true)
            VulkanRenderingEngine.loop()
            VulkanRenderingEngine.cleanup()
        }
    }
}