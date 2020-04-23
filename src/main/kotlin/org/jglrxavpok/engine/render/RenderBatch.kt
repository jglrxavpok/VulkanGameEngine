package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.render.model.Mesh
import org.jglrxavpok.engine.render.model.TextureUsage
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkCommandBuffer
import java.util.*

/**
 * Represents a group of renderable elements that can be batched together
 */
class RenderBatch {

    private var entries = mutableMapOf<Mesh, MutableList<UniformBufferObject>>()

    /**
     * Pipeline used for rendering
     */
    var usualPipeline = VulkanRenderingEngine.gBufferPipeline

    private var startingFrame = true

    fun record(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, shadowMappingPipeline: GraphicsPipeline? = null) {
        if(shadowMappingPipeline != null) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, shadowMappingPipeline.handle)
        } else {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, usualPipeline.handle)

            // TODO: this is pipeline dependent!
            VulkanRenderingEngine.bindTexture(commandBuffer, TextureUsage.Diffuse, VulkanRenderingEngine.WhiteTexture, usualPipeline.layout)
        }

        for((mesh, uboList) in entries) {
            mesh.instancedRecord(commandBuffer, commandBufferIndex, startingFrame, uboList, shadowMappingPipeline == null)
        }
        startingFrame = false
    }

    /**
     * During a single frame, meshes will not update their instance buffers, this method triggers a refresh
     */
    fun newFrame() {
        startingFrame = true
    }

    /**
     * Adds a mesh that will be put in the batch
     */
    fun add(mesh: Mesh, ubo: UniformBufferObject) {
        entries.getOrPut(mesh, ::LinkedList) += ubo
    }

    fun reset() {
        entries.clear()
    }

    fun updateInstances(frameIndex: Int) {
        entries.keys.forEach {
            it.updateInstances(frameIndex)
        }
    }

}

class RenderBatches {

    private val batches = mutableMapOf<String, RenderBatch>()

    fun getBatch(batchID: String): RenderBatch {
        return batches.getOrPut(batchID, ::RenderBatch)
    }

    fun recordAll(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, shadowMappingPipeline: GraphicsPipeline? = null) {
        for(batch in batches.values) {
            batch.record(commandBuffer, commandBufferIndex, shadowMappingPipeline)
        }
    }

    fun reset() {
        batches.values.forEach(RenderBatch::reset)
    }

    fun updateInstances(frameIndex: Int) {
        batches.values.forEach {
            it.updateInstances(frameIndex)
        }
    }

    fun newFrame() {
        batches.values.forEach(RenderBatch::newFrame)
    }
}