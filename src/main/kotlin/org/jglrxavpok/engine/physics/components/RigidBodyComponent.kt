package org.jglrxavpok.engine.physics.components

import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.linearmath.Transform
import org.jglrxavpok.engine.scene.Element
import org.jglrxavpok.engine.scene.LogicComponent
import org.joml.Matrix3f

class RigidBodyComponent(val body: RigidBody): LogicComponent {

    private val transform: Transform = Transform()
    private val convertedMatrix = Matrix3f()

    override fun tick(element: Element, dt: Float) {
        body.getWorldTransform(transform)

        val origin = transform.origin
        val rotation = transform.basis

        element.position.set(origin.x, origin.y, origin.z)
        convertedMatrix.m00 = rotation.m00
        convertedMatrix.m01 = rotation.m01
        convertedMatrix.m02 = rotation.m02
        convertedMatrix.m10 = rotation.m10
        convertedMatrix.m11 = rotation.m11
        convertedMatrix.m12 = rotation.m12
        convertedMatrix.m20 = rotation.m20
        convertedMatrix.m21 = rotation.m21
        convertedMatrix.m22 = rotation.m22

        element.rotation.setFromUnnormalized(convertedMatrix)
    }
}