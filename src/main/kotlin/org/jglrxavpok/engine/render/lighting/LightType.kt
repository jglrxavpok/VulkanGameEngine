package org.jglrxavpok.engine.render.lighting

enum class LightType(val shadowMapCount: Int) {
    Dummy(1),
    Directional(4),
    Point(6),
    Spot(1),
}