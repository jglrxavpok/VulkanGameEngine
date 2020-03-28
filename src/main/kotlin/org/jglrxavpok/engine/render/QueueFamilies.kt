package org.jglrxavpok.engine.render

/**
 * Queues used by the rendering engine
 */
class QueueFamilies: Iterable<Int?> {
    var graphics: Int? = null

    var present: Int? = null
    val isComplete get() = graphics != null && present != null

    override fun iterator(): Iterator<Int?> {
        return listOf(present, graphics).distinct().iterator()
    }

}