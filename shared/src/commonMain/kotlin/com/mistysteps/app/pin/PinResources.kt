package com.mistysteps.app.pin

import com.mistysteps.app.resources.Res
import com.mistysteps.app.resources.pin_sit
import com.mistysteps.app.resources.pin_view
import com.mistysteps.app.resources.pin_shelter
import com.mistysteps.app.resources.pin_quiet
import com.mistysteps.app.resources.pin_smell
import com.mistysteps.app.resources.pin_birdsong
import com.mistysteps.app.resources.pin_crow
import com.mistysteps.app.resources.pin_cat
import com.mistysteps.app.resources.pin_ducks
import com.mistysteps.app.resources.pin_tree
import com.mistysteps.app.resources.pin_building
import com.mistysteps.app.resources.pin_path
import com.mistysteps.app.resources.pin_other
import com.mistysteps.app.resources.pin_group_rest
import com.mistysteps.app.resources.pin_group_senses
import com.mistysteps.app.resources.pin_group_animals
import com.mistysteps.app.resources.pin_group_finds
import org.jetbrains.compose.resources.StringResource

/** The texts of the pin groups and labels as Compose string resources (Android code uses the R.string versions in PinStrings.kt). */
val PinGroup.titleResource: StringResource
    get() = when (this) {
        PinGroup.REST -> Res.string.pin_group_rest
        PinGroup.SENSES -> Res.string.pin_group_senses
        PinGroup.ANIMALS -> Res.string.pin_group_animals
        PinGroup.FINDS -> Res.string.pin_group_finds
    }

val PinLabel.textResource: StringResource
    get() = when (this) {
        PinLabel.SIT -> Res.string.pin_sit
        PinLabel.VIEW -> Res.string.pin_view
        PinLabel.SHELTER -> Res.string.pin_shelter
        PinLabel.QUIET -> Res.string.pin_quiet
        PinLabel.SMELL -> Res.string.pin_smell
        PinLabel.BIRDSONG -> Res.string.pin_birdsong
        PinLabel.CROW -> Res.string.pin_crow
        PinLabel.CAT -> Res.string.pin_cat
        PinLabel.DUCKS -> Res.string.pin_ducks
        PinLabel.TREE -> Res.string.pin_tree
        PinLabel.BUILDING -> Res.string.pin_building
        PinLabel.PATH -> Res.string.pin_path
        PinLabel.OTHER -> Res.string.pin_other
    }
