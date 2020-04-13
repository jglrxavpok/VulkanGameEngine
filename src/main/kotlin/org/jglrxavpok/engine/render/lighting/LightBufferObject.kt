package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.*
import org.jglrxavpok.engine.render.Descriptor
import org.jglrxavpok.engine.render.ShaderResource
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class LightBufferObject(val lightCount: Int): ShaderResource(), Descriptor {

    companion object {
        fun SizeOf(lightCount: Int) = (sizeof<Vector3f>() /*view position*/ + sizeof<Float>()/*padding*/ + (sizeof<Int>()/*type*/ + sizeof<Float>()*3/*padding*/ + sizeof<Vector3f>() /*position*/ + sizeof<Float>()/*padding*/ + sizeof<Vector3f>() /*direction*/ + sizeof<Float>()/*padding*/ + sizeof<Vector3f>() /*color*/ + sizeof<Float>()/*padding*/ + sizeof<Float>() /*intensity*/ + sizeof<Float>()*3/*padding*/) * lightCount).toLong()
    }

    val sizeOf = SizeOf(lightCount)
    val lights = Array<Light>(lightCount) { DummyLight() }
    val viewMatrix = Matrix4f().identity()

    override fun write(buffer: ByteBuffer): ByteBuffer {
        val tmp by lazy { Vector3f() }
        for(light in lights) {
            buffer.putInt(light.type.ordinal)
            buffer.putFloat(-1f) // padding
            buffer.putFloat(-1f) // padding
            buffer.putFloat(-1f) // padding
        }
        for(light in lights) {
            viewMatrix.transformPosition(light.position, tmp)
            tmp.get(buffer)
            buffer.skip(3*4)
            buffer.putFloat(-1f) // padding
        }
        for(light in lights) {
            viewMatrix.transformDirection(light.direction, tmp)
            tmp.get(buffer)
            buffer.skip(3*4)
            buffer.putFloat(-1f) // padding
        }
        for(light in lights) {
            light.color.get(buffer)
            buffer.skip(3*4)
            buffer.putFloat(-1f) // padding
        }
        for(light in lights) {
            buffer.putFloat(light.intensity)
        }
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        return TODO()
    }

    override fun getMemory(frameIndex: Int): VkDeviceMemory {
        return VulkanRenderingEngine.getLightMemory(frameIndex)
    }

    override fun sizeOf() = sizeOf
}