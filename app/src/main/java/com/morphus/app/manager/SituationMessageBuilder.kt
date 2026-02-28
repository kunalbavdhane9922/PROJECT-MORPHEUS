package com.morphus.app.manager

/**
 * Builds human-readable emergency SMS messages for each [SituationType].
 * Each message includes the situation description and a location link.
 */
object SituationMessageBuilder {

    fun build(type: SituationType, location: String): String {
        return when (type) {
            SituationType.IN_CAB ->
                "\uD83D\uDE95 I am in a cab and feel unsafe.\nLocation: $location"

            SituationType.FOLLOWED ->
                "⚠ Someone may be following me.\nLocation: $location"

            SituationType.UNSAFE_LOCATION ->
                "\uD83D\uDCCD I feel unsafe at this location.\nLocation: $location"

            SituationType.MEDICAL ->
                "\uD83E\uDE7A Medical emergency. Need help.\nLocation: $location"

            SituationType.MISBEHAVIOR ->
                "\uD83D\uDEAB Facing misbehavior. Need help.\nLocation: $location"

            SituationType.ABUSE ->
                "\uD83D\uDD34 Facing abuse. Immediate help needed.\nLocation: $location"

            SituationType.GENERAL ->
                "\uD83D\uDEA8 SOS ALERT.\nLocation: $location"
        }
    }
}
