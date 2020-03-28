package org.jglrxavpok.engine.render.model

import org.jglrxavpok.engine.io.AssimpFileSystem
import org.jglrxavpok.engine.render.UniformBufferObject
import org.jglrxavpok.engine.render.Vertex
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.assimp.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkCommandBuffer

class Model(val path: String, autoload: Boolean = true) {

    private val meshes = mutableListOf<Mesh>()
    private val materials = mutableListOf<Material>()
    val ubo = UniformBufferObject()

    init {
        if(autoload) {
            load()
        }
    }


    /**
     * Load the model from its path, extract meshes and materials
     */
    fun load() {
        val data = javaClass.getResource(path).readBytes()
        val dataBuffer = MemoryUtil.memAlloc(data.size)
        dataBuffer.put(data)
        dataBuffer.position(0)
        // TODO: generalize hints
        val scene = Assimp.aiImportFileEx(path,Assimp.aiProcess_Triangulate or Assimp.aiProcess_FlipUVs, AssimpFileSystem)
        if(scene == null || scene.mFlags() and Assimp.AI_SCENE_FLAGS_INCOMPLETE != 0 || scene.mRootNode() == null) {
            System.err.println("ERROR::ASSIMP: "+Assimp.aiGetErrorString()!!.substringBefore("\n"))
            error("Failed to load file with Assimp")
        }
        processMaterials(scene)
        processNode(scene.mRootNode()!!, scene)
        MemoryUtil.memFree(dataBuffer)
    }

    /**
     * Load materials present in the Assimp scene
     */
    private fun processMaterials(scene: AIScene) {
        for(i in 0 until scene.mNumMaterials()) {
            val materials = scene.mMaterials()!!
            val materialAddress = materials[i]
            val material = AIMaterial.create(materialAddress)

            val builder = MaterialBuilder()
            val count = Assimp.aiGetMaterialTextureCount(material, Assimp.aiTextureType_DIFFUSE)
            if(count > 0) { // TODO: support for multiple textures
                val path = AIString.malloc()

                println("count > 0")
                // add only if texture is present
                if(Assimp.aiGetMaterialTexture(material, Assimp.aiTextureType_DIFFUSE, 0, path, null as? IntArray, null, null, null, null, null) == 0) {
                    val pathString = "/${path.dataString()}"
                    println("load texture $pathString")
                    builder.diffuseTexture(TextureDescriptor(pathString, TextureUsage.Diffuse))
                }
                path.free()
            }

            this.materials += builder.build()
        }

        VulkanRenderingEngine.load {
            materials.forEach {
                it.diffuseTexture?.let { descriptor ->
                    VulkanRenderingEngine.createTexture(descriptor.path)
                }
            }
        }
    }

    /**
     * Load all nodes of the Assimp scenes, and extract meshes
     */
    private fun processNode(rootNode: AINode, scene: AIScene) {
        for (i in 0 until rootNode.mNumMeshes()) {
            val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
            meshes += processMesh(mesh, scene)
        }

        for (i in 0 until rootNode.mNumChildren()) {
            processNode(AINode.create(rootNode.mChildren()!!.get(i)), scene)
        }
    }

    /**
     * Builds a single Vulkan mesh from a Assimp mes
     */
    private fun processMesh(mesh: AIMesh, scene: AIScene): Mesh {
        val vertices = mutableListOf<Vertex>()
        val indices = mutableListOf<UInt>()

        val hasColor = mesh.mColors(0) != null
        val hasTexCoords = mesh.mTextureCoords(0) != null
        for(vertexIndex in 0 until mesh.mNumVertices()) {
            val pos = mesh.mVertices().get(vertexIndex)
            val colorInfo = if(hasColor) mesh.mColors(0)!![vertexIndex] else null
            val texInfo = if(hasTexCoords) mesh.mTextureCoords(0)!![vertexIndex] else null

            val color = if(colorInfo != null) {
                Vector3f(colorInfo.r(), colorInfo.g(), colorInfo.b()) // TODO: support alpha
            } else {
                Vector3f(1f,1f,1f)
            }

            // TODO: support untextured models
            val texCoords = if(texInfo != null) {
                Vector2f(texInfo.x(), texInfo.y())
            } else {
                Vector2f(0f, 0f)
            }

            // TODO: process normals
            val vertex = Vertex(
                Vector3f(pos.x(), pos.y(), pos.z()),
                color,
                texCoords
            )
            vertices += vertex
        }

        for(i in 0 until mesh.mNumFaces()) {
            val face = mesh.mFaces()[i]
            if(face.mNumIndices() != 3) {
                error("Does not support non-triangle faces")
            }

            indices.add(face.mIndices()[0].toUInt())
            indices.add(face.mIndices()[1].toUInt())
            indices.add(face.mIndices()[2].toUInt())
        }

        val materialIndex = mesh.mMaterialIndex()
        val material = materials[materialIndex]

        return Mesh(vertices, indices, material = material)
    }

    /**
     * Renders this model to the given command buffer
     */
    fun record(commandBuffer: VkCommandBuffer, commandBufferIndex: Int) {
        meshes.forEach {
            it.record(commandBuffer, commandBufferIndex, ubo)
        }
    }
}