package io.github.caendeith.awakr.ui.sound

import io.github.caendeith.awakr.core.audio.BuiltinSound
import io.github.caendeith.awakr.core.audio.BuiltinSounds
import io.github.caendeith.awakr.core.audio.RecordedSound
import io.github.caendeith.awakr.core.audio.SoundPack

/** Chip selection above the list: one pack at a time, or everything. */
sealed interface SoundFilter {
    data object All : SoundFilter
    data class Pack(val pack: SoundPack) : SoundFilter
    data object Recorded : SoundFilter
}

/** One titled block of the picker. */
sealed interface SoundSection {
    data class Builtins(val pack: SoundPack, val sounds: List<BuiltinSound>) : SoundSection
    data class Recorded(val sounds: List<RecordedSound>) : SoundSection
}

/**
 * Pure layout rules of the sound picker: which chips exist, which sections are visible for a filter
 * and whether the stored id marks a row as selected. No Android types, so it is unit tested.
 */
object SoundPickerModel {

    /** Chips in display order: everything, the built-in packs, then the user's own sounds. */
    val filters: List<SoundFilter> =
        listOf<SoundFilter>(SoundFilter.All) +
            SoundPack.entries.map { SoundFilter.Pack(it) } +
            SoundFilter.Recorded

    /** Sections for one filter; the "Recorded" section survives even when it is empty (empty state). */
    fun sections(recorded: List<RecordedSound>, filter: SoundFilter): List<SoundSection> {
        val sorted = sortRecorded(recorded)
        return when (filter) {
            SoundFilter.All -> {
                val packs = SoundPack.entries
                    .map { pack ->
                        SoundSection.Builtins(pack, BuiltinSounds.inPack(pack))
                    }
                    .filter { it.sounds.isNotEmpty() }
                if (sorted.isEmpty()) packs else packs + SoundSection.Recorded(sorted)
            }

            is SoundFilter.Pack ->
                listOf(SoundSection.Builtins(filter.pack, BuiltinSounds.inPack(filter.pack)))
                    .filter { it.sounds.isNotEmpty() }

            SoundFilter.Recorded -> listOf(SoundSection.Recorded(sorted))
        }
    }

    /** Newest first, matching what [io.github.caendeith.awakr.core.audio.SoundFiles.listIn] returns. */
    fun sortRecorded(recorded: List<RecordedSound>): List<RecordedSound> =
        recorded.sortedWith(
            compareByDescending<RecordedSound> { it.file.lastModified() }.thenBy { it.file.name },
        )

    /** The "Default sound" row is selected when the alarm inherits the global sound. */
    fun isDefaultSelected(currentSoundId: String?): Boolean = currentSoundId.isNullOrBlank()

    fun isSelected(currentSoundId: String?, soundId: String): Boolean = currentSoundId == soundId

    /** Every choosable id currently on screen, in display order. */
    fun ids(sections: List<SoundSection>): List<String> = sections.flatMap { section ->
        when (section) {
            is SoundSection.Builtins -> section.sounds.map { BuiltinSounds.idOf(it) }
            is SoundSection.Recorded -> section.sounds.map { it.soundId }
        }
    }

    /** Exactly one row may be selected; the picker relies on this staying true. */
    fun selectionCount(sections: List<SoundSection>, currentSoundId: String?): Int =
        ids(sections).count { isSelected(currentSoundId, it) }
}
