package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.*
import org.jglrxavpok.engine.render.*
import org.jglrxavpok.engine.render.VulkanRenderingEngine.useStack
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkDevice

/**
 * Simple mesh. Contains vertices, indices and may possess a Material
 */
class Mesh(val vertices: Collection<Vertex>, val indices: Collection<UInt>, autoload: Boolean = true, val vertexFormat: VertexFormat = VertexFormat.Companion.Default, val material: Material = Material.None) {

    val canBeInstanced = vertexFormat.instanceSize != 0

    /**
     * Tracks how many instance we had during the last recording. This allows to reuse the instance buffer and grow it only when necessary
     */
    private var previousInstanceCount = 0

    private var vertexBuffer: VkBuffer = -1
    private var indexBuffer: VkBuffer = -1
    private var instanceBuffer: VkBuffer = -1
    private var instanceBufferMemory: VkDeviceMemory = -1
    private val instances = mutableListOf<UniformBufferObject>()

    init {
        if(autoload)
            load()
    }

    /**
     * Load the mesh: allocates buffers, fill them
     */
    fun load() {
        val vertexBufferSize = (vertices.size * vertexFormat.vertexSize).toLong()
        val vertexBuffer = MemoryUtil.memAlloc(vertexBufferSize.toInt())
        val vertexFb = vertexBuffer.asFloatBuffer()
        for (vertex in vertices) {
            vertexFormat.writeVertex(vertex, vertexFb)
        }
        this.vertexBuffer = VulkanRenderingEngine.uploadBuffer(
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            vertexBuffer,
            vertexBufferSize
        ).first

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
        ).first

        MemoryUtil.memFree(indexBuffer)
        MemoryUtil.memFree(vertexBuffer)
    }

    /**
     * Render this mesh to the given command buffer.
     * Takes care of using the correct texture by binding the correct descriptor set
     * @param ubo allow to modify the UBO when rendering this mesh
     */
    fun instancedRecord(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, startingFrame: Boolean, ubos: List<UniformBufferObject>, useMaterial: Boolean) {
        if(startingFrame) {
            this.instances.clear()
            this.instances += ubos
        }
        useStack {
            if(useMaterial) {
                material.prepareDescriptors(commandBuffer, commandBufferIndex)
            }

            directRecord(this, commandBuffer, startingFrame, ubos.size)
        }
    }

    fun updateInstances(frameIndex: Int) {
        if(instances.size == 0)
            return
        val instanceBufferSize = (vertexFormat.instanceSize * instances.size).toLong()
        useStack {
            val instanceBuffer = malloc(instanceBufferSize.toInt())

            for(instance in instances) {
                vertexFormat.writeInstance(instance, instanceBuffer)
            }
            instanceBuffer.flip()

            VulkanRenderingEngine.fillBuffer(this@Mesh.instanceBuffer, instanceBuffer, instanceBufferSize)
        }
    }

    private fun prepareInstanceBuffer(instanceCount: Int) {
        if(instanceCount == 0)
            return
        if(previousInstanceCount >= instanceCount)
            return
        if(this.instanceBuffer != -1L) {
            vkFreeMemory(VulkanRenderingEngine.logicalDevice, instanceBufferMemory, VulkanRenderingEngine.Allocator)
            vkDestroyBuffer(VulkanRenderingEngine.logicalDevice, instanceBuffer, VulkanRenderingEngine.Allocator)
        }

        val instanceBufferSize = (vertexFormat.instanceSize * instanceCount).toLong()
        val instanceBuffer = MemoryUtil.memCalloc(instanceBufferSize.toInt())

        val instanceBufferInfo = VulkanRenderingEngine.uploadBuffer(
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            instanceBuffer,
            instanceBufferSize
        )
        this.instanceBuffer = instanceBufferInfo.first
        this.instanceBufferMemory = instanceBufferInfo.second

        MemoryUtil.memFree(instanceBuffer)
        previousInstanceCount = instanceCount
    }

    fun dispatch(batches: RenderBatches, ubo: UniformBufferObject) {
        // TODO: shadow casting in batch ID
        batches.getBatch(material.batch).add(this, ubo)
    }

    /**
     * Only performs the recording, without applying the material
     */
    fun directRecord(stack: MemoryStack, commandBuffer: VkCommandBuffer, startingFrame: Boolean, instanceCount: Int) {
        if(canBeInstanced && startingFrame) {
            prepareInstanceBuffer(instanceCount)
        }
        val pVertexBuffers = stack.mallocLong(if(canBeInstanced) 2 else 1)
        pVertexBuffers.put(vertexBuffer)
        if(canBeInstanced)
            pVertexBuffers.put(instanceBuffer)
        pVertexBuffers.flip()
        val pOffsets = stack.mallocLong(2)
        pOffsets.put(0L)
        pOffsets.put(0L)
        pOffsets.flip()
        vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffers, pOffsets)

        vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0, VK_INDEX_TYPE_UINT32)

        if(canBeInstanced) {
            vkCmdDrawIndexed(commandBuffer, indices.size, instanceCount, 0, 0, 0);
        } else {
            vkCmdDrawIndexed(commandBuffer, indices.size, 1, 0, 0, 0);
        }
    }

    /**
     * Releases the vertex and index buffers
     */
    fun free(logicalDevice: VkDevice) {
        vkDestroyBuffer(logicalDevice, instanceBuffer, VulkanRenderingEngine.Allocator)
        vkDestroyBuffer(logicalDevice, vertexBuffer, VulkanRenderingEngine.Allocator)
        vkDestroyBuffer(logicalDevice, indexBuffer, VulkanRenderingEngine.Allocator)
    }
}