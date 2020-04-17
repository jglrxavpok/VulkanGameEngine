package org.jglrxavpok.engine.render.components

import org.jglrxavpok.engine.render.Camera
import org.jglrxavpok.engine.render.RenderBatches
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
class ModelComponent(val path: String, castsShadows: Boolean = true): RenderingComponent {

    override val castsShadows: Boolean = castsShadows
    val model: Model by VulkanRenderingEngine.load({ Model.Empty }) { VulkanRenderingEngine.createModel(path) }
    val localTransform = Matrix4f().identity()

    override fun record(element: Element, batches: RenderBatches) {
        model.record(batches)
    }

    override fun preFrameRender(element: Element, frameIndex: Int) {
        model.ubo.model.identity().translate(element.position).rotate(element.rotation).mul(localTransform)
    }

}
