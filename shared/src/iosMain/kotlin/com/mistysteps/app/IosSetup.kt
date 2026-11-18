package com.mistysteps.app

import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.NSUserDefaultsStore
import com.mistysteps.app.pin.PinSprites
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun bundleLines(name: String): List<String> {
    val path = NSBundle.mainBundle.pathForResource(name, "txt") ?: error("$name.txt is not in the app bundle")
    val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("cannot read $name.txt")
    return text.lines()
}

/** Call once when the iOS app starts: loads the character and pin sprite sheets from the app bundle. */
fun setUpSharedIos() {
    Characters.init(bundleLines("characters"), NSUserDefaultsStore())
    PinSprites.init(bundleLines("pins"))
}
