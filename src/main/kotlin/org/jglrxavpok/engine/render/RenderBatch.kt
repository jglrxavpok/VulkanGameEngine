package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.render.model.Mesh
import org.jglrxavpok.engine.render.model.TextureUsage
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer

/**
 * Represents a group of renderable elements that can be batched together
 */
class RenderBatch {

    private var entries = mutableListOf<Pair<Mesh, UniformBufferObject>>()

    /**
     * Pipeline used for rendering
     */
    var pipeline = VulkanRenderingEngine.gBufferPipeline

    fun record(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle)

        // TODO: this is pipeline dependent!
        VulkanRenderingEngine.bindTexture(commandBuffer, TextureUsage.Diffuse, VulkanRenderingEngine.WhiteTexture, pipeline.layout)

        // TODO: Use instanced rendering
        for((mesh, ubo) in entries) {
            // TODO: use ubo
            mesh.record(commandBuffer, commandBufferIndex, ubo)
        }
        entries.clear()
    }

    /**
     * Adds a mesh that will be put in the batch
     */
    fun add(mesh: Mesh, ubo: UniformBufferObject) {
        entries.add(mesh to ubo)
    }

}

class RenderBatches {

    private val batches = mutableMapOf<String, RenderBatch>()

    fun getBatch(batchID: String): RenderBatch {
        return batches.getOrPut(batchID, ::RenderBatch)
    }

    fun recordAll(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        for(batch in batches.values) {
            batch.record(commandBuffer, commandBufferIndex)
        }
    }
}