package com.mistysteps.app

import com.mistysteps.app.character.Characters
import com.mistysteps.app.character.NSUserDefaultsStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** Call once when the iOS app starts: loads the character sheet from the app bundle. Safe to call again. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun setUpSharedIos() {
    val path = NSBundle.mainBundle.pathForResource("characters", "txt") ?: error("characters.txt is not in the app bundle")
    val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("cannot read characters.txt")
    Characters.init(text.lines(), NSUserDefaultsStore())
}
