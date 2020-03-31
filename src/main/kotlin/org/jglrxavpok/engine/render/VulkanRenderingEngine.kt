package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.GameInformation
import org.jglrxavpok.engine.Version
import org.jglrxavpok.engine.*
import org.jglrxavpok.engine.render.model.Mesh
import org.jglrxavpok.engine.render.model.Model
import org.joml.Vector3f
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
import org.jglrxavpok.engine.scene.Scene
import org.lwjgl.stb.STBImage
import org.lwjgl.vulkan.EXTDescriptorIndexing.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VK11.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
import java.nio.LongBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore

/**
 * Vulkan implementation of the render engine
 */
object VulkanRenderingEngine: IRenderEngine {

    val MaxObjects = 256_000
    val MaxTextures = 256
    val SSAOKernelSize = 16

    val EngineName = "jglrEngine"
    val Version = Version(0, 0, 1, "indev")
    val RenderWidth: Int = 1920
    val RenderHeight: Int = 1080
    val Allocator: VkAllocationCallbacks? = null

    private var enableValidationLayers: Boolean = true
    private var windowPointer: Long = -1L
    private var framebufferResized = false
    private val maxFramesInFlight = 3

    private val validationLayers = listOf("VK_LAYER_LUNARG_standard_validation", "VK_LAYER_LUNARG_monitor")
    private val deviceExtensions = listOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
    internal val memoryStack = MemoryStack.create(512 * 1024*1024) // 512 MB

    // Start of Vulkan objects

    private lateinit var vulkan: VkInstance
    private var debugger: VkDebugUtilsMessengerEXT = NULL

    private var surface: VkSurfaceKHR = NULL
    private var swapchain: VkSwapchainKHR = NULL
    private lateinit var physicalDevice: VkPhysicalDevice
    internal lateinit var logicalDevice: VkDevice
    private lateinit var graphicsQueue: VkQueue
    private lateinit var presentQueue: VkQueue
    private var swapchainFormat: Int = -1
    private var swapchainPresentMode: VkPresentModeKHR = -1
    private var renderPass: VkRenderPass = -1
    private var graphicsPipeline: VkPipeline = -1
    private var commandPool: VkCommandPool = -1
    private var descriptorPool: VkDescriptorPool = -1

    internal var linearSampler: VkSampler = -1

    private var depthImage: VkImage = -1
    private var depthImageMemory: VkDeviceMemory = -1
    private var depthImageView: VkImageView = -1

    private lateinit var imageAvailableSemaphores: List<VkSemaphore>
    private lateinit var renderFinishedSemaphores: List<VkSemaphore>
    private lateinit var inFlightFences: List<VkFence>
    private lateinit var swapchainExtent: VkExtent2D
    private lateinit var swapchainImages: List<VkImage>
    private lateinit var swapchainImageViews: List<VkImageView>
    private lateinit var swapchainFramebuffers: List<VkFramebuffer>
    private lateinit var commandBuffers: List<VkCommandBuffer>

    private var currentFrame = 0

    // End of Vulkan objects
    lateinit var defaultCamera: Camera

    private var previousPosX = 0.0
    private var previousPosY = 0.0

    private var scene: Scene? = null
    private val frameActions = LinkedBlockingQueue<() -> Unit>()
    private val frameLoopActions = LinkedBlockingQueue<() -> Unit>()
    private var renderThread: Thread? = null
    private val initSemaphore = Semaphore(1).apply { acquire() }

    private val activeTextures = mutableMapOf<String, Texture>()
    private val activeModels = mutableMapOf<String, Model>()
    lateinit var WhiteTexture: Texture
    private lateinit var noiseTexture: Texture

    lateinit var gBufferShaderDescriptor: DescriptorSet
    lateinit var lightingShaderDescriptor: DescriptorSet
    lateinit var ssaoShaderDescriptor: DescriptorSet
    lateinit var renderToScreenShaderDescriptor: DescriptorSet
    lateinit var emptyDescriptor: DescriptorSet
    private lateinit var uboMemories:  List<VkDeviceMemory>
    private lateinit var ssaoMemories:  List<VkDeviceMemory>
    private val imageViews = mutableMapOf<Int, VkImageView>()
    private val renderGroups = mutableListOf<RenderGroup>()
    private var textureID = 0
    private var uboID = 0

    lateinit var gBufferPipeline: GraphicsPipeline
    lateinit var renderToScreenPipeline: GraphicsPipeline
    lateinit var lightingPipeline: GraphicsPipeline
    lateinit var ssaoPipeline: GraphicsPipeline
    lateinit var defaultRenderGroup: RenderGroup

    /**
     * Special render group to compile the gbuffer info onto the screen
     */
    lateinit var toScreenGroup: RenderGroup
    private val gColorImages = mutableListOf<ImageInfo>()
    private val gPosImages = mutableListOf<ImageInfo>()
    private val gNormalImages = mutableListOf<ImageInfo>()
    private val lightingOutImages = mutableListOf<ImageInfo>()
    private val ssaoOutImages = mutableListOf<ImageInfo>()
    private lateinit var screenQuad: Mesh
    private val ssaoBufferObject = SSAOBufferObject(SSAOKernelSize)

    // Render subpasses
    private val GBufferSubpass = 0
    private val LightingSubpass = 1
    private val SSAOSubpass = 2
    private val ResolveSubpass = 3
    // -------

    private val NoiseTextureArrayIndex = 0

    /**
     * Initializes the render engine
     */
    fun init(gameInfo: GameInformation, game: Game, enableValidationLayers: Boolean = true) {
        renderThread = Thread.currentThread()
        Configuration.DEBUG.set(true)
        Configuration.DISABLE_CHECKS.set(false)
        Configuration.DISABLE_FUNCTION_CHECKS.set(false)
        if(!glfwInit()) {
            throw RuntimeException("GLFW could not be initialized")
        }
        val mode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        windowPointer = glfwCreateWindow(mode.width(), mode.height(), gameInfo.name, NULL, NULL)
        glfwSetWindowSizeCallback(windowPointer) { window, width, height ->
            framebufferResized = true
        }

        glfwSetCursorPosCallback(windowPointer) { window, xpos, ypos ->
            val dx = xpos - previousPosX
            val dy = ypos - previousPosY

            game.onMouseMoveEvent(xpos, ypos, dx, dy)

            previousPosX = xpos
            previousPosY = ypos
        }
        glfwSetKeyCallback(windowPointer) { window, key, scancode, action, mods ->
            game.onKeyEvent(key, scancode, action, mods)
        }
        glfwSetMouseButtonCallback(windowPointer) { window, button, action, mods ->
            game.onMouseButton(button, action)
        }
        glfwSetInputMode(windowPointer, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

        val xpos = DoubleArray(1)
        val ypos = DoubleArray(1)
        glfwGetCursorPos(windowPointer, xpos, ypos)
        previousPosX = xpos[0]
        previousPosY = ypos[0]

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
        createCamera()
        createRenderImageViews()
        renderPass = createRenderPass()
        gBufferPipeline = createGraphicsPipeline(gBufferPipelineBuilder())
        renderToScreenPipeline = createGraphicsPipeline(renderToScreenPipelineBuilder())
        lightingPipeline = createGraphicsPipeline(lightingPipelineBuilder())
        ssaoPipeline = createGraphicsPipeline(ssaoPipelineBuilder())
        createCommandPool()
        createDepthResources()
        createFramebuffers()
        linearSampler = createTextureSampler()
        descriptorPool = createDescriptorPool()

        val uboInfo = prepareUniformBuffers(UniformBufferObject.SizeOf)
        uboMemories = uboInfo.second

        val ssaoInfo = prepareUniformBuffers(SSAOBufferObject.SizeOf(SSAOKernelSize))
        val ssaoBuffers = ssaoInfo.first
        ssaoMemories = ssaoInfo.second

        gBufferShaderDescriptor = createDescriptorSetFromBuilder(
            gBufferPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .sampler(linearSampler)
                .ubo(uboInfo.first)
        )
        lightingShaderDescriptor = createDescriptorSetFromBuilder(
            lightingPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .subpassSampler { index -> gColorImages[index].view }
                .subpassSampler { index -> gPosImages[index].view }
                .subpassSampler { index -> gNormalImages[index].view }
        )
        noiseTexture = createNoiseTexture(VkExtent2D.create().set(4, 4))
        ssaoShaderDescriptor = createDescriptorSetFromBuilder(
            ssaoPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .frameDependentCombinedImageSampler({ index -> gPosImages[index].view }, linearSampler)
                .subpassSampler { index -> gNormalImages[index].view }
                .combinedImageSampler(noiseTexture, linearSampler)
                .uniformBuffer(SSAOBufferObject.SizeOf(SSAOKernelSize), ssaoBuffers::get, dynamic = false)

        )
        renderToScreenShaderDescriptor = createDescriptorSetFromBuilder(
            renderToScreenPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .subpassSampler { index -> lightingOutImages[index].view }
                .subpassSampler { index -> ssaoOutImages[index].view }
        )

        WhiteTexture = createTexture("/textures/white.png")

        defaultRenderGroup = object: RenderGroup {
            override val pipeline get() = gBufferPipeline
        }
        renderGroups += defaultRenderGroup

        screenQuad = Mesh(listOf(
            Vertex(Vector3f(-1f, -1f, 0f)),
            Vertex(Vector3f(-1f, 1f, 0f)),
            Vertex(Vector3f(1f, 1f, 0f)),
            Vertex(Vector3f(1f, -1f, 0f))
            ),
            listOf(0u, 1u, 2u, 2u, 3u, 0u), vertexFormat = VertexFormat.Companion.ScreenPositionOnly)

        createCommandBuffers()
        createSyncingMechanisms()

        initSemaphore.release()
    }

    private fun createNoiseTexture(size: VkExtent2D): Texture {
        return useStack {
            val pImage = mallocLong(1)
            val pImageMemory = mallocLong(1)
            val pixelBuffer = malloc(size.width()*size.height()*2*4)
            val pixels = pixelBuffer.asFloatBuffer()

            val rand = Random()
            for(i in 0 until (pixels.capacity()/2)) {
                val x = rand.nextFloat() * 2f - 1f
                val y = rand.nextFloat() * 2f - 1f
                pixels.put(x).put(y)
            }
            pixels.flip()

            uploadTexture(size.width(), size.height(), pixelBuffer, pImage, pImageMemory)
            val imageView = createImageView(!pImage, VK_FORMAT_R8G8B8A8_SRGB)

            val texture = Texture(NoiseTextureArrayIndex, !pImage, imageView, "generated:noise(${size.width()}x${size.height()})")

            imageViews[texture.textureID] = imageView

            texture
        }
    }

    private fun ssaoPipelineBuilder() = GraphicsPipelineBuilder(1, renderPass, swapchainExtent)
        .vertexShaderModule("/shaders/screenQuad.vertc").fragmentShaderModule("/shaders/gSSAO.fragc")
        .descriptorSetLayoutBindings(
            DescriptorSetLayoutBindings()
                .combinedImageSampler() // gPos
                .subpassSampler() // gNormal
                .combinedImageSampler() // noise
                .uniformBuffer(false) // projection matrix and noise sample vectors
        )
        .depthTest(false)
        .depthWrite(false)
        .subpass(SSAOSubpass)
        .useStencil(false)
        .vertexFormat(VertexFormat.Companion.ScreenPositionOnly)

    private fun lightingPipelineBuilder() = GraphicsPipelineBuilder(1, renderPass, swapchainExtent)
        .vertexShaderModule("/shaders/screenQuad.vertc").fragmentShaderModule("/shaders/gLighting.fragc")
        .descriptorSetLayoutBindings(
            DescriptorSetLayoutBindings()
                .subpassSampler() // gColor
                .subpassSampler() // gPos
                .subpassSampler() // gNormal
            )
        .depthTest(false)
        .depthWrite(false)
        .subpass(LightingSubpass)
        .useStencil(false)
        .vertexFormat(VertexFormat.Companion.ScreenPositionOnly)

    private fun renderToScreenPipelineBuilder() = GraphicsPipelineBuilder(1, renderPass, swapchainExtent)
        .vertexShaderModule("/shaders/screenQuad.vertc").fragmentShaderModule("/shaders/gResolve.fragc")
        .descriptorSetLayoutBindings(DescriptorSetLayoutBindings()
            .subpassSampler() // lighting out
            .subpassSampler() // ssao out
        )
        .depthTest(false)
        .depthWrite(false)
        .subpass(ResolveSubpass)
        .useStencil(false)
        .vertexFormat(VertexFormat.Companion.ScreenPositionOnly)

    private fun gBufferPipelineBuilder() = GraphicsPipelineBuilder(3, renderPass, swapchainExtent)
        .descriptorSetLayoutBindings(DescriptorSetLayoutBindings()
            .uniformBuffer(true)
            .textures(MaxTextures)
            .sampler()
        )
        .subpass(GBufferSubpass)
        .vertexShaderModule("/shaders/gBuffer.vertc")
        .fragmentShaderModule("/shaders/gBuffer.fragc")

    fun getUBOMemory(frameIndex: Int): VkDeviceMemory {
        return uboMemories[frameIndex]
    }

    fun getSSAOMemory(frameIndex: Int): VkDeviceMemory {
        return ssaoMemories[frameIndex]
    }

    private fun initVulkanInstance(gameInfo: GameInformation, enableValidationLayers: Boolean) {
        if(enableValidationLayers) {
            checkValidationLayersAvailable()
        }
        useStack {
            val appInfo = VkApplicationInfo.callocStack(this)
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            appInfo.apiVersion(VK_API_VERSION_1_1)
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
            vkCreateInstance(createInfo, Allocator, vkPointer).checkVKErrors()
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
                val id = VkDebugUtilsMessengerCallbackDataEXT.npMessageIdNameString(pCallbackData) ?: "<>"
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
                println("$colorPrefix[Vulkan.${typePrefixes.joinToString(".")}] ($id) $message$reset")
                false.vk
            }
            createInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT)
            createInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)

            val ptr = stack.mallocLong(1)
            EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(vulkan, createInfo, Allocator, ptr).checkVKErrors()
            debugger = !ptr
        }
    }

    private fun createSurface() {
        useStack {
            val pSurface = mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(vulkan, windowPointer, Allocator, pSurface).checkVKErrors()
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
            usedFeatures.samplerAnisotropy(true)

            val indexingFeatures = VkPhysicalDeviceDescriptorIndexingFeaturesEXT.callocStack(this)
            indexingFeatures.sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT)
            indexingFeatures.descriptorBindingPartiallyBound(true)
            indexingFeatures.runtimeDescriptorArray(true)
            // TODO: use features

            val createDeviceInfo = VkDeviceCreateInfo.callocStack(this)
            createDeviceInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            createDeviceInfo.pNext(indexingFeatures.address())
            createDeviceInfo.pEnabledFeatures(usedFeatures)
            createDeviceInfo.pQueueCreateInfos(createQueueInfoBuffer)
            createDeviceInfo.ppEnabledExtensionNames(createNamesBuffer(deviceExtensions, this))

            if(enableValidationLayers) {
                createDeviceInfo.ppEnabledLayerNames(createNamesBuffer(validationLayers, this))
            }

            val pDevice = mallocPointer(1)
            vkCreateDevice(physicalDevice, createDeviceInfo, Allocator, pDevice).checkVKErrors()
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
            vkCreateSwapchainKHR(logicalDevice, createInfo, Allocator, pSwapchain).checkVKErrors()
            swapchain = !pSwapchain

            val pCount = mallocInt(1)
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pCount, null)
            val pImages = mallocLong(!pCount)
            vkGetSwapchainImagesKHR(logicalDevice, swapchain, pCount, pImages)
            swapchainImages = pImages.it().toList()
        }
    }

    private fun createCamera() {
        defaultCamera = Camera(swapchainExtent.width(), swapchainExtent.height())
        defaultCamera.position.set(0f, 0f, 0f)
    }

    private fun createRenderImageViews() {
        useStack {
            val imageViews = mutableListOf<VkImageView>()
            for(image in swapchainImages) {
                imageViews.add(createImageView(image, swapchainFormat))
            }
            swapchainImageViews = imageViews
        }
    }

    private fun createRenderPass(): VkRenderPass {
        return useStack {
            val attachments = VkAttachmentDescription.callocStack(7, this)
            val finalColorIndex = 0
            val gColorIndex = 1
            val gPosIndex = 2
            val gNormalIndex = 3
            val lightingOutIndex = 4
            val ssaoOutIndex = 5
            val depthIndex = 6
            // 0: final color
            // 1: gColor
            // 2: gPos (world pos)
            // 3: gNormal
            // 4: lightingOut
            // 5: ssaoOut
            // 6: depth
            prepareColorAttachment(attachments.get(finalColorIndex), swapchainFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            prepareColorAttachment(attachments.get(gColorIndex), VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(gPosIndex), VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(gNormalIndex), VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(lightingOutIndex), VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(ssaoOutIndex), VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            for(i in swapchainImages.indices) {
                val gColorInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                gColorImages += gColorInfo

                val gPosInfo = createAttachmentImageView(VK_FORMAT_R16G16B16A16_SFLOAT, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                gPosImages += gPosInfo

                val gNormalInfo = createAttachmentImageView(VK_FORMAT_R16G16B16A16_SFLOAT, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                gNormalImages += gNormalInfo

                val lightingOutInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                lightingOutImages += lightingOutInfo

                val ssaoOutInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                ssaoOutImages += ssaoOutInfo
            }

            val depthAttachment = attachments.get(depthIndex)
            depthAttachment.format(findDepthFormat())
            depthAttachment.samples(VK_SAMPLE_COUNT_1_BIT)
            depthAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            depthAttachment.storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            depthAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            depthAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            depthAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            depthAttachment.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

            val screenBuffer = VkAttachmentReference.callocStack(1, this)
            screenBuffer.attachment(finalColorIndex)
            screenBuffer.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gBuffer = VkAttachmentReference.callocStack(3, this)
            val gColorRenderTarget = gBuffer[0]
            gColorRenderTarget.attachment(gColorIndex)
            gColorRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gPosRenderTarget = gBuffer[1]
            gPosRenderTarget.attachment(gPosIndex)
            gPosRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gNormalRenderTarget = gBuffer[2]
            gNormalRenderTarget.attachment(gNormalIndex)
            gNormalRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val depthAttachmentRef = VkAttachmentReference.callocStack(this)
            depthAttachmentRef.attachment(depthIndex)
            depthAttachmentRef.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

            // TODO: Multiple subpass for post-processing
            val subpasses = VkSubpassDescription.callocStack(4, this)
            val gBufferPass = subpasses.get(GBufferSubpass)
            gBufferPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            gBufferPass.colorAttachmentCount(gBuffer.capacity())
            gBufferPass.pColorAttachments(gBuffer)
            gBufferPass.pDepthStencilAttachment(depthAttachmentRef)

            val gBufferInput = VkAttachmentReference.callocStack(3, this)
            val gColorInput = gBufferInput[0]
            gColorInput.attachment(gColorIndex)
            gColorInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val gPosInput = gBufferInput[1]
            gPosInput.attachment(gColorIndex)
            gPosInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val gNormalInput = gBufferInput[2]
            gNormalInput.attachment(gColorIndex)
            gNormalInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val lightingPass = subpasses.get(LightingSubpass)
            lightingPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            val lightingOut = VkAttachmentReference.callocStack(1, this)
            lightingOut.attachment(lightingOutIndex)
            lightingOut.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            lightingPass.pInputAttachments(gBufferInput) // TODO: + shadow maps
            lightingPass.pColorAttachments(lightingOut)
            lightingPass.colorAttachmentCount(lightingOut.capacity())

            val ssaoInput = VkAttachmentReference.callocStack(2, this)
            ssaoInput[0].attachment(gPosIndex)
            ssaoInput[0].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            ssaoInput[1].attachment(gNormalIndex)
            ssaoInput[1].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val ssaoPass = subpasses.get(SSAOSubpass)
            ssaoPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            val ssaoOut = VkAttachmentReference.callocStack(1, this)
            ssaoOut.attachment(ssaoOutIndex)
            ssaoOut.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            ssaoPass.pInputAttachments(ssaoInput)
            ssaoPass.pColorAttachments(ssaoOut)
            ssaoPass.colorAttachmentCount(ssaoOut.capacity())


            val resolveInputs = VkAttachmentReference.callocStack(2, this)
            resolveInputs[0].attachment(lightingOutIndex)
            resolveInputs[0].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            resolveInputs[1].attachment(ssaoOutIndex)
            resolveInputs[1].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val renderToScreenPass = subpasses.get(ResolveSubpass)
            renderToScreenPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            renderToScreenPass.pInputAttachments(resolveInputs)
            renderToScreenPass.pColorAttachments(screenBuffer)
            renderToScreenPass.colorAttachmentCount(screenBuffer.capacity())


            val dependency = VkSubpassDependency.callocStack(5, this)
            prepareDependency(dependency.get(GBufferSubpass), VK_SUBPASS_EXTERNAL, GBufferSubpass)
            prepareDependency(dependency.get(LightingSubpass), GBufferSubpass, LightingSubpass)
            prepareDependency(dependency.get(SSAOSubpass), GBufferSubpass, SSAOSubpass)

            prepareDependency(dependency.get(3), LightingSubpass, ResolveSubpass)
            prepareDependency(dependency.get(4), SSAOSubpass, ResolveSubpass)

            val renderPassInfo = VkRenderPassCreateInfo.callocStack(this)
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            renderPassInfo.pAttachments(attachments)
            renderPassInfo.pSubpasses(subpasses)
            renderPassInfo.pDependencies(dependency)

            val pRenderPass = mallocLong(1)
            vkCreateRenderPass(logicalDevice, renderPassInfo, Allocator, pRenderPass).checkVKErrors()
            !pRenderPass
        }
    }

    private fun prepareDependency(dependency: VkSubpassDependency, from: Int, to: Int) {
        dependency.srcSubpass(from)
        dependency.dstSubpass(to)

        dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        dependency.srcAccessMask(0)

        dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
    }

    private fun prepareColorAttachment(description: VkAttachmentDescription, format: Int, initialLayout: VkImageLayout, finalLayout: VkImageLayout, loadOp: Int = VK_ATTACHMENT_LOAD_OP_CLEAR) {
        description.format(format)
        description.samples(VK_SAMPLE_COUNT_1_BIT)

        description.loadOp(loadOp)
        description.storeOp(VK_ATTACHMENT_STORE_OP_STORE)

        description.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
        description.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)

        description.initialLayout(initialLayout)
        description.finalLayout(finalLayout)
    }

    fun createGraphicsPipeline(builder: GraphicsPipelineBuilder): GraphicsPipeline {
        return useStack {
            builder.build(this)
        }
    }

    private fun createFramebuffers() {
        val framebuffers = mutableListOf<VkFramebuffer>()
        useStack {
            swapchainImageViews.forEachIndexed { index, it ->
                val attachments = mallocLong(7)
                attachments.put(it) // final color
                attachments.put(gColorImages[index].view) // gColor
                attachments.put(gPosImages[index].view) // gPos
                attachments.put(gNormalImages[index].view) // gNormal
                attachments.put(lightingOutImages[index].view) // lightingOut
                attachments.put(ssaoOutImages[index].view) // ssaoOut
                attachments.put(depthImageView)
                attachments.rewind()

                val framebufferInfo = VkFramebufferCreateInfo.callocStack(this)
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                framebufferInfo.width(swapchainExtent.width())
                framebufferInfo.height(swapchainExtent.height())
                framebufferInfo.layers(1)
                framebufferInfo.pAttachments(attachments)
                framebufferInfo.renderPass(renderPass)

                val pFramebuffer = mallocLong(1)
                vkCreateFramebuffer(logicalDevice, framebufferInfo, Allocator, pFramebuffer).checkVKErrors()
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
            vkCreateCommandPool(logicalDevice, poolInfo, Allocator, pCommandPool).checkVKErrors()
            commandPool = !pCommandPool
        }
    }

    /**
     * Creates the uniform buffers for shaders, one per frame
     */
    fun prepareUniformBuffers(size: Long, count: Int = MaxObjects): Pair<List<VkBuffer>, List<VkDeviceMemory>> {
        return useStack {
            val bufferSize = size*count
            val bufferList = mutableListOf<VkBuffer>()
            val memoryList = mutableListOf<VkDeviceMemory>()
            for (i in swapchainImages.indices) {
                val pBuffer = mallocLong(1)
                val pMemory = mallocLong(1)
                createBuffer(bufferSize, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, pBuffer, pMemory)
                bufferList += !pBuffer
                memoryList += !pMemory
            }

            bufferList to memoryList
        }
    }

    /**
     * Allocates and upload a given descriptor set to Vulkan.
     */
    // TODO: move somewhere else, will depend on shaders
    fun createDescriptorSetFromBuilder(layout: VkDescriptorSetLayout, builder: DescriptorSetUpdateBuilder): DescriptorSet {
        val actions = builder.bindings
        return useStack {
            val allocInfo = VkDescriptorSetAllocateInfo.callocStack(this)
            allocInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            allocInfo.descriptorPool(descriptorPool)
            val layouts = mallocLong(swapchainImages.size)
            for (i in swapchainImages.indices) {
                layouts.put(i, layout)
            }
            allocInfo.pSetLayouts(layouts)

            val pSets = mallocLong(swapchainImages.size)
            if(vkAllocateDescriptorSets(logicalDevice, allocInfo, pSets) != VK_SUCCESS) {
                error("Failed to allocate descriptor sets")
            }
            val descriptorSets = swapchainImages.indices.mapIndexed { index, _ -> pSets[index] }
            val result = DescriptorSet(descriptorSets.toTypedArray())
            updateDescriptorSet(result, builder)
            result
        }
    }

    fun updateDescriptorSet(descriptorSet: DescriptorSet, builder: DescriptorSetUpdateBuilder) {
        val bindings = builder.bindings
        useStack {
            val targets = VkWriteDescriptorSet.callocStack(bindings.size* swapchainImages.size, this)
            for(frameIndex in swapchainImages.indices) {
                val targetSet = descriptorSet[frameIndex]

                for(bindingIndex in bindings.indices) {
                    val target = targets.get(bindingIndex+frameIndex*bindings.size)
                    target.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    target.dstSet(targetSet)

                    bindings[bindingIndex].describe(this, target, targetSet, frameIndex)
                }
            }

            vkUpdateDescriptorSets(logicalDevice, targets, null)
        }
    }

    private fun createDescriptorPool(maxSize: Int = 100): VkDescriptorPool {
        return useStack {
            val poolSize = VkDescriptorPoolSize.callocStack(3, this)
            poolSize.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
            poolSize.get(0).descriptorCount(swapchainImages.size*maxSize) // one per frame

            poolSize.get(1).type(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
            poolSize.get(1).descriptorCount(swapchainImages.size*maxSize*MaxTextures) // one per frame

            poolSize.get(2).type(VK_DESCRIPTOR_TYPE_SAMPLER)
            poolSize.get(2).descriptorCount(swapchainImages.size*maxSize) // one per frame

            val poolInfo = VkDescriptorPoolCreateInfo.callocStack(this)
            poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            poolInfo.pPoolSizes(poolSize)
            poolInfo.maxSets(swapchainImages.size*maxSize)

            val pPool = mallocLong(1)
            if(vkCreateDescriptorPool(logicalDevice, poolInfo, Allocator, pPool) != VK_SUCCESS) {
                error("Failed to create descriptor pool")
            }

            !pPool
        }
    }

    private fun createTextureSampler(filter: Int = VK_FILTER_LINEAR): VkSampler {
        return useStack {
            val samplerInfo = VkSamplerCreateInfo.callocStack(this)
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)

            // todo change for different filters
            samplerInfo.magFilter(filter)
            samplerInfo.minFilter(filter)

            samplerInfo.addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            samplerInfo.addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            samplerInfo.addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)

            samplerInfo.anisotropyEnable(true)
            samplerInfo.maxAnisotropy(16f) // todo: configurable to configure performance

            samplerInfo.borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            samplerInfo.unnormalizedCoordinates(false)

            samplerInfo.compareEnable(false)
            samplerInfo.compareOp(VK_COMPARE_OP_ALWAYS)

            samplerInfo.mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            samplerInfo.mipLodBias(0f)
            samplerInfo.minLod(0f)
            samplerInfo.maxLod(0f)

            val pSampler = mallocLong(1)
            if(vkCreateSampler(logicalDevice, samplerInfo, Allocator, pSampler) != VK_SUCCESS) {
                error("Failed to create texture sampler")
            }
            !pSampler
        }
    }

    private fun createTextureImageView(textureImage: VkImage): VkImageView {
        return useStack {
            createImageView(textureImage, VK_FORMAT_R8G8B8A8_SRGB)
        }
    }

    private fun createImageView(image: VkImage, format: VkFormat, aspect: VkImageAspectFlags = VK_IMAGE_ASPECT_COLOR_BIT): VkImageView {
        return useStack {
            val viewInfo = VkImageViewCreateInfo.callocStack(this)
            viewInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            viewInfo.image(image)
            viewInfo.viewType(VK_IMAGE_VIEW_TYPE_2D)
            viewInfo.format(format)
            viewInfo.subresourceRange().aspectMask(aspect)
            viewInfo.subresourceRange().baseMipLevel(0)
            viewInfo.subresourceRange().baseArrayLayer(0)
            viewInfo.subresourceRange().levelCount(1)
            viewInfo.subresourceRange().layerCount(1)

            val pView = mallocLong(1)
            if(vkCreateImageView(logicalDevice, viewInfo, Allocator, pView) != VK_SUCCESS) {
                error("Failed to create texture image view")
            }

            !pView
        }
    }

    private fun createDepthResources() {
        val imageInfo = createAttachmentImageView(findDepthFormat(), swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_IMAGE_ASPECT_DEPTH_BIT)
        depthImage = imageInfo.image
        depthImageMemory = imageInfo.memory
        depthImageView = imageInfo.view
    }

    private fun createAttachmentImageView(format: VkFormat, width: Int, height: Int, usage: VkImageUsageFlags, aspect: VkImageAspectFlags): ImageInfo = useStack {
        val pImage = mallocLong(1)
        val pMemory = mallocLong(1)
        createImage(width, height, format, usage, pImage, pMemory)
        ImageInfo(!pImage, !pMemory, createImageView(!pImage, format, aspect))
    }

    private fun hasStencilComponent(format: VkFormat) = format == VK_FORMAT_D24_UNORM_S8_UINT || format == VK_FORMAT_D32_SFLOAT_S8_UINT

    private fun findDepthFormat() = findSupportedFormat(VK_IMAGE_TILING_OPTIMAL, VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT)

    private fun findSupportedFormat(tiling: VkImageTiling, features: VkFormatFeatureFlags, vararg candidates: VkFormat): VkFormat = useStack {
        val props = VkFormatProperties.callocStack(this)
        for(format in candidates) {
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props)
            if(tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() and features) == features) {
                return@useStack format
            } else if(tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() and features) == features) {
                return@useStack format
            }
        }
        error("Could not find supported format")
    }

    private fun createTextureImage(path: String, pImage: LongBuffer, pImageMemory: LongBuffer) {
        useStack {
            // read file bytes
            val textureData = javaClass.getResource(path).readBytes()
            val textureDataBuffer = malloc(textureData.size)
            textureDataBuffer.put(textureData)
            textureDataBuffer.position(0)

            // load image
            val pWidth = mallocInt(1)
            val pHeight = mallocInt(1)
            val pChannels = mallocInt(1)
            val pixels = STBImage.stbi_load_from_memory(textureDataBuffer, pWidth, pHeight, pChannels, STBImage.STBI_rgb_alpha)

            uploadTexture(!pWidth, !pHeight, pixels, pImage, pImageMemory)

            STBImage.stbi_image_free(pixels)
        }
    }

    /**
     * Creates an image object and uploads the given pixels to it
     */
    private fun MemoryStack.uploadTexture(width: Int, height: Int, pixels: ByteBuffer?, pImage: LongBuffer, pImageMemory: LongBuffer) {
        // create staging buffer
        val imageSize = width * height * 4
        val pBuffer = mallocLong(1)
        val pMemory = mallocLong(1)
        createBuffer(
            imageSize.toLong(),
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_COHERENT_BIT or VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
            pBuffer,
            pMemory
        )

        val ppData = mallocPointer(1)
        vkMapMemory(logicalDevice, !pMemory, 0, imageSize.toLong(), 0, ppData)
        memCopy(memAddress(pixels), !ppData, imageSize.toLong())
        vkUnmapMemory(logicalDevice, !pMemory)

        createImage(
            width,
            height,
            VK_FORMAT_R8G8B8A8_SRGB,
            VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT,
            pImage,
            pImageMemory
        )
        val textureImage = !pImage

        transitionImageLayout(
            textureImage,
            VK_FORMAT_R8G8B8A8_SRGB,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
        )
        copyBufferToImage(!pBuffer, textureImage, width, height)
        transitionImageLayout(
            textureImage,
            VK_FORMAT_R8G8B8A8_SRGB,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
        )

        vkDestroyBuffer(logicalDevice, !pBuffer, Allocator)
        vkFreeMemory(logicalDevice, !pMemory, Allocator)
    }

    private fun copyBufferToImage(buffer: VkBuffer, image: VkImage, width: Int, height: Int) {
        execCommandBuffer { commandBuffer, stack ->
            val region = VkBufferImageCopy.callocStack(1, stack)
            region.bufferOffset(0)
            region.bufferRowLength(0)
            region.bufferImageHeight(0)

            region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            region.imageSubresource().mipLevel(0)
            region.imageSubresource().baseArrayLayer(0)
            region.imageSubresource().layerCount(1)

            region.imageOffset().set(0,0,0)
            region.imageExtent().set(width, height, 1)

            vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
        }
    }

    private fun transitionImageLayout(image: VkImage, format: VkFormat, oldLayout: VkImageLayout, newLayout: VkImageLayout) {
        execCommandBuffer { commandBuffer, stack ->
            val barrier = VkImageMemoryBarrier.callocStack(1, stack)
            barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            barrier.oldLayout(oldLayout)
            barrier.newLayout(newLayout)
            barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)

            barrier.image(image)

            if(newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)

                if(hasStencilComponent(format)) {
                    barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT or VK_IMAGE_ASPECT_STENCIL_BIT)
                }
            } else {
                barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            }
            barrier.subresourceRange().baseMipLevel(0)
            barrier.subresourceRange().levelCount(1)
            barrier.subresourceRange().baseArrayLayer(0)
            barrier.subresourceRange().layerCount(1)

            val sourceStage: VkPipelineStageFlags
            val destinationStage: VkPipelineStageFlags

            if(oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(0)
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)

                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            } else if(oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            } else if(oldLayout == VK_IMAGE_LAYOUT_UNDEFINED  && newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.srcAccessMask(0)
                barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT)

                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            } else {
                error("Unsupported layout transition")
            }

            vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0, null, null, barrier)
        }
    }

    private fun execCommandBuffer(actions: (VkCommandBuffer, MemoryStack) -> Unit) {
        val commandBuffer = beginSingleUseCommandBuffer()
        useStack {
            actions(commandBuffer, this)
        }
        endAndExecSingleUseCommandBuffer(commandBuffer)
    }

    private fun createImage(width: Int, height: Int, imageFormat: VkFormat, usage: VkImageUsageFlags, pImage: LongBuffer, pMemory: LongBuffer) {
        useStack {
            // create image
            val imageInfo = VkImageCreateInfo.callocStack(this)
            imageInfo.sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            imageInfo.imageType(VK_IMAGE_TYPE_2D)
            imageInfo.extent().width(width)
            imageInfo.extent().height(height)
            imageInfo.extent().depth(1)
            imageInfo.mipLevels(1)
            imageInfo.arrayLayers(1)

            imageInfo.format(imageFormat)

            imageInfo.tiling(VK_IMAGE_TILING_OPTIMAL)

            imageInfo.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)

            imageInfo.usage(usage)

            imageInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            imageInfo.samples(1) // TODO: change for multisampling

            if(vkCreateImage(logicalDevice, imageInfo, Allocator, pImage) != VK_SUCCESS) {
                error("Failed to create image")
            }

            // allocate image memory
            val memRequirements = VkMemoryRequirements.callocStack(this)
            vkGetImageMemoryRequirements(logicalDevice, !pImage, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.callocStack(this)
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            allocInfo.allocationSize(memRequirements.size())
            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))

            if(vkAllocateMemory(logicalDevice, allocInfo, Allocator, pMemory) != VK_SUCCESS) {
                error("Failed to allocate image memory")
            }

            vkBindImageMemory(logicalDevice, !pImage, !pMemory, 0)
        }
    }

    /**
     * Stages a transfer to GPU memory with the given usage and data
     *
     * @param usage The type of buffer to create
     * @param dataBuffer the data to fill the buffer with
     * @param bufferSize the size of the data to upload, in bytes
     */
    fun uploadBuffer(usage: VkBufferUsageFlags, dataBuffer: ByteBuffer, bufferSize: VkDeviceSize): VkBuffer {
        return useStack {
            val pBuffer = mallocLong(1)
            val pMemory = mallocLong(1)
            createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, pBuffer, pMemory)
            val stagingBuffer = !pBuffer
            val stagingBufferMemory = !pMemory

            val ppData = mallocPointer(1)
            vkMapMemory(logicalDevice, stagingBufferMemory, 0, bufferSize, 0, ppData).checkVKErrors()
            val data = ppData.get(0)
            val prevPos = dataBuffer.position()
            dataBuffer.position(0)
            memCopy(memAddress(dataBuffer), data, bufferSize)
            dataBuffer.position(prevPos)
            vkUnmapMemory(logicalDevice, stagingBufferMemory)

            createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT or usage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, pBuffer, pMemory)
            val result = !pBuffer

            copyBuffer(stagingBuffer, result, bufferSize)

            vkDestroyBuffer(logicalDevice, stagingBuffer, Allocator)
            vkFreeMemory(logicalDevice, stagingBufferMemory, Allocator)

            result
        }
    }

    private fun copyBuffer(srcBuffer: VkBuffer, dstBuffer: VkBuffer, size: VkDeviceSize) {
        useStack {
            val commandBuffer = beginSingleUseCommandBuffer()

            val copyRegion = VkBufferCopy.callocStack(1, this)
            copyRegion.srcOffset(0)
            copyRegion.dstOffset(0)
            copyRegion.size(size)
            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion)

            endAndExecSingleUseCommandBuffer(commandBuffer)
        }
    }

    private fun endAndExecSingleUseCommandBuffer(commandBuffer: VkCommandBuffer) {
        vkEndCommandBuffer(commandBuffer).checkVKErrors()

        // execute command buffer
        submitCommandBuffer(graphicsQueue, commandBuffer)
        vkQueueWaitIdle(graphicsQueue).checkVKErrors()
        vkFreeCommandBuffers(logicalDevice, commandPool, commandBuffer)
    }

    private fun beginSingleUseCommandBuffer(): VkCommandBuffer {
        return useStack {
            // allocate command buffer to perform copy
            val allocInfo = VkCommandBufferAllocateInfo.callocStack()
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            allocInfo.commandPool(commandPool) // TODO: maybe use a separate pool for temp buffers
            allocInfo.commandBufferCount(1)

            val pBuffer = mallocPointer(1)
            vkAllocateCommandBuffers(logicalDevice, allocInfo, pBuffer).checkVKErrors()
            val commandBuffer = VkCommandBuffer(!pBuffer, logicalDevice)

            // record command buffer
            val beginInfo = VkCommandBufferBeginInfo.callocStack(this)
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            vkBeginCommandBuffer(commandBuffer, beginInfo).checkVKErrors()

            commandBuffer
        }
    }

    private fun submitCommandBuffer(queue: VkQueue, vararg commandBuffers: VkCommandBuffer) {
        useStack {
            val pBuffer = mallocPointer(commandBuffers.size)
            commandBuffers.forEach {
                pBuffer.put(it)
            }
            pBuffer.rewind()
            val submitInfo = VkSubmitInfo.callocStack(this)
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            submitInfo.pCommandBuffers(pBuffer)

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE).checkVKErrors()
        }
    }

    private fun createBuffer(size: VkDeviceSize, usage: VkBufferUsageFlags, properties: VkMemoryPropertyFlags, buffer: LongBuffer, bufferMemory: LongBuffer) {
        useStack {
            val bufferInfo = VkBufferCreateInfo.callocStack(this)
            bufferInfo.sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            bufferInfo.size(size)
            bufferInfo.usage(usage)
            bufferInfo.sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            if(vkCreateBuffer(logicalDevice, bufferInfo, Allocator, buffer) != VK_SUCCESS) {
                error("Failed to create buffer")
            }

            val memRequirements = VkMemoryRequirements.callocStack(this)
            vkGetBufferMemoryRequirements(logicalDevice, !buffer, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.callocStack(this)
            allocInfo.sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            allocInfo.allocationSize(memRequirements.size())
            allocInfo.memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties))

            if(vkAllocateMemory(logicalDevice, allocInfo, Allocator, bufferMemory) != VK_SUCCESS) {
                error("Failed to allocate buffer memory")
            }

            vkBindBufferMemory(logicalDevice, !buffer, !bufferMemory, 0)
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
            recordCommandBuffers()
        }
    }

    fun requestCommandBufferRecreation() {
        nextFrameLoop {
            useStack {
                // wait for all in flight buffers to be rendered before resetting command buffers
                val fences = mallocLong(inFlightFences.size)
                for (i in inFlightFences.indices) {
                    fences.put(i, inFlightFences[i])
                }
                vkWaitForFences(logicalDevice, fences, true, Long.MAX_VALUE)

                destroyCommandBuffers()
                createCommandBuffers()
            }
        }
    }

    private fun recordCommandBuffers() {
        synchronized(commandBuffers) {
            useStack {
                commandBuffers.forEachIndexed { index, commandBuffer ->
                    val beginInfo = VkCommandBufferBeginInfo.callocStack(this)
                    beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                    beginInfo.pInheritanceInfo(null)

                    vkBeginCommandBuffer(commandBuffer, beginInfo).checkVKErrors()

                    val clearValues = VkClearValue.callocStack(7, this)
                    // one per attachment
                    clearValues.get(0).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(1).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(2).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(3).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(4).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(5).color(VkClearColorValue.callocStack(this).float32(floats(0.5f, 0.5f, 0.5f, 1f)))
                    clearValues.get(6).depthStencil(VkClearDepthStencilValue.callocStack(this).depth(1.0f).stencil(0))

                    val renderPassInfo = VkRenderPassBeginInfo.callocStack(this)
                    renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    renderPassInfo.renderPass(renderPass)
                    renderPassInfo.framebuffer(swapchainFramebuffers[index])

                    renderPassInfo.renderArea().offset(VkOffset2D.callocStack(this).set(0, 0))
                    renderPassInfo.renderArea().extent(swapchainExtent)
                    renderPassInfo.pClearValues(clearValues)

                    // GBuffer Pass
                    vkCmdBeginRenderPass(
                        commandBuffer,
                        renderPassInfo,
                        VK_SUBPASS_CONTENTS_INLINE
                    ) // VK_SUBPASS_CONTENTS_INLINE -> primary buffer only


                    scene?.let {
                        // TODO: parallelize?
                        for(group in renderGroups) {
                            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, group.pipeline.handle)
                            bindTexture(commandBuffer, WhiteTexture, group.pipeline.layout)

                            it.recordCommandBuffer(group, commandBuffer, index)
                        }
                    }

                    // Lighting pass
                    vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
                    simpleQuadPass(commandBuffer, index, lightingPipeline, lightingShaderDescriptor)

                    // SSAO pass
                    vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
                    ssaoBufferObject.update(logicalDevice, this, index)
                    simpleQuadPass(commandBuffer, index, ssaoPipeline, ssaoShaderDescriptor)

                    // Resolve pass
                    vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
                    simpleQuadPass(commandBuffer, index, renderToScreenPipeline, renderToScreenShaderDescriptor)

                    vkCmdEndRenderPass(commandBuffer)
                    vkEndCommandBuffer(commandBuffer).checkVKErrors()
                }
            }
        }
    }

    private fun simpleQuadPass(commandBuffer: VkCommandBuffer, index: Int, pipeline: GraphicsPipeline, set: DescriptorSet) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle)

        useStack {
            val pSets = mallocLong(1)
            pSets.put(0, set[index])
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.layout,
                0,
                pSets,
                null
            )

            // TODO: move to secondary buffer
            screenQuad.directRecord(this, commandBuffer)
        }
    }

    /**
     * Tells Vulkan to start using the given descriptor sets from now on.
     * Set order is important
     */
    fun useDescriptorSets(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, uboID: Int, vararg sets: DescriptorSet) {
        useStack {
            val pSets = mallocLong(sets.size)
            for(i in sets.indices) {
                pSets.put(i, sets[i][commandBufferIndex])
            }
            val offsets = mallocInt(1)
            offsets.put(0, (uboID*UniformBufferObject.SizeOf).toInt())
            VK10.vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                gBufferPipeline.layout,
                0,
                pSets,
                offsets
            )
        }
    }

    private fun createSyncingMechanisms() {
        useStack {
            val semaphoreInfo = VkSemaphoreCreateInfo.callocStack(this)
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            val fenceInfo = VkFenceCreateInfo.callocStack(this)
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT)

            val availabilitySemaphores = mutableListOf<VkSemaphore>()
            val workDoneSemaphores = mutableListOf<VkSemaphore>()
            val fences = mutableListOf<VkFence>()
            for(i in 0 until maxFramesInFlight) {
                val pSync = mallocLong(1)
                vkCreateSemaphore(logicalDevice, semaphoreInfo, Allocator, pSync).checkVKErrors()
                availabilitySemaphores += !pSync
                vkCreateSemaphore(logicalDevice, semaphoreInfo, Allocator, pSync).checkVKErrors()
                workDoneSemaphores += !pSync

                vkCreateFence(logicalDevice, fenceInfo, Allocator, pSync).checkVKErrors()
                fences += !pSync
            }

            imageAvailableSemaphores = availabilitySemaphores
            renderFinishedSemaphores = workDoneSemaphores
            inFlightFences = fences
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

                // TODO: get all queues available in order to do multithreaded rendering
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
            if(!features.samplerAnisotropy()) return@useStack false

            // requires indexing
            val indexingFeatures = VkPhysicalDeviceDescriptorIndexingFeaturesEXT.callocStack(this)
            indexingFeatures.sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT)

            val features2 = VkPhysicalDeviceFeatures2.callocStack(this)
            features2.sType(VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
            features2.pNext(indexingFeatures.address())
            VK11.vkGetPhysicalDeviceFeatures2(device, features2)

            if(!indexingFeatures.shaderSampledImageArrayNonUniformIndexing() || !indexingFeatures.descriptorBindingPartiallyBound() || !indexingFeatures.runtimeDescriptorArray()) return@useStack false

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
        useStack {
            val pWidth = mallocInt(1)
            val pHeight = mallocInt(1)
            glfwGetFramebufferSize(windowPointer, pWidth, pHeight)
            val fbWidth = !pWidth
            val fbHeight = !pHeight
            actualExtent.width(fbWidth.coerceIn(capabilities.minImageExtent().width() .. capabilities.maxImageExtent().width()))
            actualExtent.height(fbHeight.coerceIn(capabilities.minImageExtent().height() .. capabilities.maxImageExtent().height()))
        }
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
        }

        vkDeviceWaitIdle(logicalDevice)
    }

    private fun performLoopActions() {
        synchronized(frameLoopActions) {
            frameLoopActions.forEach {
                it()
            }
            frameLoopActions.clear()
        }
    }

    private fun performActions() {
        synchronized(frameActions) {
            frameActions.forEach {
                it()
            }
            frameActions.clear()
        }
    }

    private fun drawFrame() {
        performActions()
        if(currentFrame == 0) {
            performLoopActions()
        }
        useStack {
            val fences = longs(inFlightFences[currentFrame])
            vkWaitForFences(logicalDevice, fences, true, Long.MAX_VALUE)
            vkResetFences(logicalDevice, fences)
            val pImageIndex = mallocInt(1)
            val result = vkAcquireNextImageKHR(logicalDevice, swapchain, Long.MAX_VALUE, imageAvailableSemaphores[currentFrame], VK_NULL_HANDLE, pImageIndex)
            if(result == VK_ERROR_OUT_OF_DATE_KHR) { // swapchain is no longer adequate, update
                recreateSwapchain()
                return@useStack
            }

            updateUniformBuffer(!pImageIndex)

            graphicsQueueSubmit(!pImageIndex, currentFrame)

            present(!pImageIndex, currentFrame)
        }

        currentFrame = (currentFrame + 1) % maxFramesInFlight
    }

    private fun updateUniformBuffer(frameIndex: Int) {
        var forward = 0f
        var strafe = 0f
        if (glfwGetKey(windowPointer, GLFW_KEY_W) == GLFW_PRESS) {
            forward -= 1f
        }
        if (glfwGetKey(windowPointer, GLFW_KEY_S) == GLFW_PRESS) {
            forward += 1f
        }
        if (glfwGetKey(windowPointer, GLFW_KEY_A) == GLFW_PRESS) {
            strafe += 1f
        }
        if (glfwGetKey(windowPointer, GLFW_KEY_D) == GLFW_PRESS) {
            strafe -= 1f
        }

        val direction by lazy { Vector3f() }
        if (strafe != 0f || forward != 0f) {
            direction.set(-strafe, -forward, 0f)
            direction/*.normalize()*/.rotateAxis(defaultCamera.yaw, 0f, 0f, 1f)
            val speed = 0.01f
            direction.mul(speed)
            defaultCamera.position.add(direction)
        }

        if(scene != null) {
            scene!!.preRenderFrame(frameIndex)
        }
    }

    private fun MemoryStack.graphicsQueueSubmit(imageIndex: Int, currentFrame: Int) {
        val submitInfo = VkSubmitInfo.callocStack(this)
        submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)

        val waitSemaphores = mallocLong(1)
        waitSemaphores.put(imageAvailableSemaphores[currentFrame]).flip()

        val waitStages = mallocInt(1)
        waitStages.put(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT).flip()

        submitInfo.pWaitSemaphores(waitSemaphores)
        submitInfo.waitSemaphoreCount(1)
        submitInfo.pWaitDstStageMask(waitStages)

        val signalSemaphores = mallocLong(1)
        signalSemaphores.put(renderFinishedSemaphores[currentFrame]).flip()
        submitInfo.pSignalSemaphores(signalSemaphores)

        val buffers = mallocPointer(1)
        buffers.put(commandBuffers[currentFrame].address()).flip()
        submitInfo.pCommandBuffers(buffers)

        vkResetFences(logicalDevice, inFlightFences[currentFrame])
        vkQueueSubmit(graphicsQueue, submitInfo, inFlightFences[currentFrame]).checkVKErrors()
    }

    private fun MemoryStack.present(imageIndex: Int, currentFrame: Int) {
        val presentInfo = VkPresentInfoKHR.callocStack(this)
        presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
        val waitSemaphores = longs(renderFinishedSemaphores[currentFrame])
        presentInfo.pWaitSemaphores(waitSemaphores)

        presentInfo.swapchainCount(1)

        val pSwapchain = longs(swapchain)
        presentInfo.pSwapchains(pSwapchain)

        val imageIndices = ints(imageIndex)
        presentInfo.pImageIndices(imageIndices)

        presentInfo.pResults(null)

        val result = vkQueuePresentKHR(presentQueue, presentInfo)
        if(result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR || framebufferResized) {
            recreateSwapchain()
        }
    }

    private fun recreateSwapchain() {
        // TODO: Handle window minimization
        vkDeviceWaitIdle(logicalDevice)

        cleanupSwapchain()

        framebufferResized = false

        gColorImages.clear()
        createSwapchain()
        createRenderImageViews()
        createCamera()
        renderPass = createRenderPass()
        gBufferPipeline = createGraphicsPipeline(gBufferPipelineBuilder())
        renderToScreenPipeline = createGraphicsPipeline(renderToScreenPipelineBuilder())
        lightingPipeline = createGraphicsPipeline(lightingPipelineBuilder())
        ssaoPipeline = createGraphicsPipeline(ssaoPipelineBuilder())
        createDepthResources()
        createFramebuffers()
        descriptorPool = createDescriptorPool()
        createCommandBuffers()
    }

    private fun cleanupSwapchain() {
        vkDestroyImageView(logicalDevice, depthImageView, Allocator)
        vkFreeMemory(logicalDevice, depthImageMemory, Allocator)
        vkDestroyImage(logicalDevice, depthImage, Allocator)

        swapchainFramebuffers.forEach {
            vkDestroyFramebuffer(logicalDevice, it, Allocator)
        }

        destroyCommandBuffers()

        vkDestroyPipeline(logicalDevice, graphicsPipeline, Allocator)
        vkDestroyPipelineLayout(logicalDevice, gBufferPipeline.layout, Allocator)
        vkDestroyRenderPass(logicalDevice, renderPass, Allocator)

        for(view in swapchainImageViews) {
            vkDestroyImageView(logicalDevice, view, Allocator)
        }
        vkDestroySwapchainKHR(logicalDevice, swapchain, Allocator)
    }

    private fun destroyCommandBuffers() {
        useStack {
            vkResetCommandPool(logicalDevice, commandPool, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT)
        }
    }

    private fun findMemoryType(typeFilter: Int, properties: VkMemoryPropertyFlags): Int {
        return useStack {
            val memoryProperties = VkPhysicalDeviceMemoryProperties.callocStack(this)
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties)

            for(i in 0 until memoryProperties.memoryTypeCount()) {
                if(typeFilter and (1 shl i) != 0 && (memoryProperties.memoryTypes()[i].propertyFlags() and properties) == properties) {
                    return@useStack i
                }
            }

            throw RuntimeException("Failed to find suitable memory type")
        }
    }

    /**
     * Cleanup native objects
     */
    fun cleanup() {
        cleanupSwapchain()

        vkDestroySampler(logicalDevice, linearSampler, Allocator)
        for(model in activeModels.values) {
            model.free(logicalDevice)
        }
        activeModels.clear()

        for(texture in activeTextures.values) {
            texture.free(logicalDevice)
        }
        activeTextures.clear()


        for(i in 0 until maxFramesInFlight) {
            vkDestroySemaphore(logicalDevice, imageAvailableSemaphores[i], Allocator)
            vkDestroySemaphore(logicalDevice, renderFinishedSemaphores[i], Allocator)
            vkDestroyFence(logicalDevice, inFlightFences[i], Allocator)
        }

        vkDestroyDevice(logicalDevice, Allocator)
        if(enableValidationLayers) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(vulkan, debugger, Allocator)
        }
        vkDestroySurfaceKHR(vulkan, surface, Allocator)
        vkDestroyInstance(vulkan, Allocator)
        glfwDestroyWindow(windowPointer)
        glfwTerminate()
    }

    private fun <T> useStack(action: MemoryStack.() -> T) = memoryStack.push().use(action)

    /**
     * Tells the engine which scene to render
     */
    fun setScene(scene: Scene?) {
        this.scene = scene
    }

    /**
     * Perform the given action at the beginning of the next frame loop (every 3 frames).
     * Synchronisation mechanism
     */
    fun nextFrameLoop(action: () -> Unit) {
        synchronized(frameLoopActions) {
            frameLoopActions += action
        }
    }

    /**
     * Perform the given action at the beginning of the next frame.
     * Synchronisation mechanism
     */
    fun nextFrame(action: () -> Unit) {
        synchronized(frameActions) {
            frameActions += action
        }
    }

    /**
     * Is the calling thread the rendering thread?
     */
    fun onRenderThread() = Thread.currentThread() == renderThread

    /**
     * Intended to be used with the 'by' operator of Kotlin to defer asset loading to the render thread
     */
    fun <T: Any> load(replacement: () -> T, function: () -> T): AsyncGraphicalAsset<T> {
        return if(onRenderThread()) {
            AsyncGraphicalAsset(function())
        } else {
            val result = AsyncGraphicalAsset(replacement, function)
            nextFrame {
                result.load()
            }
            result
        }
    }

    fun changeThread() {
        renderThread = Thread.currentThread()
        glfwMakeContextCurrent(windowPointer)
    }

    fun waitForInit() {
        initSemaphore.acquire()
    }

    private fun nextTextureID(): Int {
        if(textureID >= MaxTextures) {
            error("No more texture space")
        }
        val id = textureID
        textureID++
        return id
    }

    /**
     * Load a texture into Vulkan.
     * Caching is applied
     */
    fun createTexture(path: String): Texture {
        if(path in activeTextures) {
            return activeTextures[path]!!
        }
        return useStack {
            val pImage = mallocLong(1)
            val pImageMemory = mallocLong(1)
            createTextureImage(path, pImage, pImageMemory)
            val imageView = createImageView(!pImage, VK_FORMAT_R8G8B8A8_SRGB)
            // TODO: custom sampler
            val texture = Texture(nextTextureID(), !pImage, imageView, path)

            imageViews[texture.textureID] = imageView
            println("created texture with ID ${texture.textureID}")

            updateDescriptorSet(gBufferShaderDescriptor, DescriptorSetUpdateBuilder().textureSampling(texture))
            activeTextures[path] = texture

            texture
        }
    }

    /**
     * Loads a model from the given path.
     * Caching is applied, will return a copy of the original if a cache hit occured
     */
    fun createModel(path: String): Model {
        if(path in activeModels) {
            return Model(activeModels[path]!!) // return a copy
        }
        val model = Model(path)
        activeModels[path] = model
        return model
    }

    private fun nextUBOID(): Int {
        if(uboID >= MaxObjects) {
            error("out of UBO space")
        }
        return uboID++
    }

    fun createUBO(): UniformBufferObject {
        return UniformBufferObject(nextUBOID())
    }

    private var currentTexture: Texture? = null

    /**
     * Only for use when recording to a command buffer
     */
    internal fun bindTexture(commandBuffer: VkCommandBuffer, tex: Texture, pipelineLayout: VkPipeline = gBufferPipeline.layout) {
        if(tex != currentTexture) { // send the push constant change only if texture actually changed
            println("push constant ${tex.textureID}")
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, intArrayOf(tex.textureID))
            currentTexture = tex
        }
    }
}