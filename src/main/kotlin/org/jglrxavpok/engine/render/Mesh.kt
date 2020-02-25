package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.jglrxavpok.engine.render.Vertex.Companion.put
import org.jglrxavpok.engine.sizeof
import org.lwjgl.system.MemoryUtil

class Mesh(val vertices: Collection<Vertex>, val indices: Collection<UInt>, autoload: Boolean = true) {

    private var vertexBuffer: VkBuffer = -1
    private var indexBuffer: VkBuffer = -1

    init {
        if(autoload)
            load()
    }

    fun load() {
        val vertexBufferSize = (vertices.size * Vertex.SizeOf).toLong()
        val vertexBuffer = MemoryUtil.memAlloc(vertexBufferSize.toInt())
        val vertexFb = vertexBuffer.asFloatBuffer()
        for (vertex in vertices) {
            vertexFb.put(vertex)
        }
        this.vertexBuffer = VulkanRenderingEngine.uploadBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexBuffer, vertexBufferSize)

        val indexBufferSize = (sizeof<UInt>() * indices.size).toLong()
        val indexBuffer = MemoryUtil.memAlloc(indexBufferSize.toInt())
        val indexFb = indexBuffer.asIntBuffer()
        for (index in indices) {
            indexFb.put(index.toInt())
        }
        this.indexBuffer = VulkanRenderingEngine.uploadBuffer(VK_BUFFER_USAGE_INDEX_BUFFER_BIT, indexBuffer, indexBufferSize)

        MemoryUtil.memFree(indexBuffer)
        MemoryUtil.memFree(vertexBuffer)
    }

    fun record(commandBuffer: VkCommandBuffer) {
        MemoryStack.stackPush().use {
            val pVertexBuffers = it.mallocLong(1)
            pVertexBuffers.put(vertexBuffer)
            pVertexBuffers.flip()
            val pOffsets = it.mallocLong(1)
            pOffsets.put(0L)
            pOffsets.flip()
            vkCmdBindVertexBuffers(commandBuffer, 0, pVertexBuffers, pOffsets)

            vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0, VK_INDEX_TYPE_UINT32)

            vkCmdDrawIndexed(commandBuffer, indices.size, 1, 0, 0, 0);
        }
    }
}