package com.mistysteps.app.stats

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A square from (5.0, 53.0) to (5.01, 53.01) as a delta-encoded ring (units of 1e-5 degree). */
const val SQUARE_RING = "[500000,5300000,1000,0,0,1000,-1000,0,0,-1000]"

fun indexJson(cities: List<Long>, format: Int = 1) =
    """{"format":$format,"province":"fr","dataDate":"2026-11-10","cities":[${cities.joinToString(",")}]}"""

fun hoodsJson(vararg hoods: Triple<String, Long, String>) = """{"hoods":[${
    hoods.joinToString(",") { (type, id, name) -> """{"type":"$type","id":$id,"name":"$name","kind":"suburb","polygons":[[$SQUARE_RING]]}""" }
}]}"""

fun streetsJson(vararg names: String) = """{"streets":[${
    names.joinToString(",") { """{"name":"$it","lines":[[500200,5300500,400,0]]}""" }
}]}"""

/** Writes [entries] (name to text) as a zip file at [file]. */
fun writeZip(file: File, entries: Map<String, String>) {
    ZipOutputStream(file.outputStream()).use { z ->
        for ((name, text) in entries) {
            z.putNextEntry(ZipEntry(name))
            z.write(text.toByteArray())
            z.closeEntry()
        }
    }
}
