package com.mistysteps.app.ui

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The shared Compose UI as a UIKit view controller, for the iOS app to host. */
fun sharedHelloViewController(): UIViewController = ComposeUIViewController { SharedHello() }

/** The consent page (anonymous open count) as a full-screen view controller; [onAnswer] gets the person's choice. */
fun consentViewController(onAnswer: (Boolean) -> Unit): UIViewController =
    ComposeUIViewController { MistyTheme { ConsentScreen(onAnswer) } }
