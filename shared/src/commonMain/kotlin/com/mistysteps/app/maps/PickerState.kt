package com.mistysteps.app.maps

/**
 * What is ticked in the province picker. Immutable: [toggle] gives the next state. [selected] starts as
 * [preselected], leaving out ids the catalog does not have.
 */
class PickerState(allOptions: List<PackEntry>, preselected: Set<String> = emptySet()) {
    val options: List<PackEntry> = allOptions.sortedBy { it.name.lowercase() }
    val selected: Set<String> = preselected.intersect(options.map { it.id }.toSet())

    val totalBytes: Long get() = options.filter { it.id in selected }.sumOf { it.totalBytes }

    fun toggle(id: String): PickerState = PickerState(options, if (id in selected) selected - id else selected + id)

    /** The ticked provinces that still need downloading: not installed and not already on their way. */
    fun toDownload(stateOf: (PackEntry) -> PackState): List<PackEntry> =
        options.filter { it.id in selected }.filter {
            when (stateOf(it)) {
                is PackState.Ready, is PackState.Downloading -> false
                else -> true
            }
        }
}
