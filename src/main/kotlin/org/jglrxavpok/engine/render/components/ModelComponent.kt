package org.jglrxavpok.engine.render.components

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.RenderGroup
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.jglrxavpok.engine.render.model.Model
import org.jglrxavpok.engine.scene.Element
import org.jglrxavpok.engine.scene.RenderingComponent
import org.joml.Matrix4f
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Entity rendering component.
 * Used to render a given model at the location of the entity
 */
class ModelComponent(val path: String, group: RenderGroup = VulkanRenderingEngine.defaultRenderGroup, val camera: Camera = VulkanRenderingEngine.defaultCamera): RenderingComponent {

    override val renderGroup: RenderGroup = group
    val model: Model by VulkanRenderingEngine.load({ Model.Empty }) { VulkanRenderingEngine.createModel(path) }
    val localTransform = Matrix4f().identity()

    override fun record(element: Element, commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        model.record(commandBuffer, commandBufferIndex)
    }

    override fun preFrameRender(element: Element, frameIndex: Int) {
        camera.updateUBO(model.ubo)
        model.ubo.model.identity().translate(element.position).rotate(element.rotation).mul(localTransform)
        model.ubo.update(VulkanRenderingEngine.logicalDevice, VulkanRenderingEngine.memoryStack, frameIndex)
    }

}
