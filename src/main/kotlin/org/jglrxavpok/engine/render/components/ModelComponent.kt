package org.jglrxavpok.engine.render.components

import org.jglrxavpok.engine.render.Model
import org.jglrxavpok.engine.render.UniformBufferObject
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.scene.Element
import org.jglrxavpok.engine.scene.RenderingComponent
import org.lwjgl.vulkan.VkCommandBuffer

class ModelComponent(val path: String): RenderingComponent {

    // TODO
    val model: Model by VulkanRenderingEngine.load { Model(path) }

    override fun record(element: Element, commandBuffer: VkCommandBuffer) {
        model.record(commandBuffer)
    }

    override fun updateUniformBufferObject(element: Element, ubo: UniformBufferObject) {
        ubo.model.identity().translate(element.position).rotate(element.rotation)
    }

}
