package org.jglrxavpok.engine.render

import kotlin.reflect.KProperty

/**
 * Represents a graphical asset that might be loaded later on the rendering thread
 */
class AsyncGraphicalAsset<T: Any> {
    private lateinit var value: T

    private val loaded get()= this::value.isInitialized
    private var initializer: () -> T
    private val replacement: () -> T

    /**
     * Asset that will be initialized later on the rendering thread.
     * Replacement is the asset to use will this one is loading, must already be loaded
     */
    constructor(replacement: () -> T, initializer: () -> T) {
        this.initializer = initializer
        this.replacement = replacement
    }

    /**
     * Asset that is already initialized with a value
     */
    constructor(preinitializedValue: T) {
        this.initializer = { preinitializedValue }
        this.value = preinitializedValue
        this.replacement = initializer
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
        if(!loaded) {
            return replacement()
        }
        return value
    }

}
