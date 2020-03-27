package org.jglrxavpok.engine.render

import kotlin.reflect.KProperty

class AsyncGraphicalAsset<T: Any> {
    private lateinit var value: T

    private val loaded get()= this::value.isInitialized
    private var initializer: () -> T

    constructor(initializer: () -> T) {
        this.initializer = initializer
    }

    constructor(preinitializedValue: T) {
        this.initializer = { preinitializedValue }
        this.value = preinitializedValue
    }

    fun load() {
        value = initializer()
    }

    operator fun getValue(owner: Any, property: KProperty<*>): T {
        while(!loaded) {
            Thread.sleep(1)
        }
        return value
    }

}
