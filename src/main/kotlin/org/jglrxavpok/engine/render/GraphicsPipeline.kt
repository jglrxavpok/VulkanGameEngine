package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.*
import org.joml.Vector2i
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*

/**
 * Allows to define a graphics pipeline
 */
class GraphicsPipelineBuilder(val attachmentCount: Int, val renderPass: VkRenderPass, val extent: Vector2i) {

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

    constructor(attachmentCount: Int, renderPass: VkRenderPass, vkExtent: VkExtent2D): this(attachmentCount, renderPass, Vector2i(vkExtent.width(), vkExtent.height()))

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

    fun build(memoryStack: MemoryStack): GraphicsPipeline {
        // TODO: Custom shaders
        // TODO: Combine?
        // TODO: Specialization Info
        // TODO: allow own render passes
        val fragCode = javaClass.getResourceAsStream(fragmentShaderModule).readBytes()
        val vertCode = javaClass.getResourceAsStream(vertexShaderModule).readBytes()
        val fragmentShaderModule = createShaderModule(memoryStack, fragCode)
        val vertexShaderModule = createShaderModule(memoryStack, vertCode)
        val swapchainExtent = extent.toExtent(memoryStack)

        val result = memoryStack.useStack {
            val vertShaderStageInfo = VkPipelineShaderStageCreateInfo.callocStack(this)
            vertShaderStageInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            vertShaderStageInfo.module(vertexShaderModule)
            vertShaderStageInfo.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
            vertShaderStageInfo.pName(this.UTF8(vertexShaderEntryPoint))


            val fragShaderStageInfo = VkPipelineShaderStageCreateInfo.callocStack(this)
            fragShaderStageInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            fragShaderStageInfo.module(fragmentShaderModule)
            fragShaderStageInfo.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
            fragShaderStageInfo.pName(this.UTF8(fragmentShaderEntryPoint))

            val shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, this)
            shaderStages.put(vertShaderStageInfo)
            shaderStages.put(fragShaderStageInfo)
            shaderStages.flip()

            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.callocStack(this)
            vertexInputInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            val bindingDescription = vertexFormat.callocBindingDescription(this)
            val attributeDescriptions = vertexFormat.callocAttributeDescriptions(this)
            vertexInputInfo.pVertexAttributeDescriptions(attributeDescriptions)
            vertexInputInfo.pVertexBindingDescriptions(bindingDescription)

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(this)
            inputAssembly.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
            inputAssembly.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            inputAssembly.primitiveRestartEnable(false)

            val viewport = VkViewport.callocStack(1, this)
            viewport.width(swapchainExtent.width().toFloat())
            viewport.height(swapchainExtent.height().toFloat())
            viewport.minDepth(0f)
            viewport.maxDepth(1f)

            val scissor = VkRect2D.callocStack(1, this)
            scissor.offset(VkOffset2D.callocStack(this).set(0, 0))
            scissor.extent(swapchainExtent)

            val viewportState = VkPipelineViewportStateCreateInfo.callocStack(this)
            viewportState.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
            viewportState.pScissors(scissor)
            viewportState.pViewports(viewport)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(this)
            rasterizer.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
            rasterizer.depthClampEnable(false) // TODO: Use 'true' for shadow maps

            rasterizer.rasterizerDiscardEnable(false)

            rasterizer.polygonMode(VK10.VK_POLYGON_MODE_FILL) // TODO: use LINE for wireframe (requires GPU feature)

            rasterizer.lineWidth(1f)

            rasterizer.cullMode(VK10.VK_CULL_MODE_BACK_BIT)
            rasterizer.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)

            rasterizer.depthBiasEnable(false) // TODO: can be used for shadow mapping

            val multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(this)
            multisampling.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
            multisampling.sampleShadingEnable(false)
            multisampling.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)

            // TODO: Depth&Stencil buffers

            val colorBlendAttachments = VkPipelineColorBlendAttachmentState.callocStack(attachmentCount, this)
            for (i in 0 until attachmentCount) {
                val colorBlendAttachment = colorBlendAttachments[i]
                colorBlendAttachment.colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT or VK10.VK_COLOR_COMPONENT_G_BIT or VK10.VK_COLOR_COMPONENT_B_BIT or VK10.VK_COLOR_COMPONENT_A_BIT)

                // configured for alpha blending
                colorBlendAttachment.blendEnable(true)
                colorBlendAttachment.srcColorBlendFactor(VK10.VK_BLEND_FACTOR_SRC_ALPHA)
                colorBlendAttachment.dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                colorBlendAttachment.colorBlendOp(VK10.VK_BLEND_OP_ADD)
                colorBlendAttachment.srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                colorBlendAttachment.dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO)
                colorBlendAttachment.alphaBlendOp(VK10.VK_BLEND_OP_ADD)
            }

            val colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(this)
            colorBlending.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            colorBlending.logicOpEnable(false)
            colorBlending.pAttachments(colorBlendAttachments)

            // TODO: Dynamic state goes here

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(this)
            pipelineLayoutInfo.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)

            // TODO: Customizable
            val pushConstants = VkPushConstantRange.callocStack(1, this)
            pushConstants[0].stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
            pushConstants[0].offset(0)
            pushConstants[0].size(4*2)
            pipelineLayoutInfo.pPushConstantRanges(pushConstants)

            val bindings = descriptorSetLayoutBindings.calloc(this)

            val createInfo = VkDescriptorSetLayoutCreateInfo.callocStack(this)
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            createInfo.pBindings(bindings)

            val pDescriptorSetLayout = this.mallocLong(1)
            if (VK10.vkCreateDescriptorSetLayout(
                    VulkanRenderingEngine.logicalDevice,
                    createInfo,
                    VulkanRenderingEngine.Allocator,
                    pDescriptorSetLayout
                ) != VK10.VK_SUCCESS
            ) {
                error("Failed to create descriptor set layout")
            }

            val descriptorSetLayouts = this.mallocLong(1)
            descriptorSetLayouts.put(0, !pDescriptorSetLayout)
            pipelineLayoutInfo.pSetLayouts(descriptorSetLayouts)

            val pPipelineLayout = this.mallocLong(1)
            VK10.vkCreatePipelineLayout(
                VulkanRenderingEngine.logicalDevice,
                pipelineLayoutInfo,
                VulkanRenderingEngine.Allocator,
                pPipelineLayout
            ).checkVKErrors()

            val depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(this)
            depthStencil.sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
            depthStencil.depthTestEnable(depthTest)
            depthStencil.depthWriteEnable(depthWrite)
            depthStencil.depthCompareOp(VK10.VK_COMPARE_OP_LESS)

            depthStencil.depthBoundsTestEnable(false)
            // TODO: change if stencil enabled
            depthStencil.stencilTestEnable(useStencil)

            val pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, this)
            pipelineInfo.sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            pipelineInfo.pStages(shaderStages)
            pipelineInfo.layout(!pPipelineLayout)
            pipelineInfo.pDepthStencilState(depthStencil)

            pipelineInfo.pColorBlendState(colorBlending)
            pipelineInfo.pVertexInputState(vertexInputInfo)
            pipelineInfo.pInputAssemblyState(inputAssembly)
            pipelineInfo.pViewportState(viewportState)
            pipelineInfo.pRasterizationState(rasterizer)
            pipelineInfo.pMultisampleState(multisampling)

            pipelineInfo.renderPass(renderPass)
            pipelineInfo.subpass(subpass) // TODO: Possible to use the same pipeline with multiple subpasses, granted they are compatible (https://www.khronos.org/registry/vulkan/specs/1.0/html/vkspec.html#renderpass-compatibility)

            // TODO: Base Pipeline for improved performance with similar pipelines

            val pGraphicsPipeline = this.mallocLong(1)
            // TODO: Possibility to instanciate multiple pipelines at once
            VK10.vkCreateGraphicsPipelines(
                VulkanRenderingEngine.logicalDevice,
                VK10.VK_NULL_HANDLE,
                pipelineInfo,
                VulkanRenderingEngine.Allocator,
                pGraphicsPipeline
            ).checkVKErrors()

            val descriptorSetLayoutsList = mutableListOf<VkDescriptorSetLayout>()
            for (i in 0 until 1) { // TODO: support multiple layouts?
                descriptorSetLayoutsList += pDescriptorSetLayout[i]
            }
            GraphicsPipeline(!pGraphicsPipeline, !pPipelineLayout, descriptorSetLayoutsList)
        }

        VK10.vkDestroyShaderModule(
            VulkanRenderingEngine.logicalDevice,
            fragmentShaderModule,
            VulkanRenderingEngine.Allocator
        )
        VK10.vkDestroyShaderModule(
            VulkanRenderingEngine.logicalDevice,
            vertexShaderModule,
            VulkanRenderingEngine.Allocator
        )

        return result
    }

    private fun createShaderModule(memoryStack: MemoryStack, code: ByteArray): VkShaderModule {
        return memoryStack.useStack {
            val createInfo = VkShaderModuleCreateInfo.callocStack(this)
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            val codeBuffer = malloc(code.size)
            codeBuffer.put(code)
            codeBuffer.rewind()
            createInfo.pCode(codeBuffer)

            val pShader = mallocLong(1)
            VK10.vkCreateShaderModule(
                VulkanRenderingEngine.logicalDevice,
                createInfo,
                VulkanRenderingEngine.Allocator,
                pShader
            ).checkVKErrors()
            !pShader
        }
    }
}

class GraphicsPipeline(val handle: VkPipeline, val layout: VkPipelineLayout, val descriptorSetLayouts: List<VkDescriptorSetLayout>) {



}