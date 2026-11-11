package de.kh.kalorientracker.core

import java.time.LocalTime

/** Eine Mahlzeit mit Uhrzeit-Fenster in Minuten ab Mitternacht [start, ende). */
data class MahlzeitSlot(val id: Int, val name: String, val startMin: Int, val endeMin: Int, val sortierung: Int) {
    init {
        require(startMin in 0..1439 && endeMin in 1..1440 && endeMin > startMin) { "Ungültiges Fenster für $name" }
    }
    fun enthaelt(minute: Int) = minute in startMin until endeMin
}

object Mahlzeiten {
    val STANDARD = listOf(
        MahlzeitSlot(1, "Frühstück", 4 * 60, 9 * 60, 1),
        MahlzeitSlot(2, "Snack 1", 9 * 60, 11 * 60, 2),
        MahlzeitSlot(3, "Mittagessen", 11 * 60, 13 * 60, 3),
        MahlzeitSlot(4, "Snack 2", 13 * 60, 16 * 60, 4),
        MahlzeitSlot(5, "Abendessen", 16 * 60, 20 * 60, 5),
        MahlzeitSlot(6, "Snack 3", 20 * 60, 24 * 60, 6),
    )

    /**
     * Welcher Slot passt zur Uhrzeit? Zeiten, die in keinem Fenster liegen
     * (Standard: 00:00–04:00), gehören zum letzten Slot des Tages.
     */
    fun slotFuer(zeit: LocalTime, slots: List<MahlzeitSlot> = STANDARD): MahlzeitSlot {
        require(slots.isNotEmpty())
        val minute = zeit.hour * 60 + zeit.minute
        return slots.firstOrNull { it.enthaelt(minute) } ?: slots.maxBy { it.sortierung }
    }

    fun fensterText(slot: MahlzeitSlot): String = "%s–%s".format(hhmm(slot.startMin), hhmm(slot.endeMin))

    private fun hhmm(min: Int): String = "%02d:%02d".format((min / 60) % 24, min % 60)
}
