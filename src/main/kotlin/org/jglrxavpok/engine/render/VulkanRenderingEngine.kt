package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.GameInformation
import org.jglrxavpok.engine.Version
import org.jglrxavpok.engine.*
import org.jglrxavpok.engine.math.LinearEase
import org.jglrxavpok.engine.render.VulkanDebug.name
import org.jglrxavpok.engine.render.lighting.*
import org.jglrxavpok.engine.render.model.Mesh
import org.jglrxavpok.engine.render.model.Model
import org.jglrxavpok.engine.render.model.TextureUsage
import org.joml.Vector3f
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.Configuration
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
import org.lwjgl.vulkan.EXTDebugReport.*
import org.lwjgl.vulkan.EXTDescriptorIndexing.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT
import org.lwjgl.vulkan.VK11.*
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
    val MaxLights = LightingConfiguration(pointLightCount = 32, directionalLightCount = 32, spotLightCount = 32)
    val MaxShadowMaps = 16

    val EngineName = "jglrEngine"
    val Version = Version(0, 0, 1, "indev")
    val RenderWidth: Int = 1920
    val RenderHeight: Int = 1080
    val Allocator: VkAllocationCallbacks? = null

    private var enableValidationLayers: Boolean = true
    var windowPointer: Long = -1L
        private set
    private var framebufferResized = false
    private val maxFramesInFlight = 3

    private val validationLayers = listOf("VK_LAYER_LUNARG_standard_validation", "VK_LAYER_LUNARG_monitor")
    private val deviceExtensions = listOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
    private val renderDocExtensions = listOf(EXTDebugMarker.VK_EXT_DEBUG_MARKER_EXTENSION_NAME)
    private val memoryStack = MemoryStack.create(512 * 1024*1024) // 512 MB

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
    private var usualRenderPass: VkRenderPass = VK_NULL_HANDLE
    private var shadowMapRenderPass: VkRenderPass = VK_NULL_HANDLE
    private var primaryCommandPool: VkCommandPool = VK_NULL_HANDLE
    private var secondaryCommandPool: VkCommandPool = VK_NULL_HANDLE
    private var temporaryCommandPool: VkCommandPool = VK_NULL_HANDLE
    private var descriptorPool: VkDescriptorPool = VK_NULL_HANDLE

    internal var linearSampler: VkSampler = VK_NULL_HANDLE
    internal var nearestSampler: VkSampler = VK_NULL_HANDLE
    internal var shadowSampler: VkSampler = VK_NULL_HANDLE

    private var depthImage: VkImage = VK_NULL_HANDLE
    private var depthImageMemory: VkDeviceMemory = VK_NULL_HANDLE
    private var depthImageView: VkImageView = VK_NULL_HANDLE

    private lateinit var imageAvailableSemaphores: List<VkSemaphore>
    private lateinit var renderFinishedSemaphores: List<VkSemaphore>
    private lateinit var inFlightFences: List<VkFence>
    private lateinit var swapchainExtent: VkExtent2D
    private var shadowMapExtent: VkExtent2D = VkExtent2D.create().set(2048,2048)
    private lateinit var swapchainImages: List<VkImage>
    private lateinit var swapchainImageViews: List<VkImageView>
    private lateinit var swapchainFramebuffers: List<VkFramebuffer>
    private lateinit var shadowMapFramebuffers: List<VkFramebuffer>
    private lateinit var primaryCommandBuffers: List<VkCommandBuffer>
    private lateinit var secondaryCommandBuffers: List<VkCommandBuffer>

    private var currentFrame = 0

    // End of Vulkan objects
    lateinit var defaultCamera: Camera
    lateinit var lightCamera: Camera

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
    lateinit var BlackSpecularTexture: Texture
    private lateinit var noiseTexture: Texture

    lateinit var gBufferShaderDescriptor: DescriptorSet
    lateinit var lightingShaderDescriptorSet0: DescriptorSet
    lateinit var lightingShaderDescriptorSet1: DescriptorSet
    lateinit var ssaoShaderDescriptor: DescriptorSet
    lateinit var renderToScreenShaderDescriptor: DescriptorSet
    lateinit var emptyDescriptor: DescriptorSet
    lateinit var shadowMappingMatrices: ShadowMappingMatrices
    private lateinit var ssaoMemories:  List<VkDeviceMemory>
    private lateinit var lightMemories:  List<VkDeviceMemory>
    private val imageViews = Array(TextureUsage.values().size) { mutableMapOf<Int, VkImageView>() }
    private var uboID = 0
    private val textureCounters = IntArray(TextureUsage.values().size)

    lateinit var gBufferPipeline: GraphicsPipeline
    lateinit var renderToScreenPipeline: GraphicsPipeline
    lateinit var lightingPipeline: GraphicsPipeline
    lateinit var ssaoPipeline: GraphicsPipeline
    lateinit var shadowMappingPipelines: List<GraphicsPipeline>
    lateinit var shadowMappingShaderDescriptorSets: List<DescriptorSet>
    lateinit var shadowMappingCameraObjects: List<CameraObject>

    private val gColorImages = mutableListOf<ImageInfo>()
    private val gPosImages = mutableListOf<ImageInfo>()
    private val gNormalImages = mutableListOf<ImageInfo>()
    private val gSpecularImages = mutableListOf<ImageInfo>()
    private val lightingOutImages = mutableListOf<ImageInfo>()
    private val ssaoOutImages = mutableListOf<ImageInfo>()
    private lateinit var screenQuad: Mesh
    private val ssaoBufferObject = SSAOBufferObject(SSAOKernelSize)
    private val lightBufferObject = LightBufferObject(MaxLights)
    private val renderBatches = RenderBatches()
    private lateinit var cameraObject: CameraObject

    /**
     * shadowMap[lightIndex][frameIndex]
     */
    private val shadowMaps = Array(MaxShadowMaps) {
        mutableListOf<ImageInfo>()
    }
    internal val shadowCastingLights: Array<Light> = Array(MaxShadowMaps) { SpotLight.None }

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
    fun init(gameInfo: GameInformation, game: Game, enableValidationLayers: Boolean = true, renderDocUsed: Boolean = false) {
        VulkanDebug.enabled = renderDocUsed
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
        createLogicalDevice(renderDocUsed)
        createSwapchain()
        createCamera()
        createRenderImageViews()
        shadowMapRenderPass = createShadowPass(getDepthFormat())
        usualRenderPass = createRenderPass()
        gBufferPipeline = createGraphicsPipeline(gBufferPipelineBuilder())
        renderToScreenPipeline = createGraphicsPipeline(renderToScreenPipelineBuilder())
        lightingPipeline = createGraphicsPipeline(lightingPipelineBuilder())
        ssaoPipeline = createGraphicsPipeline(ssaoPipelineBuilder())
        primaryCommandPool = createCommandPool()
        name(primaryCommandPool, "Primary command pool", VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_POOL_EXT)
        secondaryCommandPool = createCommandPool()
        name(secondaryCommandPool, "Secondary command pool", VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_POOL_EXT)
        temporaryCommandPool = createCommandPool()
        name(temporaryCommandPool, "Temporary command pool", VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_POOL_EXT)
        createDepthResources()
        createFramebuffers()
        createShadowMapFramebuffers()
        linearSampler = createTextureSampler(VK_FILTER_LINEAR)
        nearestSampler = createTextureSampler(VK_FILTER_NEAREST)
        shadowSampler = createTextureSampler(VK_FILTER_LINEAR, VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
        descriptorPool = createDescriptorPool()
        name(descriptorPool, "Descriptor pool", VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_POOL_EXT)

        // TODO: custom cameras
        val cameraInfo = prepareUniformBuffers(CameraObject.SizeOf, 1)
        val cameraObjectMemories = cameraInfo.second

        cameraObject = CameraObject(cameraObjectMemories)

        val shadowMappingMatrixInfo = prepareUniformBuffers(ShadowMappingMatrices.SizeOf(MaxShadowMaps), 1)
        shadowMappingMatrices = ShadowMappingMatrices(shadowMappingMatrixInfo.second, shadowMappingMatrixInfo.first)

        val ssaoInfo = prepareUniformBuffers(SSAOBufferObject.SizeOf(SSAOKernelSize), 1)
        val ssaoBuffers = ssaoInfo.first
        ssaoMemories = ssaoInfo.second

        val lightInfo = prepareUniformBuffers(LightBufferObject.SizeOf(MaxLights), 1)
        val lightBuffers = lightInfo.first
        lightMemories = lightInfo.second

        val rand = Random()
        for(i in 0 until SSAOKernelSize) {
            val x = rand.nextFloat() * 2f - 1f
            val y = rand.nextFloat() * 2f - 1f
            val z = rand.nextFloat()
            val sample = Vector3f(x,y,z)
            sample.normalize()
            sample.mul(rand.nextFloat())
            var scale = i.toFloat() / SSAOKernelSize.toFloat()
            scale = LinearEase(0.1f, 1.0f, scale * scale)
            sample.mul(scale)
            ssaoBufferObject.noiseSamples[i].set(sample)
        }

        useStack {
            for(i in swapchainImages.indices) {
                ssaoBufferObject.update(logicalDevice, this, i)
            }
        }

        gBufferShaderDescriptor = createDescriptorSetFromBuilder(
            gBufferPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .cameraBuffer(cameraInfo.first)
                .sampler(linearSampler)
        )
        val updateBuilder = DescriptorSetUpdateBuilder()
            .subpassSampler { index -> gColorImages[index].view }
            .subpassSampler { index -> gPosImages[index].view }
            .subpassSampler { index -> gNormalImages[index].view }
            .uniformBuffer(LightBufferObject.SizeOf(MaxLights), { index -> lightBuffers[index] }, false)
            .subpassSampler { index -> gSpecularImages[index].view }

        for(lightIndex in 0 until MaxShadowMaps) {
            updateBuilder.frameDependentCombinedImageSampler({ index -> shadowMaps[lightIndex][index].view }, shadowSampler, VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
        }
        lightingShaderDescriptorSet0 = createDescriptorSetFromBuilder(
            lightingPipeline.descriptorSetLayouts[0],
            updateBuilder
        )
        lightingShaderDescriptorSet1 = createDescriptorSetFromBuilder(
            lightingPipeline.descriptorSetLayouts[1],
            DescriptorSetUpdateBuilder().uniformBuffer(ShadowMappingMatrices.SizeOf(MaxShadowMaps), shadowMappingMatrices.buffers::get, true)
        )
        noiseTexture = createNoiseTexture(VkExtent2D.create().set(4, 4))
        ssaoShaderDescriptor = createDescriptorSetFromBuilder(
            ssaoPipeline.descriptorSetLayouts[0],
            DescriptorSetUpdateBuilder()
                .frameDependentCombinedImageSampler({ index -> gPosImages[index].view }, linearSampler)
                .subpassSampler { index -> gNormalImages[index].view } //.frameDependentCombinedImageSampler({ index -> gNormalImages[index].view }, nearestSampler)
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
        BlackSpecularTexture = createTexture("/textures/black.png", TextureUsage.Specular)

        screenQuad = Mesh(listOf(
            Vertex(Vector3f(-1f, -1f, 0f)),
            Vertex(Vector3f(-1f, 1f, 0f)),
            Vertex(Vector3f(1f, 1f, 0f)),
            Vertex(Vector3f(1f, -1f, 0f))
            ),
            listOf(0u, 1u, 2u, 2u, 3u, 0u), vertexFormat = VertexFormat.Companion.ScreenPositionOnly)

        setupShadowMapping()
        createSecondaryCommandBuffers()
        createPrimaryCommandBuffers()
        recordBuffers()
        createSyncingMechanisms()

        initSemaphore.release()
    }

    fun setupShadowMapping() {
        shadowMappingPipelines = (0 until MaxShadowMaps).map { createGraphicsPipeline(shadowMapPipelineBuilder(it, shadowCastingLights[it].type)) }

        val descriptors = mutableListOf<DescriptorSet>()
        val cameras = mutableListOf<CameraObject>()
        for(i in 0 until MaxShadowMaps) {
            val pipeline = shadowMappingPipelines[i]

            val cameraInfo = prepareUniformBuffers(CameraObject.SizeOf, 1)
            val lightCamera = CameraObject(cameraInfo.second)
            val descriptorSet = createDescriptorSetFromBuilder(
                pipeline.descriptorSetLayouts[0],
                DescriptorSetUpdateBuilder()
                    .cameraBuffer(cameraInfo.first)
            )

            descriptors += descriptorSet
            cameras += lightCamera
        }
        shadowMappingCameraObjects = cameras
        shadowMappingShaderDescriptorSets = descriptors
    }

    fun useShadowCasting(lights: Collection<Light>) {
        if(lights.sumBy { it.type.shadowMapCount } >= MaxShadowMaps) {
            error("Too many shadow casting lights, max is $MaxShadowMaps")
        }
        var cursor = 0
        for(light in lights) {
            for(i in 0 until light.type.shadowMapCount) {
                shadowCastingLights[cursor] = light
                cursor++
            }
        }
        updateShadowMapIndices()
        nextFrame {
            setupShadowMapping()
        }
    }

    private fun createNoiseTexture(size: VkExtent2D): Texture {
        return useStack {
            val pImage = mallocLong(1)
            val pImageMemory = mallocLong(1)
            val pixelBuffer = malloc(size.width()*size.height()*4*4)
            val pixels = pixelBuffer.asFloatBuffer()

            val rand = Random()
            for(i in 0 until size.width()*size.height()) {
                val x = rand.nextFloat() * 2f - 1f
                val y = rand.nextFloat() * 2f - 1f
                pixels.put(x).put(y).put(0f).put(0f)
            }
            pixels.flip()

            uploadTexture(size.width(), size.height(), pixelBuffer, pImage, pImageMemory)
            val imageView = createImageView(!pImage, VK_FORMAT_R8G8B8A8_SRGB)

            val texture = Texture(NoiseTextureArrayIndex, !pImage, imageView, "generated:noise(${size.width()}x${size.height()})")

            imageViews[TextureUsage.Diffuse.ordinal][texture.textureID] = imageView

            texture
        }
    }

    private fun ssaoPipelineBuilder() = GraphicsPipelineBuilder(1, usualRenderPass, swapchainExtent)
        .vertexShaderModule("/shaders/screenQuad.vertc").fragmentShaderModule("/shaders/gSSAO.fragc")
        .descriptorSetLayoutBindings(
            DescriptorSetLayoutBindings()
                .combinedImageSampler() // gPos
                .subpassSampler() // gNormal
                .combinedImageSampler() // noise
                .uniformBuffer(false, stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT) // projection matrix and noise sample vectors
        )
        .depthTest(false)
        .depthWrite(false)
        .subpass(SSAOSubpass)
        .useStencil(false)
        .vertexFormat(VertexFormat.Companion.ScreenPositionOnly)

    private fun lightingPipelineBuilder(): GraphicsPipelineBuilder {
        val bindings = DescriptorSetLayoutBindings()
            .subpassSampler() // gColor
            .subpassSampler() // gPos
            .subpassSampler() // gNormal
            .uniformBuffer(false, stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT) // lights
            .subpassSampler() // gSpecular

        for(i in 0 until MaxShadowMaps) {
            bindings.combinedImageSampler() // shadow map #i
        }

        val shadowMappingBindings =
            DescriptorSetLayoutBindings()
                .uniformBuffer(true, VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
        val builder = GraphicsPipelineBuilder(1, usualRenderPass, swapchainExtent)
            .vertexShaderModule("/shaders/screenQuad.vertc").fragmentShaderModule("/shaders/gLighting.fragc")
            .descriptorSetLayoutBindings(
                bindings
            )
            .descriptorSetLayoutBindings(
                shadowMappingBindings
            )
            .depthTest(false)
            .depthWrite(false)
            .subpass(LightingSubpass)
            .useStencil(false)
            .descriptorSetCount(2)
            .vertexFormat(VertexFormat.Companion.ScreenPositionOnly)

        return builder
    }

    private fun renderToScreenPipelineBuilder() = GraphicsPipelineBuilder(1, usualRenderPass, swapchainExtent)
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

    private fun gBufferPipelineBuilder() = GraphicsPipelineBuilder(4, usualRenderPass, swapchainExtent)
        .descriptorSetLayoutBindings(DescriptorSetLayoutBindings()
            .uniformBuffer(true)
            .textures(MaxTextures) // diffuse
            .sampler()
            .textures(MaxTextures) // specular
        )
        .subpass(GBufferSubpass)
        .vertexShaderModule("/shaders/gBuffer.vertc")
        .fragmentShaderModule("/shaders/gBuffer.fragc")

    private fun shadowMapPipelineBuilder(lightIndex: Int, lightType: LightType) = GraphicsPipelineBuilder(1, shadowMapRenderPass, shadowMapExtent)
        .descriptorSetLayoutBindings(DescriptorSetLayoutBindings()
            .uniformBuffer(true) // camera information
        )
        .subpass(lightIndex)
        .vertexShaderModule("/shaders/shadowMapping/base.vertc")
        .fragmentShaderModule("/shaders/shadowMapping/null.fragc")

    fun getSSAOMemory(frameIndex: Int): VkDeviceMemory {
        return ssaoMemories[frameIndex]
    }

    fun getLightMemory(frameIndex: Int): VkDeviceMemory {
        return lightMemories[frameIndex]
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
        useStack {
            val stack = this
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

    private fun createLogicalDevice(renderDocUsed: Boolean) {
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
            val extensions = deviceExtensions + (if(renderDocUsed) { renderDocExtensions } else emptyList())
            createDeviceInfo.ppEnabledExtensionNames(createNamesBuffer(extensions, this))

            if(enableValidationLayers) {
                createDeviceInfo.ppEnabledLayerNames(createNamesBuffer(validationLayers, this))
            }

            val pDevice = mallocPointer(1)
            vkCreateDevice(physicalDevice, createDeviceInfo, Allocator, pDevice).checkVKErrors()
            logicalDevice = VkDevice(!pDevice, physicalDevice, createDeviceInfo)
            name(logicalDevice.address(), "Logical Device", EXTDebugReport.VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_EXT)
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

        lightCamera = Camera(shadowMapExtent.width(), shadowMapExtent.height())
    }

    private fun createRenderImageViews() {
        useStack {
            val imageViews = mutableListOf<VkImageView>()
            for((index, image) in swapchainImages.withIndex()) {
                val view = createImageView(image, swapchainFormat)
                name(view, "Render Image View #$index", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                imageViews.add(view)
            }
            swapchainImageViews = imageViews
        }
    }

    private fun createShadowPass(depthFormat: VkFormat): VkRenderPass {
        return useStack {
            val attachments = VkAttachmentDescription.callocStack(MaxShadowMaps, this)
            val subpasses = VkSubpassDescription.callocStack(MaxShadowMaps, this)
            val dependency = VkSubpassDependency.callocStack(MaxShadowMaps, this)

            for(i in 0 until MaxShadowMaps) {
                val attachment = attachments.get(i)

                for(frameIndex in swapchainImages.indices) {
                    val shadowMapInfo = createAttachmentImageView(depthFormat, shadowMapExtent.width(), shadowMapExtent.height(), VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_DEPTH_BIT)
                    shadowMaps[i].add(shadowMapInfo)
                }

                attachment.format(depthFormat)
                attachment.samples(VK_SAMPLE_COUNT_1_BIT)
                attachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                attachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                attachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                attachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                attachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                attachment.finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)

                val lightPass = subpasses.get(i)
                lightPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

                val depthAttachmentRef = VkAttachmentReference.callocStack(this)
                depthAttachmentRef.attachment(i)
                depthAttachmentRef.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                lightPass.pDepthStencilAttachment(depthAttachmentRef)
                lightPass.colorAttachmentCount(0)

                prepareDependency(dependency.get(i), VK_SUBPASS_EXTERNAL, i)
            }

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

    private fun createRenderPass(): VkRenderPass {
        return useStack {
            val attachments = VkAttachmentDescription.callocStack(8, this)
            val finalColorIndex = 0
            val gColorIndex = 1
            val gPosIndex = 2
            val gNormalIndex = 3
            val lightingOutIndex = 4
            val ssaoOutIndex = 5
            val depthIndex = 6
            val gSpecularIndex = 7
            // 0: final color
            // 1: gColor
            // 2: gPos (world pos)
            // 3: gNormal
            // 4: lightingOut
            // 5: ssaoOut
            // 6: depth
            // 7: specular
            prepareColorAttachment(attachments.get(finalColorIndex), swapchainFormat, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            prepareColorAttachment(attachments.get(gColorIndex), VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(gPosIndex), VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(gNormalIndex), VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(lightingOutIndex), VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            prepareColorAttachment(attachments.get(ssaoOutIndex), VK_FORMAT_B8G8R8A8_UNORM/*VK_FORMAT_R8_UNORM*/, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            prepareColorAttachment(attachments.get(gSpecularIndex), VK_FORMAT_B8G8R8A8_UNORM/*VK_FORMAT_R8_UNORM*/, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            for(i in swapchainImages.indices) {
                val gColorInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(gColorInfo.image, "gColor #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(gColorInfo.view, "View of gColor #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(gColorInfo.memory, "Memory of gColor #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                gColorImages += gColorInfo

                val gPosInfo = createAttachmentImageView(VK_FORMAT_R16G16B16A16_SFLOAT, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(gPosInfo.image, "gPos #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(gPosInfo.view, "View of gPos #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(gPosInfo.memory, "Memory of gPos #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                gPosImages += gPosInfo

                val gNormalInfo = createAttachmentImageView(VK_FORMAT_R16G16B16A16_SFLOAT, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(gNormalInfo.image, "gNormal #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(gNormalInfo.view, "View of gNormal #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(gNormalInfo.memory, "Memory of gNormal #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                gNormalImages += gNormalInfo

                val lightingOutInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(lightingOutInfo.image, "lightingOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(lightingOutInfo.view, "View of lightingOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(lightingOutInfo.memory, "Memory of lightingOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                lightingOutImages += lightingOutInfo

                val ssaoOutInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM/*VK_FORMAT_R8_UNORM*/, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(ssaoOutInfo.image, "ssaoOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(ssaoOutInfo.view, "View of ssaoOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(ssaoOutInfo.memory, "Memory of ssaoOut #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                ssaoOutImages += ssaoOutInfo

                val gSpecularInfo = createAttachmentImageView(VK_FORMAT_B8G8R8A8_UNORM/*VK_FORMAT_R8_UNORM*/, swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT, VK_IMAGE_ASPECT_COLOR_BIT)
                name(gSpecularInfo.image, "gSpecular #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT)
                name(gSpecularInfo.view, "View of gSpecular #$i", VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT)
                name(gSpecularInfo.memory, "Memory of gSpecular #$i", VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT)
                gSpecularImages += gSpecularInfo
            }

            val depthAttachment = attachments.get(depthIndex)
            depthAttachment.format(getDepthFormat())
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

            val gBuffer = VkAttachmentReference.callocStack(4, this)
            val gColorRenderTarget = gBuffer[0]
            gColorRenderTarget.attachment(gColorIndex)
            gColorRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gPosRenderTarget = gBuffer[1]
            gPosRenderTarget.attachment(gPosIndex)
            gPosRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gNormalRenderTarget = gBuffer[2]
            gNormalRenderTarget.attachment(gNormalIndex)
            gNormalRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val gSpecularRenderTarget = gBuffer[3]
            gSpecularRenderTarget.attachment(gSpecularIndex)
            gSpecularRenderTarget.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val depthAttachmentRef = VkAttachmentReference.callocStack(this)
            depthAttachmentRef.attachment(depthIndex)
            depthAttachmentRef.layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

            // TODO: Multiple subpass for post-processing
            val subpasses = VkSubpassDescription.callocStack(4, this)
            val gBufferPass = subpasses.get(GBufferSubpass)
            gBufferPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            gBufferPass.pColorAttachments(gBuffer)
            gBufferPass.colorAttachmentCount(4)
            gBufferPass.pDepthStencilAttachment(depthAttachmentRef)

            val gBufferInput = VkAttachmentReference.callocStack(4, this)
            val gColorInput = gBufferInput[0]
            gColorInput.attachment(gColorIndex)
            gColorInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val gPosInput = gBufferInput[1]
            gPosInput.attachment(gPosIndex)
            gPosInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val gNormalInput = gBufferInput[2]
            gNormalInput.attachment(gNormalIndex)
            gNormalInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val gSpecularInput = gBufferInput[3]
            gSpecularInput.attachment(gSpecularIndex)
            gSpecularInput.layout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val lightingPass = subpasses.get(LightingSubpass)
            lightingPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            val lightingOut = VkAttachmentReference.callocStack(1, this)
            lightingOut.attachment(lightingOutIndex)
            lightingOut.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            lightingPass.pInputAttachments(gBufferInput) // TODO: + shadow maps
            lightingPass.pColorAttachments(lightingOut)
            lightingPass.colorAttachmentCount(1)

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
            ssaoPass.colorAttachmentCount(1)


            val resolveInputs = VkAttachmentReference.callocStack(2, this)
            resolveInputs[0].attachment(lightingOutIndex)
            resolveInputs[0].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            resolveInputs[1].attachment(ssaoOutIndex)
            resolveInputs[1].layout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val renderToScreenPass = subpasses.get(ResolveSubpass)
            renderToScreenPass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)

            renderToScreenPass.pInputAttachments(resolveInputs)
            renderToScreenPass.pColorAttachments(screenBuffer)
            renderToScreenPass.colorAttachmentCount(1)


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
        dependency.srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT)

        dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
        dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT or VK_ACCESS_INPUT_ATTACHMENT_READ_BIT)

        dependency.dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
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

    private fun createShadowMapFramebuffers() {
        val framebuffers = mutableListOf<VkFramebuffer>()
        useStack {
            swapchainImageViews.forEachIndexed { frameIndex, it ->
                val attachments = mallocLong(MaxShadowMaps)
                for(lightIndex in 0 until MaxShadowMaps) {
                    attachments.put(shadowMaps[lightIndex][frameIndex].view) // shadow map
                }
                attachments.rewind()

                val framebufferInfo = VkFramebufferCreateInfo.callocStack(this)
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                framebufferInfo.width(shadowMapExtent.width())
                framebufferInfo.height(shadowMapExtent.height())
                framebufferInfo.layers(1)
                framebufferInfo.pAttachments(attachments)
                framebufferInfo.renderPass(shadowMapRenderPass)

                val pFramebuffer = mallocLong(1)
                vkCreateFramebuffer(logicalDevice, framebufferInfo, Allocator, pFramebuffer).checkVKErrors()
                name(!pFramebuffer, "Shadow map framebuffer #$frameIndex", VK_DEBUG_REPORT_OBJECT_TYPE_FRAMEBUFFER_EXT)
                framebuffers += !pFramebuffer
            }
        }
        shadowMapFramebuffers = framebuffers
    }

    private fun createFramebuffers() {
        val framebuffers = mutableListOf<VkFramebuffer>()
        useStack {
            swapchainImageViews.forEachIndexed { index, it ->
                val attachments = mallocLong(8)
                attachments.put(it) // final color
                attachments.put(gColorImages[index].view) // gColor
                attachments.put(gPosImages[index].view) // gPos
                attachments.put(gNormalImages[index].view) // gNormal
                attachments.put(lightingOutImages[index].view) // lightingOut
                attachments.put(ssaoOutImages[index].view) // ssaoOut
                attachments.put(depthImageView)
                attachments.put(gSpecularImages[index].view) // ssaoOut
                attachments.rewind()

                val framebufferInfo = VkFramebufferCreateInfo.callocStack(this)
                framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                framebufferInfo.width(swapchainExtent.width())
                framebufferInfo.height(swapchainExtent.height())
                framebufferInfo.layers(1)
                framebufferInfo.pAttachments(attachments)
                framebufferInfo.renderPass(usualRenderPass)

                val pFramebuffer = mallocLong(1)
                vkCreateFramebuffer(logicalDevice, framebufferInfo, Allocator, pFramebuffer).checkVKErrors()
                name(!pFramebuffer, "Main framebuffer #$frameIndex", VK_DEBUG_REPORT_OBJECT_TYPE_FRAMEBUFFER_EXT)
                framebuffers += !pFramebuffer
            }
        }
        swapchainFramebuffers = framebuffers
    }

    private fun createCommandPool(): VkCommandPool {
        return useStack {
            val familyIndices = findQueueFamilies(physicalDevice)
            val poolInfo = VkCommandPoolCreateInfo.callocStack(this)
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            poolInfo.queueFamilyIndex(familyIndices.graphics!!)
            poolInfo.flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT or VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT) // some buffers are rerecorded very often and not all should be reset at once

            val pCommandPool = mallocLong(1)
            vkCreateCommandPool(logicalDevice, poolInfo, Allocator, pCommandPool).checkVKErrors()
            !pCommandPool
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
            val result = DescriptorSet(descriptorSets.toTypedArray(), builder.bindings.count { it is DescriptorSetUpdateBuilder.UniformBufferBinding && it.dynamic })
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

    private fun createTextureSampler(filter: Int = VK_FILTER_LINEAR, addressMode: Int = VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT): VkSampler {
        return useStack {
            val samplerInfo = VkSamplerCreateInfo.callocStack(this)
            samplerInfo.sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)

            // todo change for different filters
            samplerInfo.magFilter(filter)
            samplerInfo.minFilter(filter)

            samplerInfo.addressModeU(addressMode)
            samplerInfo.addressModeV(addressMode)
            samplerInfo.addressModeW(addressMode)

            samplerInfo.anisotropyEnable(true)
            samplerInfo.maxAnisotropy(16f) // todo: configurable to configure performance

            samplerInfo.borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_WHITE)
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
        val imageInfo = createAttachmentImageView(getDepthFormat(), swapchainExtent.width(), swapchainExtent.height(), VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_IMAGE_ASPECT_DEPTH_BIT)
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

    private fun getDepthFormat(): VkFormat {
        val depthFormat by lazy { findSupportedFormat(VK_IMAGE_TILING_OPTIMAL, VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT) }
        return depthFormat
    }

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
            val textureData = (javaClass.getResource(path) ?: error("Could not open file $path")).readBytes()
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
    fun uploadBuffer(usage: VkBufferUsageFlags, dataBuffer: ByteBuffer, bufferSize: VkDeviceSize): Pair<VkBuffer, VkDeviceMemory> {
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

            result to !pMemory
        }
    }

    /**
     * Same as uploadBuffer but does not create a new buffer
     */
    fun fillBuffer(buffer: VkBuffer, dataBuffer: ByteBuffer, bufferSize: VkDeviceSize): Unit {
        useStack {
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

            copyBuffer(stagingBuffer, buffer, bufferSize)

            vkDestroyBuffer(logicalDevice, stagingBuffer, Allocator)
            vkFreeMemory(logicalDevice, stagingBufferMemory, Allocator)
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
        vkFreeCommandBuffers(logicalDevice, temporaryCommandPool, commandBuffer)
    }

    private fun beginSingleUseCommandBuffer(): VkCommandBuffer {
        return useStack {
            // allocate command buffer to perform copy
            val allocInfo = VkCommandBufferAllocateInfo.callocStack(this)
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            allocInfo.commandPool(temporaryCommandPool)
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

    private fun createPrimaryCommandBuffers() {
        val count = swapchainFramebuffers.size
        useStack {
            val allocationInfo = VkCommandBufferAllocateInfo.callocStack(this)
            allocationInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocationInfo.commandBufferCount(count)
            allocationInfo.commandPool(primaryCommandPool)
            allocationInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY) // SECONDARY for reuse in other buffers

            val pBuffers = mallocPointer(count)
            vkAllocateCommandBuffers(logicalDevice, allocationInfo, pBuffers).checkVKErrors()
            primaryCommandBuffers = pBuffers.it().map { VkCommandBuffer(it, logicalDevice) }
        }
    }


    private fun createSecondaryCommandBuffers() {
        val count = swapchainFramebuffers.size
        // secondary buffer
        useStack {
            val allocationInfo = VkCommandBufferAllocateInfo.callocStack(this)
            allocationInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            allocationInfo.commandBufferCount(count)
            allocationInfo.commandPool(secondaryCommandPool)
            allocationInfo.level(VK_COMMAND_BUFFER_LEVEL_SECONDARY) // SECONDARY for reuse in other buffers

            val pBuffers = mallocPointer(count)
            vkAllocateCommandBuffers(logicalDevice, allocationInfo, pBuffers).checkVKErrors()
            secondaryCommandBuffers = pBuffers.it().map { VkCommandBuffer(it, logicalDevice) }
        }
    }

    fun recordBuffers() {
        renderBatches.reset()

        scene?.let {
            it.record(renderBatches)
        }

        recordSecondaryCommandBuffers()
        recordPrimaryCommandBuffers()
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

                recordBuffers()
            }
        }
    }

    private fun recordSecondaryCommandBuffers() {
        synchronized(secondaryCommandBuffers) {
            useStack {
                secondaryCommandBuffers.forEachIndexed { index, commandBuffer ->
                    val beginInfo = VkCommandBufferBeginInfo.callocStack(this)
                    beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT or VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)

                    val inheritance = VkCommandBufferInheritanceInfo.callocStack(this)
                    inheritance.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO)
                    inheritance.framebuffer(swapchainFramebuffers[index])
                    inheritance.renderPass(usualRenderPass)
                    beginInfo.pInheritanceInfo(inheritance)

                    vkBeginCommandBuffer(commandBuffer, beginInfo).checkVKErrors()
                    renderBatches.recordAll(commandBuffer, index)
                    vkEndCommandBuffer(commandBuffer).checkVKErrors()
                }
            }
        }
    }

    private fun recordShadowMapping(commandBuffer: VkCommandBuffer, index: Int) {
        useStack {
            val clearValues = VkClearValue.callocStack(MaxShadowMaps, this)

            // one per attachment
            for(i in 0 until MaxShadowMaps) {
                clearValues.get(i).depthStencil(VkClearDepthStencilValue.callocStack(this).depth(1.0f).stencil(0))
            }

            val renderPassInfo = VkRenderPassBeginInfo.callocStack(this)
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            renderPassInfo.renderPass(shadowMapRenderPass)
            renderPassInfo.framebuffer(shadowMapFramebuffers[index])

            renderPassInfo.renderArea().offset(VkOffset2D.callocStack(this).set(0, 0))
            renderPassInfo.renderArea().extent(shadowMapExtent)
            renderPassInfo.pClearValues(clearValues)

            // Shadow Mapping Pass
            vkCmdBeginRenderPass(
                commandBuffer,
                renderPassInfo,
                VK_SUBPASS_CONTENTS_INLINE
            ) // VK_SUBPASS_CONTENTS_INLINE -> primary buffer only

            fun pass(lightIndex: Int) {
                useDescriptorSets(commandBuffer, index, shadowMappingPipelines[lightIndex], shadowMappingShaderDescriptorSets[lightIndex])
                if(shadowCastingLights[lightIndex].shadowMapIndex != -1) { // detects placeholder lights
                    renderBatches.recordAll(commandBuffer, lightIndex, shadowMappingPipelines[lightIndex])
                }
            }

            pass(0)

            for(i in 1 until MaxShadowMaps) {
                vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
                pass(i)
            }

            vkCmdEndRenderPass(commandBuffer)
        }
    }

    private fun recordPrimaryCommandBuffers() {
        synchronized(primaryCommandBuffers) {
            useStack {
                primaryCommandBuffers.forEachIndexed { index, commandBuffer ->
                    val beginInfo = VkCommandBufferBeginInfo.callocStack(this)
                    beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    beginInfo.flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                    beginInfo.pInheritanceInfo(null)

                    vkBeginCommandBuffer(commandBuffer, beginInfo).checkVKErrors()

                    // Shadow mapping
                    recordShadowMapping(commandBuffer, index)

                    // G-Pass and Lighting
                    val clearValues = VkClearValue.callocStack(8, this)
                    // one per attachment
                    clearValues.get(0).color(VkClearColorValue.callocStack(this).float32(floats(0.0f, 0.0f, 0.0f, 1f)))
                    clearValues.get(1).color(VkClearColorValue.callocStack(this).float32(floats(0.0f, 0.0f, 0.0f, 1f)))
                    clearValues.get(2).color(VkClearColorValue.callocStack(this).float32(floats(0.0f, 0.0f, 0.0f, 1f)))
                    clearValues.get(3).color(VkClearColorValue.callocStack(this).float32(floats(0.0f, 0.0f, 0.0f, 1f)))
                    clearValues.get(4).color(VkClearColorValue.callocStack(this).float32(floats(0.0f, 0.0f, 0.0f, 1f)))
                    clearValues.get(5).color(VkClearColorValue.callocStack(this).float32(floats(1f, 1f, 1f, 1f)))
                    clearValues.get(6).depthStencil(VkClearDepthStencilValue.callocStack(this).depth(1.0f).stencil(0))
                    clearValues.get(7).color(VkClearColorValue.callocStack(this).float32(floats(0f, 0f, 0f, 1f)))

                    val renderPassInfo = VkRenderPassBeginInfo.callocStack(this)
                    renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    renderPassInfo.renderPass(usualRenderPass)
                    renderPassInfo.framebuffer(swapchainFramebuffers[index])

                    renderPassInfo.renderArea().offset(VkOffset2D.callocStack(this).set(0, 0))
                    renderPassInfo.renderArea().extent(swapchainExtent)
                    renderPassInfo.pClearValues(clearValues)

                    // GBuffer Pass
                    vkCmdBeginRenderPass(
                        commandBuffer,
                        renderPassInfo,
                        VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS
                    ) // VK_SUBPASS_CONTENTS_INLINE -> primary buffer only

                  /*  renderBatches.reset()

                    scene?.let {
                        it.record(renderBatches)

                        renderBatches.recordAll(commandBuffer, index)
                    }*/
                    vkCmdExecuteCommands(commandBuffer, secondaryCommandBuffers[index])


                    // Lighting pass
                    vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
                    simpleQuadPass(commandBuffer, index, lightingPipeline, lightingShaderDescriptorSet0, lightingShaderDescriptorSet1)

                    // SSAO pass
                    vkCmdNextSubpass(commandBuffer, VK_SUBPASS_CONTENTS_INLINE)
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

    private fun simpleQuadPass(commandBuffer: VkCommandBuffer, frameIndex: Int, pipeline: GraphicsPipeline, vararg sets: DescriptorSet) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle)

        useStack {
            val pSets = mallocLong(sets.size)
            for(i in sets.indices) {
                pSets.put(i, sets[i].sets[frameIndex])
            }
            val dynamicOffsetCount = sets.sumBy { it.dynamicCount }
            val dynamicOffsets = mallocInt(dynamicOffsetCount)
            for(i in 0 until dynamicOffsetCount) {
                dynamicOffsets.put(i, 0)
            }
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.layout,
                0,
                pSets,
                dynamicOffsets
            )

            // TODO: move to secondary buffer
            screenQuad.directRecord(this, commandBuffer, true, 1)
        }
    }

    /**
     * Tells Vulkan to start using the given descriptor sets from now on.
     * Set order is important
     */
    fun useDescriptorSets(commandBuffer: VkCommandBuffer, commandBufferIndex: Int, pipeline: GraphicsPipeline, vararg sets: DescriptorSet) {
        useStack {
            val pSets = mallocLong(sets.size)
            for(i in sets.indices) {
                pSets.put(i, sets[i][commandBufferIndex])
            }

            val offsets = mallocInt(1)
            offsets.put(0)
            offsets.flip()
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.layout, // TODO customizable
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
        renderBatches.newFrame()
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

            updateUniformBuffers(!pImageIndex)

            graphicsQueueSubmit(!pImageIndex, currentFrame)

            present(!pImageIndex, currentFrame)
        }

        currentFrame = (currentFrame + 1) % maxFramesInFlight
    }

    private fun updateUniformBuffers(frameIndex: Int) {
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

        var up = 0f
        if (glfwGetKey(windowPointer, GLFW_KEY_SPACE) == GLFW_PRESS) {
            up += 1f
        }
        if (glfwGetKey(windowPointer, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            up -= 1f
        }

        val direction by lazy { Vector3f() }
        if (strafe != 0f || forward != 0f || up != 0f) {
            direction.set(-strafe, 0f, forward)
            direction/*.normalize()*/.rotateAxis(defaultCamera.yaw, 0f, 1f, 0f)
            direction.y = up
            val speed = 0.01f
            direction.mul(speed)
            defaultCamera.position.add(direction)
        }

        defaultCamera.updateCameraObject(cameraObject)
        cameraObject.update(logicalDevice, memoryStack, frameIndex)

        if(scene != null) {
            scene!!.preRenderFrame(frameIndex, lightBufferObject)
        }
        lightBufferObject.update(logicalDevice, memoryStack, frameIndex)

        // update shadow mapping views
        for((i, light) in shadowCastingLights.withIndex()) {
            val lightCameraObject = shadowMappingCameraObjects[i]
            light.updateCameraForShadowMapping(lightCamera, i-light.shadowMapIndex)
            lightCamera.updateCameraObject(lightCameraObject)
            lightCameraObject.update(logicalDevice, memoryStack, frameIndex)
        }


        shadowMappingMatrices.update(logicalDevice, memoryStack, frameIndex)

        renderBatches.updateInstances(frameIndex)

        useStack {
            // TODO: customize camera
            ssaoBufferObject.proj.set(defaultCamera.projection)
            ssaoBufferObject.proj.m11(ssaoBufferObject.proj.m11() * -1f)
            ssaoBufferObject.update(logicalDevice, this, frameIndex)
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
        buffers.put(primaryCommandBuffers[currentFrame].address()).flip()
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
        gPosImages.clear()
        gNormalImages.clear()
        gSpecularImages.clear()
        ssaoOutImages.clear()
        lightingOutImages.clear()

        createSwapchain()
        createRenderImageViews()
        createCamera()
        shadowMapRenderPass = createShadowPass(getDepthFormat())
        usualRenderPass = createRenderPass()
        gBufferPipeline = createGraphicsPipeline(gBufferPipelineBuilder())
        renderToScreenPipeline = createGraphicsPipeline(renderToScreenPipelineBuilder())
        lightingPipeline = createGraphicsPipeline(lightingPipelineBuilder())
        ssaoPipeline = createGraphicsPipeline(ssaoPipelineBuilder())
        createDepthResources()
        createFramebuffers()
        createShadowMapFramebuffers()
        descriptorPool = createDescriptorPool()
        createSecondaryCommandBuffers()
        createPrimaryCommandBuffers()
    }

    private fun cleanupSwapchain() {
        vkDestroyImageView(logicalDevice, depthImageView, Allocator)
        vkFreeMemory(logicalDevice, depthImageMemory, Allocator)
        vkDestroyImage(logicalDevice, depthImage, Allocator)

        destroyPrimaryCommandBuffers()
        destroySecondaryCommandBuffers()

        destroyImages(gPosImages)
        destroyImages(gColorImages)
        destroyImages(gNormalImages)
        destroyImages(gSpecularImages)
        destroyImages(lightingOutImages)
        destroyImages(ssaoOutImages)

        vkDestroyPipeline(logicalDevice, ssaoPipeline.handle, Allocator)
        vkDestroyPipelineLayout(logicalDevice, ssaoPipeline.layout, Allocator)
        vkDestroyPipeline(logicalDevice, renderToScreenPipeline.handle, Allocator)
        vkDestroyPipelineLayout(logicalDevice, renderToScreenPipeline.layout, Allocator)
        vkDestroyPipeline(logicalDevice, lightingPipeline.handle, Allocator)
        vkDestroyPipelineLayout(logicalDevice, lightingPipeline.layout, Allocator)
        vkDestroyPipeline(logicalDevice, gBufferPipeline.handle, Allocator)
        vkDestroyPipelineLayout(logicalDevice, gBufferPipeline.layout, Allocator)

        vkDestroyRenderPass(logicalDevice, usualRenderPass, Allocator)
        if(shadowMapRenderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(logicalDevice, shadowMapRenderPass, Allocator)
        }
        for(view in swapchainImageViews) {
            vkDestroyImageView(logicalDevice, view, Allocator)
        }

        for(framebuffer in shadowMapFramebuffers) {
            vkDestroyFramebuffer(logicalDevice, framebuffer, Allocator)
        }
        for(framebuffer in swapchainFramebuffers) {
            vkDestroyFramebuffer(logicalDevice, framebuffer, Allocator)
        }
        vkDestroySwapchainKHR(logicalDevice, swapchain, Allocator)
    }

    private fun destroyImages(images: List<ImageInfo>) {
        for(img in images) {
            vkDestroyImageView(logicalDevice, img.view, Allocator)
            vkFreeMemory(logicalDevice, img.memory, Allocator)
            vkDestroyImage(logicalDevice, img.image, Allocator)
        }
    }

    private fun destroyPrimaryCommandBuffers() {
        useStack {
            vkResetCommandPool(logicalDevice, primaryCommandPool, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT)
        }
    }

    private fun destroySecondaryCommandBuffers() {
        useStack {
            vkResetCommandPool(logicalDevice, secondaryCommandPool, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT)
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
        vkDestroySampler(logicalDevice, nearestSampler, Allocator)
        vkDestroyDescriptorPool(logicalDevice, descriptorPool, Allocator)

        vkDestroyCommandPool(logicalDevice, temporaryCommandPool, Allocator)
        vkDestroyCommandPool(logicalDevice, secondaryCommandPool, Allocator)
        vkDestroyCommandPool(logicalDevice, primaryCommandPool, Allocator)

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

    internal fun <T> useStack(action: MemoryStack.() -> T) = memoryStack.useStack(action)

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

    private fun nextTextureID(usage: TextureUsage): Int {
        val texID = textureCounters[usage.ordinal]
        if(texID >= MaxTextures) {
            error("No more texture space")
        }
        textureCounters[usage.ordinal] = texID+1
        return texID
    }

    /**
     * Load a texture into Vulkan.
     * Caching is applied
     */
    fun createTexture(path: String, usage: TextureUsage = TextureUsage.Diffuse): Texture {
        if(path in activeTextures) {
            return activeTextures[path]!!
        }
        return useStack {
            val pImage = mallocLong(1)
            val pImageMemory = mallocLong(1)
            createTextureImage(path, pImage, pImageMemory)
            val imageView = createImageView(!pImage, VK_FORMAT_R8G8B8A8_SRGB)
            // TODO: custom sampler
            val texture = Texture(nextTextureID(usage), !pImage, imageView, path)

            imageViews[usage.ordinal][texture.textureID] = imageView
            println("created texture with ID ${texture.textureID} with usage ${usage.name}")

            val binding = when(usage) {
                TextureUsage.Specular -> 3
                TextureUsage.Diffuse -> 1
                else -> 1
            }
            updateDescriptorSet(gBufferShaderDescriptor, DescriptorSetUpdateBuilder().textureSampling(texture, binding))
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

    fun createUBO(): UniformBufferObject {
        return UniformBufferObject()
    }

    private var currentTexture = Array<Texture?>(TextureUsage.values().size) { null }

    /**
     * Only for use when recording to a command buffer
     */
    internal fun bindTexture(commandBuffer: VkCommandBuffer, usage: TextureUsage, tex: Texture, pipelineLayout: VkPipeline = gBufferPipeline.layout) {
        if(tex != currentTexture[usage.ordinal]) { // send the push constant change only if texture actually changed
            val offset = when(usage) {
                TextureUsage.Specular -> 4
                else -> 0
            }
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_FRAGMENT_BIT, offset, intArrayOf(tex.textureID))
            currentTexture[usage.ordinal] = tex
        }
    }

    fun updateShadowMapIndices() {
        lightBufferObject.resetShadowMapIndices()
        var cursor = 0
        for(light in shadowCastingLights.distinct()) {
            light.shadowMapIndex = cursor
            cursor += light.type.shadowMapCount
        }
    }

    fun setLights(lights: List<Light>) {
        lightBufferObject.setLights(lights)
        updateShadowMapIndices()
    }

}