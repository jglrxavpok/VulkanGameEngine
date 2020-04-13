package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.sizeof
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.FloatBuffer

/**
 * Describes how vertices are fed to the GPU
 */
abstract class VertexFormat {

    companion object {
        object Default: VertexFormat() {
            override val size = 11* sizeof<Float>()

            override fun callocBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer {
                val bindingDescription = VkVertexInputBindingDescription.callocStack(1, stack)
                bindingDescription.binding(0) // index
                bindingDescription.stride(size) // size
                bindingDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)
                return bindingDescription
            }

            override fun callocAttributeDescriptions(stack: MemoryStack): VkVertexInputAttributeDescription.Buffer {
                val descriptions = VkVertexInputAttributeDescription.callocStack(4, stack)
                descriptions[0].binding(0)
                descriptions[0].location(0) // pos
                descriptions[0].format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                descriptions[0].offset(0)

                descriptions[1].binding(0)
                descriptions[1].location(1) // color
                descriptions[1].format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                descriptions[1].offset(3* sizeof<Float>())

                descriptions[2].binding(0)
                descriptions[2].location(2) // texCoords
                descriptions[2].format(VK10.VK_FORMAT_R32G32_SFLOAT)
                descriptions[2].offset(6* sizeof<Float>())

                descriptions[3].binding(0)
                descriptions[3].location(3) // normal
                descriptions[3].format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                descriptions[3].offset(8* sizeof<Float>())


                return descriptions
            }

            override fun write(vertex: Vertex, buffer: FloatBuffer) {
                buffer.put(vertex.pos.x())
                buffer.put(vertex.pos.y())
                buffer.put(vertex.pos.z())
                buffer.put(vertex.color.x())
                buffer.put(vertex.color.y())
                buffer.put(vertex.color.z())
                buffer.put(vertex.texCoords.x())
                buffer.put(vertex.texCoords.y())
                buffer.put(vertex.normal.x())
                buffer.put(vertex.normal.y())
                buffer.put(vertex.normal.z())
            }
        }

        /**
         * Use to render the GBuffer to the screen.
         * Only uses pos.x and pos.y
         */
        object ScreenPositionOnly: VertexFormat() {
            override val size = 2* sizeof<Float>()

            override fun callocBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer {
                val bindingDescription = VkVertexInputBindingDescription.callocStack(1, stack)
                bindingDescription.binding(0) // index
                bindingDescription.stride(size) // size
                bindingDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)
                return bindingDescription
            }

            override fun callocAttributeDescriptions(stack: MemoryStack): VkVertexInputAttributeDescription.Buffer {
                val descriptions = VkVertexInputAttributeDescription.callocStack(1, stack)
                descriptions[0].binding(0)
                descriptions[0].location(0) // pos
                descriptions[0].format(VK10.VK_FORMAT_R32G32_SFLOAT)
                descriptions[0].offset(0)

                return descriptions
            }

            override fun write(vertex: Vertex, buffer: FloatBuffer) {
                buffer.put(vertex.pos.x())
                buffer.put(vertex.pos.y())
            }
        }
    }

    /**
     * Size in bytes of a vertex in memory
     */
    abstract val size: Int

    /**
     * Creates the corresponding VkVertexInputBindingDescription
     */
    abstract fun callocBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer

    /**
     * Creates the corresponding VkVertexInputAttributeDescription buffer
     */
    abstract fun callocAttributeDescriptions(stack: MemoryStack): VkVertexInputAttributeDescription.Buffer

    /**
     * Write the vertex to the given buffer
     */
    abstract fun write(vertex: Vertex, buffer: FloatBuffer)
}


