package org.jglrxavpok.engine.render

import kotlin.reflect.KProperty

/**
 * Represents a graphical asset that might be loaded later on the rendering thread
 */
class AsyncGraphicalAsset<T: Any> {
    private lateinit var value: T

    private val loaded get()= this::value.isInitialized
    private var initializer: () -> T

    /**
     * Asset that will be initialized later on the rendering thread
     */
    constructor(initializer: () -> T) {
        this.initializer = initializer
    }

    /**
     * Asset that is already initialized with a value
     */
    constructor(preinitializedValue: T) {
        this.initializer = { preinitializedValue }
        this.value = preinitializedValue
    }

    /**
     * Loads the value
     */
    internal fun load() {
        value = initializer()
    }

    /**
     * Used by the 'by' operator of Kotlin, will sleep as long as the value is not loaded
     */
    operator fun getValue(owner: Any, property: KProperty<*>): T {
        while(!loaded) {
            Thread.sleep(1)
        }
        return value
    }

}
