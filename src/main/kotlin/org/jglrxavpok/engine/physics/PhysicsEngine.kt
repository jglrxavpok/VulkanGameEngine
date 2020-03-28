package org.jglrxavpok.engine.physics

import com.bulletphysics.collision.broadphase.DbvtBroadphase
import com.bulletphysics.collision.dispatch.CollisionDispatcher
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration
import com.bulletphysics.dynamics.DiscreteDynamicsWorld
import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver
import org.jglrxavpok.engine.GameEngine
import org.jglrxavpok.engine.render.VulkanRenderingEngine
import org.joml.Vector3fc
import java.util.concurrent.Semaphore

/**
 * Entry point to the physics engine
 */
object PhysicsEngine {

    private lateinit var world: DiscreteDynamicsWorld
    private var lastStepTime = 0.0
    private val initSemaphore = Semaphore(1).apply { acquire() }

    fun init() {
        val broadphase = DbvtBroadphase()
        val collisionConfiguration = DefaultCollisionConfiguration()
        val dispatcher = CollisionDispatcher(collisionConfiguration)

        val solver = SequentialImpulseConstraintSolver()

        world = DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration)
        lastStepTime = GameEngine.time

        initSemaphore.release()
    }

    fun tick() {
        val deltaTime = GameEngine.time - lastStepTime
        synchronized(world) {
            world.stepSimulation(deltaTime.toFloat())
        }
        lastStepTime = GameEngine.time
    }

    /**
     * Sets the gravity of the world.
     * The given vector is copied
     */
    fun setGravity(gravity: Vector3fc) {
        synchronized(world) {
            world.setGravity(javax.vecmath.Vector3f(gravity.x(), gravity.y(), gravity.z()))
        }
    }

    /**
     * Adds a new rigid body to the simulation
     */
    fun addRigidBody(body: RigidBody) {
        synchronized(world) {
            world.addRigidBody(body)
        }
    }

    /**
     * Waits for the physics engine to be ready
     */
    fun waitForInit() {
        initSemaphore.acquire()
    }

}