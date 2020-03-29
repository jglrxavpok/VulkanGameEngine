package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkPipeline
import org.jglrxavpok.engine.VkPipelineLayout

/**
 * Allows to define a graphics pipeline
 */
class GraphicsPipelineBuilder() {

    var vertexShaderModule: String = "/shaders/default.vertc"
    var vertexShaderEntryPoint: String = "main"
    var fragmentShaderModule: String = "/shaders/default.fragc"
    var fragmentShaderEntryPoint: String = "main"

    fun vertexShaderModule(path: String): GraphicsPipelineBuilder {
        this.vertexShaderModule = path
        return this
    }

    fun fragmentShaderModule(path: String): GraphicsPipelineBuilder {
        this.fragmentShaderModule = path
        return this
    }

    fun vertexShaderEntryPoint(path: String): GraphicsPipelineBuilder {
        this.vertexShaderEntryPoint = path
        return this
    }

    fun fragmentShaderEntryPoint(path: String): GraphicsPipelineBuilder {
        this.fragmentShaderEntryPoint = path
        return this
    }

    fun build(): GraphicsPipeline {
        return VulkanRenderingEngine.createGraphicsPipeline(this)
    }
}

class GraphicsPipeline(val handle: VkPipeline, val layout: VkPipelineLayout) {



}