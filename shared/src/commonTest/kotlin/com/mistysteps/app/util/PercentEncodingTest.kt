package com.mistysteps.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {
    @Test fun keepsUnreservedCharacters() = assertEquals("Abc-1_2.3~!'()*", percentEncode("Abc-1_2.3~!'()*"))

    @Test fun encodesSpacesAndSymbols() = assertEquals("Misty%20Steps%20feedback%26more%3D1", percentEncode("Misty Steps feedback&more=1"))

    @Test fun encodesNewlinesAndTheDash() = assertEquals("a%0A%0A%E2%80%94%0Ab", percentEncode("a\n\n\u2014\nb"))

    @Test fun encodesNonAscii() = assertEquals("%C3%A9%F0%9F%90%B1", percentEncode("\u00e9\uD83D\uDC31"))
}
