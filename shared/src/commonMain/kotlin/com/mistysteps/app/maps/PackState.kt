package com.mistysteps.app.maps

enum class Reason { NETWORK, CHECKSUM, NO_SPACE }

sealed class PackState {
    object NotDownloaded : PackState()
    class Downloading(val bytes: Long, val total: Long) : PackState()
    class Ready(val year: Int) : PackState()
    class UpdateAvailable(val installedYear: Int, val newYear: Int) : PackState()
    class Failed(val reason: Reason) : PackState()
}
