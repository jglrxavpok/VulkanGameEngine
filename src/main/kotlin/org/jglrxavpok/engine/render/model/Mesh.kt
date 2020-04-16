package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.render.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.jglrxavpok.engine.sizeof
import org.joml.Matrix4f
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkDevice

/**
 * Simple mesh. Contains vertices, indices and may possess a Material
 */
class Mesh(val vertices: Collection<Vertex>, val indices: Collection<UInt>, autoload: Boolean = true, val vertexFormat: VertexFormat = VertexFormat.Companion.Default, val material: Material = Material.None) {

    private var vertexBuffer: VkBuffer = -1
    private var indexBuffer: VkBuffer = -1

    init {
        if(autoload)
            load()
    }

    /**
     * Load the mesh: allocates buffers, fill them
     */
    fun load() {
        val vertexBufferSize = (vertices.size * vertexFormat.size).toLong()
        val vertexBuffer = MemoryUtil.memAlloc(vertexBufferSize.toInt())
        val vertexFb = vertexBuffer.asFloatBuffer()
        for (vertex in vertices) {
            vertexFormat.write(vertex, vertexFb)
        }
        this.vertexBuffer = VulkanRenderingEngine.uploadBuffer(
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            vertexBuffer,
            vertexBufferSize
        )

        val indexBufferSize = (sizeof<UInt>() * indices.size).toLong()
        val indexBuffer = MemoryUtil.memAlloc(indexBufferSize.toInt())
        val indexFb = indexBuffer.asIntBuffer()
        for (index in indices) {
            indexFb.put(index.toInt())
        }
        this.indexBuffer = VulkanRenderingEngine.uploadBuffer(
            VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
            indexBuffer,
            indexBufferSize
        )

        MemoryUtil.memFree(indexBuffer)
        MemoryUtil.memFree(vertexBuffer)
    }

    /**
     * Render this mesh to the given command buffer.
     * Takes care of using the correct texture by binding the correct descriptor set
     * @param ubo allow to modify the UBO when rendering this mesh
     */
    fun record(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, ubo: UniformBufferObject) {
        MemoryStack.stackPush().use {
            VulkanRenderingEngine.useDescriptorSets(commandBuffer, commandBufferIndex, ubo.uboID, VulkanRenderingEngine.gBufferShaderDescriptor)
            material.prepareDescriptors(commandBuffer)

            directRecord(it, commandBuffer)
        }
    }

    fun dispatch(batches: RenderBatches, ubo: UniformBufferObject) {
        // TODO: shadow casting in batch ID
        batches.getBatch(material.batch).add(this, ubo)
    }

    /**
     * Only performs the recording, without applying the material
     */
    fun directRecord(stack: MemoryStack, commandBuffer: VkCommandBuffer) {
        val pVertexBuffers = stack.mallocLong(1)
        pVertexBuffers.put(vertexBuffer)
        pVertexBuffers.flip()
        val pOffsets = stack.mallocLong(1)
        pOffsets.put(0L)
        pOffsets.flip()
        vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffers, pOffsets)

        vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0, VK_INDEX_TYPE_UINT32)

        vkCmdDrawIndexed(commandBuffer, indices.size, 1, 0, 0, 0);
    }

    /**
     * Releases the vertex and index buffers
     */
    fun free(logicalDevice: VkDevice) {
        vkDestroyBuffer(logicalDevice, vertexBuffer, VulkanRenderingEngine.Allocator)
        vkDestroyBuffer(logicalDevice, indexBuffer, VulkanRenderingEngine.Allocator)
    }
}