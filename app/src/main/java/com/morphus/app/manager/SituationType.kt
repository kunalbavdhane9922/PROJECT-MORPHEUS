package com.morphus.app.manager

/**
 * Predefined emergency situation types for the custom SOS message mode.
 * Triggered from the calculator's hidden emergency mode (long-press AC).
 */
enum class SituationType {
    IN_CAB,
    FOLLOWED,
    UNSAFE_LOCATION,
    MEDICAL,
    MISBEHAVIOR,
    ABUSE,
    GENERAL
}
