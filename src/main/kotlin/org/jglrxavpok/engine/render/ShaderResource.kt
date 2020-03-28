package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDescriptorSetLayout
import org.jglrxavpok.engine.not
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkDevice
import java.nio.ByteBuffer

/**
 * Represents a resource that can be written to and read from a byte buffer
 */
abstract class ShaderResource() {

    /**
     * Serialize to a buffer. Must return the given buffer to allow for chaining
     */
    abstract fun write(buffer: ByteBuffer): ByteBuffer

    /**
     * Deserialize from a buffer. Must return this for chaining
     */
    abstract fun read(from: ByteBuffer): ShaderResource

    companion object {

    }
}
