package org.jglrxavpok.engine.render.lighting

import org.jglrxavpok.engine.*
import org.jglrxavpok.engine.render.Descriptor
import org.jglrxavpok.engine.render.ShaderResource
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.joml.Matrix4f
import java.nio.ByteBuffer

/**
 * Uniform Buffer Objects are used to pass variables to shaders without changing the rendering pipeline
 */
class LightBufferObject(val lightingConfiguration: LightingConfiguration): ShaderResource(), Descriptor {

    companion object {
        fun SizeOf(lightingConfiguration: LightingConfiguration): Long =
            (
                    +lightingConfiguration.directionalLightCount * DirectionalLight.SizeOf
                    +lightingConfiguration.pointLightCount * PointLight.SizeOf
                    +lightingConfiguration.spotLightCount * SpotLight.SizeOf
            ).toLong()
    }

    val sizeOf = SizeOf(lightingConfiguration)
    val viewMatrix = Matrix4f().identity()
    private val pointLights = Array<PointLight>(lightingConfiguration.pointLightCount) { PointLight.None }
    private val spotLights = Array<SpotLight>(lightingConfiguration.spotLightCount) { SpotLight.None }
    private val directionalLights = Array<DirectionalLight>(lightingConfiguration.directionalLightCount) { DirectionalLight.None }

    override fun write(buffer: ByteBuffer): ByteBuffer {
        for (light in pointLights) {
            light.write(buffer, viewMatrix)
        }
        for (light in spotLights) {
            light.write(buffer, viewMatrix)
        }
        for (light in directionalLights) {
            light.write(buffer, viewMatrix)
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

    fun setLights(lights: List<Light>) {
        var pointCursor = 0
        var spotCursor = 0
        var directionalCursor = 0

        // TODO: range check
        lights.forEach {
            when(it) {
                is SpotLight -> spotLights[spotCursor++] = it
                is DirectionalLight -> directionalLights[directionalCursor++] = it
                is PointLight -> pointLights[pointCursor++] = it
                else -> error("Unsupported light type: $it")
            }
        }
    }
}