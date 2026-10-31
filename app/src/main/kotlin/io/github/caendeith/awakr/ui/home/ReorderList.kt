package io.github.caendeith.awakr.ui.home

/**
 * Moves the item at [from] to [to], shifting the rest, without touching the platform.
 *
 * Pulled out of the drag handler so the ordering rules (clamping, no data loss) can be tested
 * without a device. Indices outside the list are clamped instead of throwing.
 */
internal fun <T> List<T>.movedTo(from: Int, to: Int): List<T> {
    if (isEmpty()) return this
    val source = from.coerceIn(0, lastIndex)
    val target = to.coerceIn(0, lastIndex)
    if (source == target) return this
    return toMutableList().apply { add(target, removeAt(source)) }
}
