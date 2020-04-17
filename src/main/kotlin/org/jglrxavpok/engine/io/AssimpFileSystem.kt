package org.jglrxavpok.engine.io

import org.jglrxavpok.engine.render.model.Model
import org.lwjgl.assimp.AIFile
import org.lwjgl.assimp.AIFileIO
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryUtil

/**
 * FileSystem to use to load Assimp assets from classpath
 */
val AssimpFileSystem = AIFileIO.create()
    .OpenProc { pFileIO, fileName, openMode ->
        val path = MemoryUtil.memUTF8(fileName)
        val connection = Model::class.java.getResource(path).openConnection()
        val inputStream = connection.getInputStream().buffered()
        AIFile.malloc()
            .FileSizeProc {
                connection.contentLengthLong
            }
            .SeekProc { pFile, offset, origin ->
                Assimp.AI_FALSE
            }
            .FlushProc {

            }
            .TellProc {
                -1
            }
            .WriteProc { pFile, pBuffer, memB, count ->
                -1
            }
            .ReadProc { pFile, pBuffer, size, count ->
                val length = size*count
                val out = ByteArray(length.toInt())

                val read = inputStream.read(out, 0, length.toInt()).toLong()

                if(read > 0) {
                    val buffer = MemoryUtil.memByteBuffer(pBuffer, length.toInt())
                    buffer.put(out, 0, read.toInt())
                }
                if(read < 0) {
                    return@ReadProc 0
                }

                read
            }
            .address()
    }
    .CloseProc { pFileIO, pFile ->
        AIFile.create(pFile).free()
    }