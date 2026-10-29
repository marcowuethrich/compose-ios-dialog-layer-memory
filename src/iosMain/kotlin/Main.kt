@file:OptIn(BetaInteropApi::class, ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)

package repro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.window.Dialog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.coroutines.delay
import metaltextures.liveTextures
import metaltextures.startTrackingTextures
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIDevice
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScene
import platform.UIKit.UISceneConfiguration
import platform.UIKit.UISceneConnectionOptions
import platform.UIKit.UISceneSession
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneDelegateProtocol
import platform.UIKit.UIWindowSceneDelegateProtocolMeta
import platform.posix.exit

/**
 * Opens and closes a [Dialog] on a timeline and, at each mark, logs the Metal textures that are
 * still alive, then exits. The launch argument `serial` sets `parallelRendering = false`.
 */
fun main(args: Array<String>) {
    startTrackingTextures()
    memScoped {
        val argv = (arrayOf("DialogLayerRepro") + args).map { it.cstr.ptr }.toCValues()
        autoreleasepool {
            UIApplicationMain(args.size + 1, argv, null, NSStringFromClass(AppDelegate))
        }
    }
}

@Composable
fun App(mode: String) {
    var shown by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        BasicText("No dialog")
        if (shown) {
            Dialog(onDismissRequest = { shown = false }) {
                Box(Modifier.size(280.dp, 160.dp).background(Color.White), Alignment.Center) {
                    BasicText("Dialog")
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        println("REPRO $mode | ${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}")
        delay(5_000)
        mark(mode, "before the first dialog")
        repeat(2) { cycle ->
            shown = true
            delay(3_000)
            mark(mode, "dialog ${cycle + 1} open")
            shown = false
            delay(5_000)
            mark(mode, "dialog ${cycle + 1} closed 5 s ago")
        }
        delay(15_000)
        mark(mode, "dialog 2 closed 20 s ago")
        exit(0)
    }
}

private fun mark(mode: String, label: String) {
    println("REPRO $mode | $label | live Metal textures ${liveTextures()}")
}

class AppDelegate : UIResponder, UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    @OverrideInit
    constructor() : super()

    override fun application(
        application: UIApplication,
        configurationForConnectingSceneSession: UISceneSession,
        options: UISceneConnectionOptions,
    ): UISceneConfiguration = UISceneConfiguration(
        name = "Default",
        sessionRole = configurationForConnectingSceneSession.role,
    ).apply { delegateClass = SceneDelegate }
}

class SceneDelegate : UIResponder, UIWindowSceneDelegateProtocol {
    companion object : UIResponderMeta(), UIWindowSceneDelegateProtocolMeta

    @OverrideInit
    constructor() : super()

    private var sceneWindow: UIWindow? = null

    override fun window(): UIWindow? = sceneWindow

    override fun setWindow(window: UIWindow?) {
        sceneWindow = window
    }

    override fun scene(
        scene: UIScene,
        willConnectToSession: UISceneSession,
        options: UISceneConnectionOptions,
    ) {
        val serial = NSProcessInfo.processInfo.arguments.contains("serial")
        val mode = if (serial) "parallelRendering=false" else "parallelRendering=true"
        sceneWindow = UIWindow(windowScene = scene as UIWindowScene).apply {
            rootViewController = ComposeUIViewController(configure = { parallelRendering = !serial }) {
                App(mode)
            }
            makeKeyAndVisible()
        }
    }
}
