package com.mistysteps.app.pin

import androidx.annotation.StringRes
import com.mistysteps.app.R

/** Android string resources for the pin groups and labels; the shared module only knows the keys. */
@get:StringRes
val PinGroup.titleRes: Int
    get() = when (this) {
        PinGroup.REST -> R.string.pin_group_rest
        PinGroup.SENSES -> R.string.pin_group_senses
        PinGroup.ANIMALS -> R.string.pin_group_animals
        PinGroup.FINDS -> R.string.pin_group_finds
    }

@get:StringRes
val PinLabel.textRes: Int
    get() = when (this) {
        PinLabel.SIT -> R.string.pin_sit
        PinLabel.VIEW -> R.string.pin_view
        PinLabel.SHELTER -> R.string.pin_shelter
        PinLabel.QUIET -> R.string.pin_quiet
        PinLabel.SMELL -> R.string.pin_smell
        PinLabel.BIRDSONG -> R.string.pin_birdsong
        PinLabel.CROW -> R.string.pin_crow
        PinLabel.CAT -> R.string.pin_cat
        PinLabel.DUCKS -> R.string.pin_ducks
        PinLabel.TREE -> R.string.pin_tree
        PinLabel.BUILDING -> R.string.pin_building
        PinLabel.PATH -> R.string.pin_path
        PinLabel.OTHER -> R.string.pin_other
    }
