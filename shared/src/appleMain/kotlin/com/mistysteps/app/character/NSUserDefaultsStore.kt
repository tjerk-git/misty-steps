package com.mistysteps.app.character

import platform.Foundation.NSUserDefaults

/** [KeyValueStore] on iOS's NSUserDefaults. */
class NSUserDefaultsStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}
