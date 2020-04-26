package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.math.sizeof
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * Describes how vertices are fed to the GPU
 */
abstract class VertexFormat {

    companion object {
        object Default: VertexFormat() {
            override val vertexSize = 11* sizeof<Float>()
            override val instanceSize = UniformBufferObject.SizeOf.toInt()

            override fun callocBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer {
                val bindingDescription = VkVertexInputBindingDescription.callocStack(2, stack)
                bindingDescription[0].binding(0) // index
                bindingDescription[0].stride(vertexSize) // size
                bindingDescription[0].inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)

                bindingDescription[1].binding(1) // index
                bindingDescription[1].stride(instanceSize) // size
                bindingDescription[1].inputRate(VK10.VK_VERTEX_INPUT_RATE_INSTANCE)
                return bindingDescription
            }

            override fun callocAttributeDescriptions(stack: MemoryStack): VkVertexInputAttributeDescription.Buffer {
                val descriptions = VkVertexInputAttributeDescription.callocStack(8, stack)
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

                // UBO
                // matrix
                for(i in 0 until 4) {
                    descriptions[i+4].binding(1) // ubo
                    descriptions[i+4].location(i+4) // ubo0
                    descriptions[i+4].format(VK10.VK_FORMAT_R32G32B32A32_SFLOAT)
                    descriptions[i+4].offset(i*4* sizeof<Float>())
                }


                return descriptions
            }

            override fun writeVertex(vertex: Vertex, buffer: FloatBuffer) {
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

            override fun writeInstance(instance: UniformBufferObject, buffer: ByteBuffer) {
                instance.write(buffer)
            }
        }

        /**
         * Use to render the GBuffer to the screen.
         * Only uses pos.x and pos.y
         */
        object ScreenPositionOnly: VertexFormat() {
            override val vertexSize = 2* sizeof<Float>()
            override val instanceSize = 0

            override fun callocBindingDescription(stack: MemoryStack): VkVertexInputBindingDescription.Buffer {
                val bindingDescription = VkVertexInputBindingDescription.callocStack(1, stack)
                bindingDescription.binding(0) // index
                bindingDescription.stride(vertexSize) // size
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

            override fun writeVertex(vertex: Vertex, buffer: FloatBuffer) {
                buffer.put(vertex.pos.x())
                buffer.put(vertex.pos.y())
            }

            override fun writeInstance(instance: UniformBufferObject, buffer: ByteBuffer) { }
        }
    }

    /**
     * Size in bytes of a vertex in memory
     */
    abstract val vertexSize: Int

    /**
     * Size in bytes of an instance in memory
     */
    abstract val instanceSize: Int

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
    abstract fun writeVertex(vertex: Vertex, buffer: FloatBuffer)

    /**
     * Write an instance to the given buffer
     */
    abstract fun writeInstance(instance: UniformBufferObject, buffer: ByteBuffer)
}


