package phonon.xv.common

import java.util.logging.Logger

/**
 * Different style of controls for vehicles.
 */
public enum class ControlStyle {
    // no controls
    NONE,
    // controls are based on player mouse movement (e.g. player view direction)
    MOUSE,
    // controls are based on WASD key presses
    WASD,
    ;

    companion object {
        /**
         * Try to get the control type enum from a string. Return null if
         * the string does not match any of the types.
         */
        public fun tryFromString(s: String): ControlStyle? {
            return when (s.uppercase()) {
                "NONE" -> NONE
                "MOUSE" -> MOUSE
                "WASD" -> WASD
                else -> null
            }
        }

        /**
         * Try to get the control type enum from a string. Return NONE if string
         * is invalid and does not match any type
         */
        public fun fromStringOrNone(s: String, logger: Logger? = null): ControlStyle {
            return when (s.uppercase()) {
                "NONE" -> NONE
                "MOUSE" -> MOUSE
                "WASD" -> WASD
                else -> {
                    logger?.severe("Invalid ControlStyle: $s, defaulting to NONE")
                    NONE
                }
            }
        }
    }   
}