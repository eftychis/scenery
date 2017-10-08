package graphics.scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import java.io.Serializable
import java.lang.reflect.Field
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Class describing a [Node] of a [Scene], inherits from [Renderable]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a node with default settings, e.g. identity matrices
 *  for model, view, projection, etc.
 * @property[name] The name of the [Node]
 */
open class Node(open var name: String = "Node") : Renderable, Serializable {

    /** Hash map used for storing metadata for the Node. [DeferredLightingRenderer] uses
     * it to e.g. store [OpenGLObjectState]. */
    @Transient var metadata: HashMap<String, NodeMetadata> = HashMap()

    /** Material of the Node */
    @Transient override var material: Material = Material.DefaultMaterial()
    /** Initialisation flag. */
    override var initialized: Boolean = false
    /** Whether the Node is dirty and needs updating. */
    override var dirty: Boolean = true
    /** Flag to set whether the Node is visible or not, recursively affects children. */
    override var visible: Boolean = true
        set(v) {
            children.forEach { it.visible = v }
            field = v
        }
    /** Is this Node an instance of another Node? */
    var instanceOf: Node? = null
    /** instanced properties */
    var instancedProperties = LinkedHashMap<String, () -> Any>()
    /** flag to set whether this node is an instance master */
    var instanceMaster: Boolean = false
    /** The Node's lock. */
    override var lock: ReentrantLock = ReentrantLock()

    /** bounding box coordinates **/
    var boundingBoxCoords: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    /**
     * Initialisation function for the Node.
     *
     * @return True of false whether initialisation was successful.
     */
    override fun init(): Boolean {
        return true
    }

    /** Name of the Node's type */
    open var nodeType = "Node"
    /** Should the Node's class name be used to derive a GLSL shader file name for a [GLProgram]? */
    open var useClassDerivedShader = false

    /** Node update routine, called before updateWorld */
    open var update: (() -> Unit)? = null

    /** World transform matrix. Will create inverse [iworld] upon modification. */
    override var world: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [world] transform matrix. */
    override var iworld: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Model transform matrix. Will create inverse [imodel] upon modification. */
    override var model: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [world] transform matrix. */
    override var imodel: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** View matrix. Will create inverse [iview] upon modification. */
    override var view: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [view] matrix. */
    override var iview: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** Projection matrix. Will create inverse [iprojection] upon modification. */
    override var projection: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [projection] transform matrix. */
    override var iprojection: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** ModelView matrix. Will create inverse [imodelview] upon modification. */
    override var modelView: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }
    /** Inverse [modelView] transform matrix. */
    override var imodelView: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** ModelViewProjection matrix. */
    override var mvp: GLMatrix by Delegates.observable(GLMatrix.getIdentity()) { property, old, new -> propertyChanged(property, old, new) }

    /** World position of the Node. Setting will trigger [world] update. */
    override var position: GLVector by Delegates.observable(GLVector(0.0f, 0.0f, 0.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** x/y/z scale of the Node. Setting will trigger [world] update. */
    override var scale: GLVector by Delegates.observable(GLVector(1.0f, 1.0f, 1.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** Rotation of the Node. Setting will trigger [world] update. */
    override var rotation: Quaternion by Delegates.observable(Quaternion(0.0f, 0.0f, 0.0f, 1.0f)) { property, old, new -> propertyChanged(property, old, new) }

    /** Children of the Node. */
    @Transient var children: CopyOnWriteArrayList<Node>
    /** Other nodes that have linked transforms. */
    @Transient var linkedNodes: CopyOnWriteArrayList<Node>
    /** Parent node of this node. */
    var parent: Node? = null

    /** Flag to store whether the node is a billboard and will always face the camera. */
    override var isBillboard: Boolean = false

    /** Creation timestamp of the node. */
    var createdAt: Long = 0
    /** Modification timestamp of the node. */
    var modifiedAt: Long = 0

    /** Stores whether the [model] matrix needs an update. */
    var needsUpdate = true
    /** Stores whether the [world] matrix needs an update. */
    var needsUpdateWorld = true

    protected fun <R> propertyChanged(property: KProperty<*>, old: R, new: R): Unit {
        if(property.name == "rotation" || property.name == "position" || property.name  == "scale") {
            needsUpdate = true
            needsUpdateWorld = true
        }
    }

    init {
        this.createdAt = (Timestamp(Date().time).time).toLong()
        this.model = GLMatrix.getIdentity()
        this.imodel = GLMatrix.getIdentity()

        this.modelView = GLMatrix.getIdentity()
        this.imodelView = GLMatrix.getIdentity()

        this.children = CopyOnWriteArrayList<Node>()
        this.linkedNodes = CopyOnWriteArrayList<Node>()
        // null should be the signal to use the default shader
    }

    /**
     * Attaches a child node to this node.
     *
     * @param[child] The child to attach to this node.
     */
    fun addChild(child: Node) {
        child.parent = this
        this.children.add(child)
    }

    /**
     * Removes a given node from the set of children of this node.
     *
     * @param[child] The child node to remove.
     */
    fun removeChild(child: Node): Boolean {
        return this.children.remove(child)
    }

    /**
     * Removes a given node from the set of children of this node.
     * If possible, use [removeChild] instead.
     *
     * @param[name] The name of the child node to remove.
     */
    fun removeChild(name: String): Boolean {
        for (c in this.children) {
            if (c.name.compareTo(name) == 0) {
                c.parent = null
                this.children.remove(c)
                return true
            }
        }

        return false
    }

    /**
     * Routine to call if the node has special requirements for drawing.
     */
    open fun draw() {

    }

    /**
     * Update the the [world] matrix of the [Node].
     *
     * This method will update the [model] and [world] matrices of the node,
     * if [needsUpdate] is true, or [force] is true. If [recursive] is true,
     * this method will also recurse into the [children] and [linkedNodes] of
     * the node and update these as well.
     *
     * @param[recursive] Whether the [children] should be recursed into.
     * @param[force] Force update irrespective of [needsUpdate] state.
     */
    @Synchronized fun updateWorld(recursive: Boolean, force: Boolean = false) {
        update?.invoke()

        if (needsUpdate or force) {
            this.composeModel()

            needsUpdate = false
            needsUpdateWorld = true
        }

        if (needsUpdateWorld or force) {
            if (this.parent == null || this.parent is Scene) {
                world.copyFrom(model)
                //          this.world.translate(this.position.x(), this.position.y(), this.position.z())
            } else {
                world.copyFrom(parent!!.world)
                world.mult(this.model)
                //m.translate(this.position.x(), this.position.y(), this.position.z())
            }

            this.needsUpdateWorld = false
        }

        if (recursive) {
            this.children.forEach { it.updateWorld(true, true) }
            // also update linked nodes -- they might need updated
            // model/view/proj matrices as well
            this.linkedNodes.forEach { it.updateWorld(true, true) }
        }
    }

    /**
     * This method composes the [model] matrices of the node from its
     * [position], [scale] and [rotation].
     */
    fun composeModel() {
        @Suppress("SENSELESS_COMPARISON")
        if(position != null && rotation != null && scale != null) {
            model.setIdentity()
            model.scale(this.scale.x(), this.scale.y(), this.scale.z())
            model.mult(this.rotation)
            model.translate(this.position.x(), this.position.y(), this.position.z())
        }
    }

    /**
     * This method composes the Node's [modelView] matrix.
     */
    fun composeModelView() {
        modelView.copyFrom(model)
        modelView.mult(this.view)
    }

    /**
     * This method composes the Node's [mvp] matrix. It runs
     * [composeModel] and [composeModelView] first.
     */
    fun composeMVP() {
        composeModel()
        composeModelView()

        mvp.copyFrom(modelView)
        mvp.mult(projection)
    }


    fun generateBoundingBox() {

        if (this is Mesh) {
            if (vertices.capacity() == 0) {
                System.err.println("Zero vertices currently, returning null bounding box")
                boundingBoxCoords = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
            } else {

                /*val x = vertices.filterIndexed { i, fl -> (i + 3).mod(3) == 0 }
                val y = vertices.filterIndexed { i, fl -> (i + 2).mod(3) == 0 }
                val z = vertices.filterIndexed { i, fl -> (i + 1).mod(3) == 0 }

                val xmin: Float = x.min()!!.toFloat()
                val xmax: Float = x.max()!!.toFloat()

                val ymin: Float = y.min()!!.toFloat()
                val ymax: Float = y.max()!!.toFloat()

                val zmin: Float = z.min()!!.toFloat()
                val zmax: Float = z.max()!!.toFloat()

                boundingBoxCoords = floatArrayOf(xmin, xmax, ymin, ymax, zmin, zmax)
                */
                System.err.println("Created bouding box with ${boundingBoxCoords.joinToString(", ")}")
            }
        } else {
            System.err.println("Assuming 3rd party BB generation")
            // assume bounding box was created somehow
        }
    }

    private val shaderPropertyFieldCache = HashMap<String, Field>()
    fun getShaderProperty(name: String): Any? {
        if(shaderPropertyFieldCache.containsKey(name)) {
            return shaderPropertyFieldCache[name]!!.get(this)
        } else {
            val field = this.javaClass.declaredFields.find { it.name == name && it.isAnnotationPresent(ShaderProperty::class.java) }

            if(field != null) {
                field.isAccessible = true
                shaderPropertyFieldCache.put(name, field)

                return field.get(this)
            } else {
                return null
            }
        }
    }

    fun getScene(): Scene? {
        var p: Node? = this.parent
        while(p !is Scene && p != null) {
            p = p.parent
        }

        return p as? Scene
    }

    /**
     * Centers the [Node] on a given position.
     *
     * @param[position] - the position to center the [Node] on.
     * @return GLVector - the center offset calculcated for the [Node].
     */
    fun centerOn(position: GLVector): GLVector {
        val min = GLMatrix.getScaling(this.scale).mult(GLVector(this.boundingBoxCoords[0], this.boundingBoxCoords[2], this.boundingBoxCoords[4], 1.0f))
        val max = GLMatrix.getScaling(this.scale).mult(GLVector(this.boundingBoxCoords[1], this.boundingBoxCoords[3], this.boundingBoxCoords[5], 1.0f))

        val center = (max - min) * 0.5f
        this.position = position - center

        return center
    }

    /**
     * Fits the [Node] within a box of the given dimension.
     *
     * @param[sideLength] - The size of the box to fit the [Node] uniformly into.
     */
    fun fitInto(sideLength: Float) {
        val min = GLVector(this.boundingBoxCoords[0], this.boundingBoxCoords[2], this.boundingBoxCoords[4], 1.0f)
        val max = GLVector(this.boundingBoxCoords[1], this.boundingBoxCoords[3], this.boundingBoxCoords[5], 1.0f)

        (max - min).toFloatArray().max()?.let { maxDimension ->
            val scaling = sideLength/maxDimension

            this.scale = GLVector(scaling, scaling, scaling)
        }
    }

    companion object NodeHelpers {
        /**
         * Depth-first search for elements in a Scene.
         *
         * @param[s] The Scene to search in
         * @param[func] A lambda taking a [Node] and returning a Boolean for matching.
         * @return A list of [Node]s that match [func].
         */
        fun discover(origin: Node, func: (Node) -> Boolean): ArrayList<Node> {
            val visited = HashSet<Node>()
            val matched = ArrayList<Node>()

            fun discover(current: Node, f: (Node) -> Boolean) {
                if (!visited.add(current)) return
                for (v in current.children) {
                    if (f(v)) {
                        matched.add(v)
                    }
                    discover(v, f)
                }
            }

            discover(origin, func)

            return matched
        }
    }
}
