package com.mistysteps.app.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The shared Compose UI as a UIKit view controller, for the iOS app to host. */
fun sharedHelloViewController(): UIViewController = ComposeUIViewController { SharedHello() }
