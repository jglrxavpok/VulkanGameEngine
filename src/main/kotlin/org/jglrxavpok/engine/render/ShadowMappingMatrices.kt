package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDeviceMemory
import org.jglrxavpok.engine.math.sizeof
import org.jglrxavpok.engine.math.skip
import org.joml.Matrix4f
import java.nio.ByteBuffer

/**
 * Holds screen-space to world space matrices for shadow mapping lights
 */
class ShadowMappingMatrices(val memories: List<VkDeviceMemory>, val buffers: List<VkBuffer>): ShaderResource() {

    companion object {
        fun SizeOf(lightCount: Int): Long =
            (sizeof<Matrix4f>() *2*lightCount).toLong()
    }

    private val tmpCamera = Camera(16f/9f)

    override fun sizeOf(): Long {
        return SizeOf(VulkanRenderingEngine.MaxShadowMaps)
    }

    override fun write(buffer: ByteBuffer): ByteBuffer {
        val worldToProjected by lazy { Matrix4f() }
        val lights = VulkanRenderingEngine.shadowCastingLights
        for((i, light) in lights.withIndex()) {
            light.updateCameraForShadowMapping(tmpCamera, i-light.shadowMapIndex)
            tmpCamera.updateMatrices()

            tmpCamera.projection.m11(tmpCamera.projection.m11() * -1f)
            tmpCamera.projection.get(buffer)
            buffer.skip(sizeof<Matrix4f>())
            tmpCamera.view.get(buffer)
            buffer.skip(sizeof<Matrix4f>())
        }

        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        TODO("Not yet implemented")
    }

    override fun getMemory(frameIndex: Int): VkDeviceMemory {
        return memories[frameIndex]
    }
}