package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.sizeof
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.FloatBuffer

/**
 * Represents a vertex for the rendering engine
 */
// TODO: Normals for lighting, other components?
data class Vertex(val pos: Vector3fc = Vector3f(),
                  val color: Vector3fc = Vector3f(),
                  val texCoords: Vector2f = Vector2f()) {

    companion object {
        val SizeOf = 8* sizeof<Float>()
        // TODO: Builder methods to specify vertices?

        fun getBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer {
            val bindingDescription = VkVertexInputBindingDescription.callocStack(1, stack)
            bindingDescription.binding(0) // index
            bindingDescription.stride(SizeOf) // size
            bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
            return bindingDescription
        }

        fun getAttributeDescriptions(stack: MemoryStack): VkVertexInputAttributeDescription.Buffer {
            val descriptions = VkVertexInputAttributeDescription.callocStack(3, stack)
            descriptions[0].binding(0)
            descriptions[0].location(0) // pos
            descriptions[0].format(VK_FORMAT_R32G32B32_SFLOAT)
            descriptions[0].offset(0)

            descriptions[1].binding(0)
            descriptions[1].location(1) // color
            descriptions[1].format(VK_FORMAT_R32G32B32_SFLOAT)
            descriptions[1].offset(3*sizeof<Float>())

            descriptions[2].binding(0)
            descriptions[2].location(2) // texCoords
            descriptions[2].format(VK_FORMAT_R32G32_SFLOAT)
            descriptions[2].offset(6*sizeof<Float>())

            return descriptions
        }

        fun FloatBuffer.put(vertex: Vertex) {
            this.put(vertex.pos.x())
            this.put(vertex.pos.y())
            this.put(vertex.pos.z())
            this.put(vertex.color.x())
            this.put(vertex.color.y())
            this.put(vertex.color.z())
            this.put(vertex.texCoords.x())
            this.put(vertex.texCoords.y())
        }
    }
}