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
// TODO: other components?
data class Vertex(val pos: Vector3fc = Vector3f(),
                  val color: Vector3fc = Vector3f(),
                  val texCoords: Vector2f = Vector2f(),
                  val normal: Vector3fc = Vector3f())