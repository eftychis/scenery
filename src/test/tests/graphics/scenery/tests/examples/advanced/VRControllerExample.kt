package graphics.scenery.tests.examples.advanced

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.utils.Numerics
import org.junit.Test

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class VRControllerExample : SceneryBase(VRControllerExample::class.java.simpleName,
    windowWidth = 1920, windowHeight = 1200) {
    private var hmd: OpenVRHMD? = null

    override fun init() {
        hmd = OpenVRHMD(useCompositor = true)

        if(hmd == null || hmd?.initializedAndWorking() == false) {
            logger.error("This demo is intended to show the use of OpenVR controllers, but no OpenVR-compatible HMD could be initialized.")
            System.exit(1)
        }

        hub.add(SceneryElement.HMDInput, hmd!!)

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera(hmd)
        cam.position = GLVector(0.0f, 0.0f, 0.0f)

        cam.perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
        cam.active = true

        scene.addChild(cam)

        val b = (0..10).map {
            val obj = Box()
            obj.position = GLVector(2.0f, -4.0f + (it + 1) * 2.0f, 2.0f)
            scene.addChild(obj)
            obj
        }

        val controllers = (0..1).map { Mesh() }
        controllers.forEachIndexed { i, controller ->
            controller.name = "default"

            controller.update = {
                hmd!!.getPose(TrackedDeviceType.Controller).getOrNull(i)?.let {
                    if (controller.name == "default") {
                        hmd!!.loadModelForMesh(it.name, controller)
                    }

                    controller.model.setIdentity()
                    controller.model.translate(cam.position)
                    controller.model.mult(it.pose)
                    controller.needsUpdate = false
                    controller.needsUpdateWorld = true
                }
            }

            scene.addChild(controller)
        }

        (0..10).map {
            val light = PointLight()
            light.emissionColor = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
            light.position = Numerics.randomVectorFromRange(3, -5.0f, 5.0f)
            light.intensity = 100.0f
            light.quadratic = 0.001f

            light
        }.forEach { scene.addChild(it) }

        val hullbox = Box(GLVector(20.0f, 20.0f, 20.0f), insideNormals = true)
        val hullboxMaterial = Material()
        hullboxMaterial.ambient = GLVector(0.6f, 0.6f, 0.6f)
        hullboxMaterial.diffuse = GLVector(0.4f, 0.4f, 0.4f)
        hullboxMaterial.specular = GLVector(0.0f, 0.0f, 0.0f)
        hullboxMaterial.doubleSided = true
        hullbox.material = hullboxMaterial

        scene.addChild(hullbox)
    }

    @Test override fun main() {
        super.main()
    }

}
