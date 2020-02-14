package org.jglrxavpok.engine.render

import org.jglrxavpok.engine.VkImage
import java.nio.ByteBuffer

class Image(val handle: VkImage, val width: Int, val height: Int): ShaderResource() {


    // images are uploaded in a different place
    override fun write(buffer: ByteBuffer): ByteBuffer {
        /* nop */
        return buffer
    }

    override fun read(from: ByteBuffer): ShaderResource {
        /* nop */
        return this
    }
}