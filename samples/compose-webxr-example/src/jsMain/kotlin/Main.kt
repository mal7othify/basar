import com.spatialkit.compose.SpatialView
import com.spatialkit.engine.SessionMode
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement

fun main() {
    window.onload = {
        console.log("Spatial-KMP WebXR Example starting...")

        // Note: SpatialView is a Compose @Composable
        // For now, just log that the example is loaded
        // Full integration requires Compose for Web setup

        document.getElementById("status")?.textContent = "WebXR Example Loaded"
        console.log("DSL available: cube, sphere, cylinder")
        console.log("Engine: WebXR + Three.js")
        console.log("To use: Set up Compose for Web context")
    }
}
