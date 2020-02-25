package org.jglrxavpok.engine.render

import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkCommandBuffer
import java.util.*

class Model(val path: String, autoload: Boolean = true) {

    private val meshes = mutableListOf<Mesh>()

    init {
        if(autoload) {
            load()
        }
    }


    fun load() {
        val data = javaClass.getResource(path).readBytes()
        val dataBuffer = MemoryUtil.memAlloc(data.size)
        dataBuffer.put(data)
        dataBuffer.position(0)
        // TODO: generalize hints
        val scene = Assimp.aiImportFileFromMemory(dataBuffer,Assimp.aiProcess_Triangulate or Assimp.aiProcess_FlipUVs, "obj")
        if(scene == null || scene.mFlags() and Assimp.AI_SCENE_FLAGS_INCOMPLETE != 0 || scene.mRootNode() == null) {
            System.err.println("ERROR::ASSIMP: "+Assimp.aiGetErrorString()!!.substringBefore("\n"))
            error("Failed to load file with Assimp")
        }
        processNode(scene.mRootNode()!!, scene)
        MemoryUtil.memFree(dataBuffer)
    }

    private fun processNode(rootNode: AINode, scene: AIScene) {
        for (i in 0 until rootNode.mNumMeshes()) {
            val mesh = AIMesh.create(scene.mMeshes()!!.get(i))
            meshes += processMesh(mesh, scene)
        }

        for (i in 0 until rootNode.mNumChildren()) {
            processNode(AINode.create(rootNode.mChildren()!!.get(i)), scene)
        }
    }

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

            // TODO: process materials
            // TODO: process normals
            val vertex = Vertex(Vector3f(pos.x(), pos.y(), pos.z()), color, texCoords)
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


        return Mesh(vertices, indices)
    }

    fun record(commandBuffer: VkCommandBuffer) {
        meshes.forEach {
            it.record(commandBuffer)
        }
    }
}