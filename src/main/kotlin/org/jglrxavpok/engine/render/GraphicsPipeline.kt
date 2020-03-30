package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkDescriptorSetLayout
import org.jglrxavpok.engine.VkPipeline
import org.jglrxavpok.engine.VkPipelineLayout

/**
 * Allows to define a graphics pipeline
 */
class GraphicsPipelineBuilder() {

    var vertexFormat: VertexFormat = VertexFormat.Companion.Default
    var depthTest = true
    var depthWrite = true
    var useStencil = false
    var subpass = 0
    var descriptorSetLayoutBindings = DescriptorSetLayoutBindings()
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

    fun descriptorSetLayoutBindings(bindings: DescriptorSetLayoutBindings): GraphicsPipelineBuilder {
        this.descriptorSetLayoutBindings = bindings
        return this
    }

    fun depthTest(value: Boolean): GraphicsPipelineBuilder {
        this.depthTest = value
        return this
    }

    fun depthWrite(value: Boolean): GraphicsPipelineBuilder {
        this.depthWrite = value
        return this
    }

    fun useStencil(value: Boolean): GraphicsPipelineBuilder {
        this.useStencil = value
        return this
    }

    fun subpass(value: Int): GraphicsPipelineBuilder {
        this.subpass = value
        return this
    }

    fun vertexFormat(description: VertexFormat): GraphicsPipelineBuilder {
        this.vertexFormat = description
        return this
    }

    fun build(): GraphicsPipeline {
        return VulkanRenderingEngine.createGraphicsPipeline(this)
    }
}

class GraphicsPipeline(val handle: VkPipeline, val layout: VkPipelineLayout, val descriptorSetLayouts: List<VkDescriptorSetLayout>) {



}