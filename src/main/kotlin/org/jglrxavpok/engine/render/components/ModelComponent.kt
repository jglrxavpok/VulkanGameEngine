package org.jglrxavpok.engine.render.components

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.render.model.Model
import org.jglrxavpok.engine.scene.Element
import org.jglrxavpok.engine.scene.RenderingComponent
import org.lwjgl.vulkan.VkCommandBuffer

class ModelComponent(val path: String): RenderingComponent {

    // TODO
    val model: Model by VulkanRenderingEngine.load { Model(path) }

    override fun record(element: Element, commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        model.record(commandBuffer, commandBufferIndex)
    }

    override fun preFrameRender(element: Element, frameIndex: Int, camera: Camera) {
        camera.updateUBO(model.ubo)
        model.ubo.model.identity().translate(element.position).rotate(element.rotation)
        model.ubo.update(VulkanRenderingEngine.logicalDevice, VulkanRenderingEngine.memoryStack, frameIndex)
    }

}
