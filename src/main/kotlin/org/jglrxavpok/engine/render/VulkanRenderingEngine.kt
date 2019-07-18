package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.GameInformation
import org.jglrxavpok.engine.Version
import org.jglrxavpok.engine.*
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.Configuration
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import java.lang.RuntimeException
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import java.nio.ByteBuffer

/**
 * Vulkan implementation of the render engine
 */
object VulkanRenderingEngine: IRenderEngine {

    val EngineName = "jglrEngine"
    val Version = Version(0, 0, 1, "indev")
    val RenderWidth: Int = 1920
    val RenderHeight: Int = 1080
    private var enableValidationLayers: Boolean = true
    private var windowPointer: Long = -1L

    private val validationLayers = listOf("VK_LAYER_LUNARG_standard_validation")
    private val deviceExtensions = listOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
    private val memoryStack = MemoryStack.create()
    // Start of Vulkan objects

    private lateinit var vulkan: VkInstance

    private var debugger: VkDebugUtilsMessengerEXT = NULL

    private var surface: VkSurfaceKHR = NULL
    private var swapchain: VkSwapchainKHR = NULL
    private lateinit var physicalDevice: VkPhysicalDevice
    private lateinit var logicalDevice: VkDevice
    private lateinit var graphicsQueue: VkQueue
    private lateinit var presentQueue: VkQueue
    private var swapchainFormat: Int = -1
    private var swapchainPresentMode: VkPresentModeKHR = -1
    private var renderPass: VkRenderPass = -1
    private var pipelineLayout: VkPipelineLayout = -1
    private var graphicsPipeline: VkPipeline = -1
    private var commandPool: VkCommandPool = -1
    private var imageAvailableSemaphore: VkSemaphore = -1
    private var renderFinishedSemaphore: VkSemaphore = -1
    private lateinit var swapchainExtent: VkExtent2D
    private lateinit var swapchainImages: List<VkImage>
    private lateinit var swapchainImageViews: List<VkImageView>
    private lateinit var swapchainFramebuffers: List<VkFramebuffer>
    private lateinit var commandBuffers: List<VkCommandBuffer>

    // End of Vulkan objects

    /**
     * Initializes the render engine
     */
    fun init(gameInfo: GameInformation, enableValidationLayers: Boolean = true) {
        Configuration.DEBUG.set(true)
        Configuration.DISABLE_CHECKS.set(false)
        Configuration.DISABLE_FUNCTION_CHECKS.set(false)
        if(!glfwInit()) {
            throw RuntimeException("GLFW could not be initialized")
        }
        val mode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        windowPointer = glfwCreateWindow(mode.width(), mode.height(), gameInfo.name, glfwGetPrimaryMonitor(), NULL)

        // init Vulkan
        this.enableValidationLayers = enableValidationLayers
        initVulkanInstance(gameInfo, enableValidationLayers)
        if(enableValidationLayers) {
            setupDebug()
        }
        createSurface()
        pickGraphicsCard()
        createLogicalDevice()
        createSwapchain()
        createImageViews()
        createRenderPass()
        createGraphicsPipeline()
        createFramebuffers()
        createCommandPool()
        createCommandBuffers()
        createSemaphores()
    }

    private fun initVulkanInstance(gameInfo: GameInformation, enableValidationLayers: Boolean) {
        if(enableValidationLayers) {
            checkValidationLayersAvailable()
        }
        useStack {
            val appInfo = VkApplicationInfo.callocStack(this)
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            appInfo.apiVersion(VK_API_VERSION_1_0)
            appInfo.applicationVersion(VK_MAKE_VERSION(gameInfo.version.major, gameInfo.version.minor, gameInfo.version.patch))
            appInfo.engineVersion(VK_MAKE_VERSION(Version.major, Version.minor, Version.patch))

            appInfo.pApplicationName(UTF8(gameInfo.name))
            appInfo.pEngineName(UTF8(EngineName))

            val createInfo = VkInstanceCreateInfo.callocStack(this)
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            createInfo.pApplicationInfo(appInfo)

            createInfo.ppEnabledExtensionNames(requiredExtensions(this))

            if(enableValidationLayers) {
                val layerNames = createNamesBuffer(validationLayers, this)
                createInfo.ppEnabledLayerNames(layerNames)
            }

            val vkPointer = mallocPointer(1)
            vkCreateInstance(createInfo, null, vkPointer).checkVKErrors()
            vulkan = VkInstance(!vkPointer, createInfo)
        }
    }

    private fun setupDebug() {
        if( ! vulkan.capabilities.VK_EXT_debug_utils) {
            error("Impossible to setup Vulkan logging, capability is not present")
        }
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
            createInfo.sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
            createInfo.pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
                val message = VkDebugUtilsMessengerCallbackDataEXT.npMessageString(pCallbackData)
                val colorPrefix =
                    when(messageSeverity) {
                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT -> "\u001b[33m"
                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT -> "\u001b[31m"
                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT -> "\u001b[34m"
                        else -> ""
                    }
                val typePrefixes = mutableListOf<String>()
                if(messageTypes and VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT != 0) typePrefixes += "General"
                if(messageTypes and VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT != 0) typePrefixes += "Violation"
                if(messageTypes and VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT != 0) typePrefixes += "Performance"
                val reset = "\u001B[0m"
                println("$colorPrefix[Vulkan.${typePrefixes.joinToString(".")}] $message$reset")
                false.vk
            }
            createInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT)
            createInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)

            val ptr = stack.mallocLong(1)
            EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(vulkan, createInfo, null, ptr).checkVKErrors()
            debugger = !ptr
        }
    }

    private fun createSurface() {
        useStack {
            val pSurface = mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(vulkan, windowPointer, null, pSurface).checkVKErrors()
            surface = !pSurface
        }
    }

    private fun pickGraphicsCard() {
        useStack {
            val countPtr = this.mallocInt(1)
            vkEnumeratePhysicalDevices(vulkan, countPtr, null).checkVKErrors()
            if(!countPtr == 0) {
                error("Failed to find GPUs with Vulkan support!")
            }

            val devices = this.mallocPointer(!countPtr)
            vkEnumeratePhysicalDevices(vulkan, countPtr, devices).checkVKErrors()
            physicalDevice = devices.it().map { VkPhysicalDevice(it, vulkan) }.firstOrNull(VulkanRenderingEngine::isValidDevice) ?: error("Could not find a suitable physical device!")
        }
    }

    private fun createLogicalDevice() {
        useStack {
            val indices = findQueueFamilies(physicalDevice)

            val createQueueInfoBuffer = VkDeviceQueueCreateInfo.callocStack(indices.asIterable().count(), this)

            for((index, family) in indices.withIndex()) {
                val createQueueInfo = createQueueInfoBuffer[index]
                createQueueInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                createQueueInfo.queueFamilyIndex(family!!)
                val queuePriorities = floats(1f)
                createQueueInfo.pQueuePriorities(queuePriorities)
            }

            val usedFeatures = VkPhysicalDeviceFeatures.callocStack(this)
            // TODO: use features

            val createDeviceInfo = VkDeviceCreateInfo.callocStack(this)
            createDeviceInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            createDeviceInfo.pEnabledFeatures(usedFeatures)
            createDeviceInfo.pQueueCreateInfos(createQueueInfoBuffer)
            createDeviceInfo.ppEnabledExtensionNames(createNamesBuffer(deviceExtensions, this))

            if(enableValidationLayers) {
                createDeviceInfo.ppEnabledLayerNames(createNamesBuffer(validationLayers, this))
            }

            val pDevice = mallocPointer(1)
            vkCreateDevice(physicalDevice, createDeviceInfo, null, pDevice).checkVKErrors()
            logicalDevice = VkDevice(!pDevice, physicalDevice, createDeviceInfo)
            fetchQueues(indices)
        }
    }

    private fun createSwapchain() {
        useStack {
            val support = querySwapchainSupport(physicalDevice)
            val format = chooseSwapSurfaceFormat(support.formats)
            swapchainFormat = format.format()
            swapchainPresentMode = chooseSwapPresentMode(support.presentModes)
            swapchainExtent = VkExtent2D.create().set(chooseSwapExtent(support.capabilities))

            var imageCount = support.capabilities.minImageCount() +1
            if(support.capabilities.maxImageCount() > 0) { // finite amount of images available
                imageCount = imageCount.coerceAtMost(support.capabilities.maxImageCount())
            }

            val createInfo = VkSwapchainCreateInfoKHR.callocStack(this)
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            createInfo.imageExtent(swapchainExtent)
            createInfo.imageFormat(format.format())
            createInfo.imageColorSpace(format.colorSpace())
            createInfo.presentMode(swapchainPresentMode)
            createInfo.minImageCount(imageCount)
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) // VK_IMAGE_USAGE_TRANSFER_DST_BIT  for post-processing
            createInfo.imageArrayLayers(1)
            createInfo.surface(surface)

            // specify sharing mode
            val indices = findQueueFamilies(physicalDevice)
            if(indices.present != indices.graphics) { // no sharing
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                val pIndices = ints(indices.present!!, indices.graphics!!)
                createInfo.pQueueFamilyIndices(pIndices)
            } else { // share
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                createInfo.pQueueFamilyIndices(null)
            }

            createInfo.preTransform(support.capabilities.currentTransform())
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) // can be used for blending windows
            createInfo.clipped(true)
            createInfo.oldSwapchain(VK_NULL_HANDLE) // TODO: use for window resizing

            val pSwapchain = mallocLong(1)
            vkCreateSwapchainKHR(logicalDevice, createInfo, null, pSwapchain).checkVKErrors()
            swapchain = !pSwapchain

            val pCount = mallocInt(1)
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pCount, null)
            val pImages = mallocLong(!pCount)
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pCount, pImages)
            swapchainImages = pImages.it().toList()
        }
    }

    private fun createImageViews() {
        useStack {
            val imageViews = mutableListOf<VkImageView>()
            for(image in swapchainImages) {
                val createInfo = VkImageViewCreateInfo.callocStack(this)
                createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                createInfo.image(image)
                createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D)
                createInfo.format(swapchainFormat)
                createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY)
                createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY)
                createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY)
                createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY)
                createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                // no mipmapping
                createInfo.subresourceRange().baseMipLevel(0)
                createInfo.subresourceRange().levelCount(1)
                createInfo.subresourceRange().baseArrayLayer(0)
                createInfo.subresourceRange().layerCount(1)

                val pImageView = mallocLong(1)
                vkCreateImageView(logicalDevice, createInfo, null, pImageView).checkVKErrors()
                imageViews.add(!pImageView)
            }
            swapchainImageViews = imageViews
        }
    }

    private fun createRenderPass() {
        useStack {
            val colorAttachment = VkAttachmentDescription.callocStack(1, this)
            colorAttachment.format(swapchainFormat)
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT)

            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE)

            // TODO: stencil load/store behaviors
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)

            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            val colorAttachmentRef = VkAttachmentReference.callocStack(1, this)
            colorAttachmentRef.attachment(0)
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            // TODO: Multiple subpass for post-processing
            val subpass = VkSubpassDescription.callocStack(1, this)
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            subpass.colorAttachmentCount(1)
            subpass.pColorAttachments(colorAttachmentRef)

            val dependency = VkSubpassDependency.callocStack(1, this)
            dependency.srcSubpass(VK_SUBPASS_EXTERNAL)
            dependency.dstSubpass(0)

            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            dependency.srcAccessMask(0)

            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

            val renderPassInfo = VkRenderPassCreateInfo.callocStack(this)
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            renderPassInfo.pAttachments(colorAttachment)
            renderPassInfo.pSubpasses(subpass)
            renderPassInfo.pDependencies(dependency)

            val pRenderPass = mallocLong(1)
            vkCreateRenderPass(logicalDevice, renderPassInfo, null, pRenderPass).checkVKErrors()
            renderPass = !pRenderPass
        }
    }

    private fun createGraphicsPipeline() {
        // TODO: Combine?
        // TODO Specialization Info
        val fragCode = javaClass.getResourceAsStream("/shaders/default.fragc").readBytes()
        val vertCode = javaClass.getResourceAsStream("/shaders/default.vertc").readBytes()
        val fragmentShaderModule = createShaderModule(fragCode)
        val vertexShaderModule = createShaderModule(vertCode)

        useStack {
            val vertShaderStageInfo = VkPipelineShaderStageCreateInfo.callocStack(this)
            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            vertShaderStageInfo.module(vertexShaderModule)
            vertShaderStageInfo.stage(VK_SHADER_STAGE_VERTEX_BIT)
            vertShaderStageInfo.pName(UTF8("main"))


            val fragShaderStageInfo = VkPipelineShaderStageCreateInfo.callocStack(this)
            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            fragShaderStageInfo.module(fragmentShaderModule)
            fragShaderStageInfo.stage(VK_SHADER_STAGE_FRAGMENT_BIT)
            fragShaderStageInfo.pName(UTF8("main"))

            val shaderStages = VkPipelineShaderStageCreateInfo.callocStack(2, this)
            shaderStages.put(vertShaderStageInfo)
            shaderStages.put(fragShaderStageInfo)
            shaderStages.flip()

            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.callocStack(this)
            vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            // TODO: Filled later

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(this)
            inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
            inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            inputAssembly.primitiveRestartEnable(false)

            val viewport = VkViewport.callocStack(1, this)
            viewport.width(swapchainExtent.width().toFloat())
            viewport.height(swapchainExtent.height().toFloat())
            viewport.minDepth(0f)
            viewport.maxDepth(1f)

            val scissor = VkRect2D.callocStack(1, this)
            scissor.offset(VkOffset2D.callocStack(this).set(0,0))
            scissor.extent(swapchainExtent)

            val viewportState = VkPipelineViewportStateCreateInfo.callocStack(this)
            viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
            viewportState.pScissors(scissor)
            viewportState.pViewports(viewport)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(this)
            rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
            rasterizer.depthClampEnable(false) // TODO: Use 'true' for shadow maps

            rasterizer.rasterizerDiscardEnable(false)

            rasterizer.polygonMode(VK_POLYGON_MODE_FILL) // TODO: use LINE for wireframe (requires GPU feature)

            rasterizer.lineWidth(1f)

            rasterizer.cullMode(VK_CULL_MODE_BACK_BIT)
            rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE)

            rasterizer.depthBiasEnable(false) // TODO: can be used for shadow mapping

            val multisampling = VkPipelineMultisampleStateCreateInfo.callocStack(this)
            multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
            multisampling.sampleShadingEnable(false)
            multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

            // TODO: Depth&Stencil buffers

            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(1, this)
            colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT)

            // configured for alpha blending
            colorBlendAttachment.blendEnable(true)
            colorBlendAttachment.srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
            colorBlendAttachment.dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
            colorBlendAttachment.colorBlendOp(VK_BLEND_OP_ADD)
            colorBlendAttachment.srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
            colorBlendAttachment.dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
            colorBlendAttachment.alphaBlendOp(VK_BLEND_OP_ADD)

            val colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(this)
            colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            colorBlending.logicOpEnable(false)
            colorBlending.pAttachments(colorBlendAttachment)

            // TODO: Dynamic state goes here

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(this)
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)

            val pPipelineLayout = mallocLong(1)
            vkCreatePipelineLayout(logicalDevice, pipelineLayoutInfo, null, pPipelineLayout).checkVKErrors()
            pipelineLayout = !pPipelineLayout

            val pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(1, this)
            pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            pipelineInfo.pStages(shaderStages)
            pipelineInfo.layout(pipelineLayout)

            pipelineInfo.pColorBlendState(colorBlending)
            pipelineInfo.pVertexInputState(vertexInputInfo)
            pipelineInfo.pInputAssemblyState(inputAssembly)
            pipelineInfo.pViewportState(viewportState)
            pipelineInfo.pRasterizationState(rasterizer)
            pipelineInfo.pMultisampleState(multisampling)

            pipelineInfo.renderPass(renderPass)
            pipelineInfo.subpass(0) // TODO: Possible to use the same pipeline with multiple subpasses, granted they are compatible (https://www.khronos.org/registry/vulkan/specs/1.0/html/vkspec.html#renderpass-compatibility)

            // TODO: Base Pipeline for improved performance with similar pipelines

            val pGraphicsPipeline = mallocLong(1)
            // TODO: Possibility to instanciate multiple pipelines at once
            vkCreateGraphicsPipelines(logicalDevice, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline).checkVKErrors()
            graphicsPipeline = !pGraphicsPipeline
        }

        vkDestroyShaderModule(logicalDevice, fragmentShaderModule, null)
        vkDestroyShaderModule(logicalDevice, vertexShaderModule, null)
    }

    private fun createFramebuffers() {
        val framebuffers = mutableListOf<VkFramebuffer>()
        useStack {
            swapchainImageViews.forEach {
                val attachments = mallocLong(1)
                attachments.put(it)
                attachments.rewind()

                val framebufferInfo = VkFramebufferCreateInfo.callocStack(this)
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                framebufferInfo.width(swapchainExtent.width())
                framebufferInfo.height(swapchainExtent.height())
                framebufferInfo.layers(1)
                framebufferInfo.pAttachments(attachments)
                framebufferInfo.renderPass(renderPass)

                val pFramebuffer = mallocLong(1)
                vkCreateFramebuffer(logicalDevice, framebufferInfo, null, pFramebuffer).checkVKErrors()
                framebuffers += !pFramebuffer
            }
        }
        swapchainFramebuffers = framebuffers
    }

    private fun createCommandPool() {
        useStack {
            val familyIndices = findQueueFamilies(physicalDevice)
            val poolInfo = VkCommandPoolCreateInfo.callocStack(this)
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            poolInfo.queueFamilyIndex(familyIndices.graphics!!)
            poolInfo.flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT or VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT) // some buffers are rerecorded very often and not all should be reset at once

            val pCommandPool = mallocLong(1)
            vkCreateCommandPool(logicalDevice, poolInfo, null, pCommandPool).checkVKErrors()
            commandPool = !pCommandPool
        }
    }

    private fun createCommandBuffers() {
        val count = swapchainFramebuffers.size
        useStack {
            val allocationInfo = VkCommandBufferAllocateInfo.callocStack(this)
            allocationInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocationInfo.commandBufferCount(count)
            allocationInfo.commandPool(commandPool)
            allocationInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY) // SECONDARY for reuse in other buffers

            val pBuffers = mallocPointer(count)
            vkAllocateCommandBuffers(logicalDevice, allocationInfo, pBuffers).checkVKErrors()
            commandBuffers = pBuffers.it().map { VkCommandBuffer(it, logicalDevice) }


            // recording
            commandBuffers.forEachIndexed { index, it ->
                val beginInfo = VkCommandBufferBeginInfo.callocStack(this)
                beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                beginInfo.pInheritanceInfo(null)

                vkBeginCommandBuffer(it, beginInfo).checkVKErrors()

                val renderPassInfo = VkRenderPassBeginInfo.callocStack(this)
                renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                renderPassInfo.renderPass(renderPass)
                renderPassInfo.framebuffer(swapchainFramebuffers[index])

                renderPassInfo.renderArea().offset(VkOffset2D.callocStack(this).set(0,0))
                renderPassInfo.renderArea().extent(swapchainExtent)

                val clearColor = VkClearValue.callocStack(1, this)
                clearColor.color(VkClearColorValue.callocStack(this).float32(floats(0f, 0f, 0f, 1f)))
                renderPassInfo.pClearValues(clearColor)

                vkCmdBeginRenderPass(it, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE) // VK_SUBPASS_CONTENTS_INLINE -> primary buffer only

                vkCmdBindPipeline(it, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline)
                vkCmdDraw(it, 3, 1, 0, 0)

                vkCmdEndRenderPass(it)

                vkEndCommandBuffer(it).checkVKErrors()
            }
        }
    }

    private fun createSemaphores() {
        useStack {
            val semaphoreInfo = VkSemaphoreCreateInfo.callocStack(this)
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

            val pSemaphores = mallocLong(1)
            vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pSemaphores).checkVKErrors()
            imageAvailableSemaphore = !pSemaphores
            vkCreateSemaphore(logicalDevice, semaphoreInfo, null, pSemaphores).checkVKErrors()
            renderFinishedSemaphore = !pSemaphores
        }
    }

    private fun createShaderModule(code: ByteArray): VkShaderModule {
        return useStack {
            val createInfo = VkShaderModuleCreateInfo.callocStack(this)
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            val codeBuffer = malloc(code.size)
            codeBuffer.put(code)
            codeBuffer.rewind()
            createInfo.pCode(codeBuffer)

            val pShader = mallocLong(1)
            vkCreateShaderModule(logicalDevice, createInfo, null, pShader).checkVKErrors()
            !pShader
        }
    }

    private fun MemoryStack.querySwapchainSupport(device: VkPhysicalDevice): SwapchainSupportDetails {
        val details = SwapchainSupportDetails()
        details.capabilities = VkSurfaceCapabilitiesKHR.callocStack(this)
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities)

        val pCount = mallocInt(1)
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, pCount, null)
        val formats = VkSurfaceFormatKHR.callocStack(!pCount, this)
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, pCount, formats)
        details.formats += formats

        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, pCount, null)
        val modes = mallocInt(!pCount)
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, pCount, modes)
        details.presentModes += modes.it()

        return details
    }

    private fun fetchQueues(indices: QueueFamilies) {
        useStack {
            val pQueue = mallocPointer(1)
            vkGetDeviceQueue(logicalDevice, indices.graphics!!, 0, pQueue)
            graphicsQueue = VkQueue(!pQueue, logicalDevice)

            vkGetDeviceQueue(logicalDevice, indices.present!!, 0, pQueue)
            presentQueue = VkQueue(!pQueue, logicalDevice)
        }
    }

    private fun createNamesBuffer(names: Collection<String>, stack: MemoryStack): PointerBuffer? {
        val layerNames = stack.mallocPointer(names.size)
        for(layer in names) {
            layerNames.put(stack.UTF8(layer))
        }
        layerNames.flip()
        return layerNames
    }

    private fun findQueueFamilies(device: VkPhysicalDevice): QueueFamilies {
        return useStack {
            val families = QueueFamilies()
            val pCount = mallocInt(1)
            vkGetPhysicalDeviceQueueFamilyProperties(device, pCount, null)

            val properties = VkQueueFamilyProperties.callocStack(!pCount, this)
            vkGetPhysicalDeviceQueueFamilyProperties(device, pCount, properties)
            for((index, prop) in properties.withIndex()) {
                if(prop.queueCount() > 0 && prop.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                    families.graphics = index
                }

                val pSupport = mallocInt(1)
                vkGetPhysicalDeviceSurfaceSupportKHR(device, index, surface, pSupport).checkVKErrors()
                val supportsPresent = !pSupport == VK_TRUE
                if(prop.queueCount() > 0 && supportsPresent) {
                    families.present = index
                }

                if(families.isComplete)
                    break
            }
            families
        }
    }

    private fun isValidDevice(device: VkPhysicalDevice): Boolean {
        if(device.address() == VK_NULL_HANDLE)
            return false
        return useStack {
            val features = VkPhysicalDeviceFeatures.callocStack(this)
            val properties = VkPhysicalDeviceProperties.callocStack(this)
            vkGetPhysicalDeviceFeatures(device, features)
            vkGetPhysicalDeviceProperties(device, properties)
            if(!checkExtensions(device)) return@useStack false
            val swapchainSupport = querySwapchainSupport(device)
            if(swapchainSupport.formats.isEmpty() || swapchainSupport.presentModes.isEmpty()) return@useStack false
            findQueueFamilies(device).isComplete
        }
    }

    private fun chooseSwapSurfaceFormat(formats: List<VkSurfaceFormatKHR>): VkSurfaceFormatKHR {
        for(format in formats) {
            // try to get the best format possible
            if(format.format() == VK_FORMAT_B8G8R8A8_UNORM && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format
            }
        }
        // otherwise just take the first available
        return formats[0]
    }

    private fun chooseSwapPresentMode(modes: List<VkPresentModeKHR>): VkPresentModeKHR {
        if(VK_PRESENT_MODE_MAILBOX_KHR in modes) {
            return VK_PRESENT_MODE_MAILBOX_KHR
        }
        if(VK_PRESENT_MODE_FIFO_KHR in modes) {
            return VK_PRESENT_MODE_FIFO_KHR
        }
        return VK_PRESENT_MODE_IMMEDIATE_KHR
    }

    private fun MemoryStack.chooseSwapExtent(capabilities: VkSurfaceCapabilitiesKHR): VkExtent2D {
        if(capabilities.currentExtent().width().toLong() != UInt.MAX_VALUE.toLong()) {
            return capabilities.currentExtent()
        }
        val actualExtent = VkExtent2D.callocStack(this)
        actualExtent.width(RenderWidth.coerceIn(capabilities.minImageExtent().width() .. capabilities.maxImageExtent().width()))
        actualExtent.height(RenderHeight.coerceIn(capabilities.minImageExtent().height() .. capabilities.maxImageExtent().height()))
        return actualExtent
    }

    private fun checkExtensions(device: VkPhysicalDevice): Boolean {
        return useStack {
            val pCount = mallocInt(1)
            vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, pCount, null)

            val properties = VkExtensionProperties.callocStack(!pCount, this)
            vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, pCount, properties)

            val availableExtensions = properties.map { it.extensionNameString() }
            deviceExtensions.all { it in availableExtensions } // ensure all device extensions are supported
        }
    }

    private fun requiredExtensions(stack: MemoryStack): PointerBuffer {
        val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()!!
        val extensions = stack.mallocPointer(glfwExtensions.capacity() + 1)
        extensions.put(glfwExtensions)
        extensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME))
        extensions.flip()
        return extensions
    }

    /**
     * Find Vulkan validation layers available on this platform
     */
    private fun checkValidationLayersAvailable() {
        useStack {
            val countBuffer = mallocInt(1)
            vkEnumerateInstanceLayerProperties(countBuffer, null).checkVKErrors()
            val layers = VkLayerProperties.callocStack(countBuffer[0], this)
            vkEnumerateInstanceLayerProperties(countBuffer, layers).checkVKErrors()

            for(layerName in validationLayers) {
                var found = false

                layers.forEach { layer ->
                    if(layer.layerNameString() == layerName) {
                        found = true
                        return@forEach
                    }
                }

                if(!found) {
                    error("Missing validation layer '$layerName'")
                }
            }
        }

        println("Found all validation layers")
    }

    /**
     * Main loop, record, presenting, swapping, etc.
     */
    fun loop() {
        // TODO: Multithreading - Vulkan "allows multiple threads to create and submit commands in parallel"
        while(!glfwWindowShouldClose(windowPointer)) {
            glfwPollEvents()
            drawFrame()
            // TODO
            //glfwSwapBuffers(windowPointer)
        }
    }

    private fun drawFrame() {
        useStack {
            val pImageIndex = mallocInt(1)
            vkAcquireNextImageKHR(logicalDevice, swapchain, Long.MAX_VALUE, imageAvailableSemaphore, VK_NULL_HANDLE, pImageIndex)

            graphicsQueueSubmit(!pImageIndex)

            present(!pImageIndex)
        }
    }

    private fun MemoryStack.graphicsQueueSubmit(imageIndex: Int) {
        val submitInfo = VkSubmitInfo.callocStack(this)
        submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)

        val waitSemaphores = mallocLong(1)
        waitSemaphores.put(imageAvailableSemaphore).flip()

        val waitStages = mallocInt(1)
        waitStages.put(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).flip()

        submitInfo.pWaitSemaphores(waitSemaphores)
        submitInfo.waitSemaphoreCount(1)
        submitInfo.pWaitDstStageMask(waitStages)

        val signalSemaphores = mallocLong(1)
        signalSemaphores.put(renderFinishedSemaphore).flip()
        submitInfo.pSignalSemaphores(signalSemaphores)

        val buffers = mallocPointer(1)
        buffers.put(commandBuffers[imageIndex].address()).flip()
        submitInfo.pCommandBuffers(buffers)

        vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE).checkVKErrors()
    }

    private fun MemoryStack.present(imageIndex: Int) {
        val presentInfo = VkPresentInfoKHR.callocStack(this)
        presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
        val waitSemaphores = longs(renderFinishedSemaphore)
        presentInfo.pWaitSemaphores(waitSemaphores)

        presentInfo.swapchainCount(1)

        val pSwapchain = longs(swapchain)
        presentInfo.pSwapchains(pSwapchain)

        val imageIndices = ints(imageIndex)
        presentInfo.pImageIndices(imageIndices)

        presentInfo.pResults(null)

        vkQueuePresentKHR(presentQueue, presentInfo)
    }

    /**
     * Cleanup native objects
     */
    fun cleanup() {
        vkDestroySemaphore(logicalDevice, imageAvailableSemaphore, null)
        vkDestroySemaphore(logicalDevice, renderFinishedSemaphore, null)
        swapchainFramebuffers.forEach {
            vkDestroyFramebuffer(logicalDevice, it, null)
        }
        vkDestroyPipeline(logicalDevice, graphicsPipeline, null)
        vkDestroyPipelineLayout(logicalDevice, pipelineLayout, null)
        vkDestroyRenderPass(logicalDevice, renderPass, null)
        for(view in swapchainImageViews) {
            vkDestroyImageView(logicalDevice, view, null)
        }
        vkDestroySwapchainKHR(logicalDevice, swapchain, null)
        vkDestroyDevice(logicalDevice, null)
        if(enableValidationLayers) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkan, debugger, null)
        }
        vkDestroySurfaceKHR(vulkan, surface, null)
        vkDestroyInstance(vulkan, null)
        glfwDestroyWindow(windowPointer)
        glfwTerminate()
    }

    private fun <T> useStack(action: MemoryStack.() -> T) = memoryStack.push().use(action)
}