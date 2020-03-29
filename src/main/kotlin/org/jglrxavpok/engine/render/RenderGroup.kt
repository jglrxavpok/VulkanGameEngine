package org.jglrxavpok.engine.render

interface RenderGroup {
    val pipeline: GraphicsPipeline
}

interface RenderGroupDispatcher {
    fun getBuffer(frameIndex: Int, group: RenderGroup)
}