package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.SceneryPanel
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * TexturedCubeExample, embedded in a JavaFX window
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class JavaFXTexturedCubeExample : SceneryBase("JavaFXTexturedCubeExample", windowWidth = 512, windowHeight = 512) {
    override fun init() {
        val latch = CountDownLatch(1)
        var imagePanel: SceneryPanel? = null

        PlatformImpl.startup { }

        Platform.runLater {
            val stage = Stage()
            stage.title = applicationName

            val stackPane = StackPane()
            stackPane.backgroundProperty()
                .set(Background(BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)))

            val pane = GridPane()
            val label = Label(applicationName)

            imagePanel = SceneryPanel(windowWidth, windowHeight)

            GridPane.setHgrow(imagePanel, Priority.ALWAYS)
            GridPane.setVgrow(imagePanel, Priority.ALWAYS)

            GridPane.setFillHeight(imagePanel, true)
            GridPane.setFillWidth(imagePanel, true)

            GridPane.setHgrow(label, Priority.ALWAYS)
            GridPane.setHalignment(label, HPos.CENTER)
            GridPane.setValignment(label, VPos.BOTTOM)

            label.maxWidthProperty().bind(pane.widthProperty())

            pane.style = """
            -fx-background-color: rgb(20, 255, 20);
            -fx-font-family: Consolas;
            -fx-font-weight: 400;
            -fx-font-size: 1.2em;
            -fx-text-fill: white;
            -fx-text-alignment: center;
            """
            label.style = """
            -fx-padding: 0.2em;
            -fx-text-fill: black;
            """

            label.textAlignment = TextAlignment.CENTER

            pane.add(imagePanel, 1, 1)
            pane.add(label, 1, 2)
            stackPane.children.addAll(pane)

            val scene = Scene(stackPane)
            stage.scene = scene
            stage.onCloseRequest = EventHandler {
                renderer?.shouldClose = true

                Platform.runLater { Platform.exit() }
            }

            stage.show()

            latch.countDown()
        }

        latch.await()

        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn = imagePanel)
        hub.add(SceneryElement.Renderer, renderer!!)

        val boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
            textures.put("diffuse", TexturedCubeExample::class.java.getResource("textures/helix.png").file)
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"

        with(box) {
            box.material = boxmaterial
            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 500.2f * (i + 1)
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
