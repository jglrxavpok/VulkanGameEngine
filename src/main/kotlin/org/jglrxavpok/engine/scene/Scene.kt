package org.jglrxavpok.engine.scene

import org.jglrxavpok.engine.render.RenderBatches
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.render.lighting.Light
import org.jglrxavpok.engine.render.lighting.LightBufferObject
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A scene is a collection of Element, it is responsible of dispatching ticks and rendering the elements
 */
class Scene {

    private var futureActions = mutableListOf<() -> Unit>()

    /**
     * Used to determine if the graphics engine needs to rebuild command buffers, textures, etc.
     */
    private var isRenderingDirty: Boolean = false

    private val elements = LinkedList<Element>()
    private val lights = LinkedList<Light>()
    val ambientLighting = Vector3f(0f)

    private val elementLocks = ReentrantReadWriteLock()

    fun addElement(element: Element) {
        elementLocks.write {
            elements.add(element)
            element.onAdded(this)
            isRenderingDirty = true
        }
    }

    fun record(batches: RenderBatches) {
        elementLocks.read {
            elements.forEach {
                it.record(batches)
            }
        }
    }

    fun tickAll(dt: Float) {
        synchronized(futureActions) {
            futureActions.forEach {
                it()
            }
            futureActions.clear()
        }
        if(isRenderingDirty) {
            refreshRendering()
            isRenderingDirty = false
        }
        elementLocks.read {
            elements.forEach {
                it.tick(dt)
            }
        }
    }

    fun preRenderFrame(frameIndex: Int, lightBufferObject: LightBufferObject) {
        lightBufferObject.viewMatrix.set(VulkanRenderingEngine.defaultCamera.view)
        lightBufferObject.ambientLighting.set(ambientLighting)
        elementLocks.read {
            elements.forEach {
                it.preFrameRender(frameIndex)
            }
        }
    }

    fun refreshRendering() {
        VulkanRenderingEngine.requestCommandBufferRecreation()
    }

    fun nextTick(function: () -> Unit) {
        synchronized(futureActions) {
            futureActions.add(function)
        }
    }

    fun addLight(light: Light) {
        synchronized(lights) {
            lights += light
            VulkanRenderingEngine.setLights(lights)
        }
        // TODO: shadow casting lights
    }

}