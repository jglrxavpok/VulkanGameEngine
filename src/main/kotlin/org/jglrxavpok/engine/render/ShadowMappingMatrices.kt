package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkBuffer
import org.jglrxavpok.engine.VkDeviceMemory
import org.jglrxavpok.engine.sizeof
import org.jglrxavpok.engine.skip
import org.joml.Matrix4f
import java.nio.ByteBuffer

/**
 * Holds screen-space to world space matrices for shadow mapping lights
 */
class ShadowMappingMatrices(val memories: List<VkDeviceMemory>, val buffers: List<VkBuffer>): ShaderResource() {

    companion object {
        fun SizeOf(lightCount: Int): Long =
            (sizeof<Matrix4f>()*2*lightCount).toLong()
    }

    private val tmpCamera = Camera(16f/9f)

    override fun sizeOf(): Long {
        return SizeOf(VulkanRenderingEngine.MaxShadowCastingLights)
    }

    override fun write(buffer: ByteBuffer): ByteBuffer {
        val worldToProjected by lazy { Matrix4f() }
        val lights = VulkanRenderingEngine.shadowCastingLights
        tmpCamera.projection.identity()
        tmpCamera.view.identity()
        tmpCamera.position.set(0f)
        tmpCamera.yaw = 0f
        tmpCamera.pitch = 0f
        tmpCamera.roll = 0f
        for(light in lights) {
            light.updateCameraForShadowMapping(tmpCamera)
            tmpCamera.updateMatrices()

            //tmpCamera.projection.mul(tmpCamera.view, worldToProjected) // compute world space to NDC
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