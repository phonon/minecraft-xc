package phonon.xv.common

/**
 * User controls for a vehicle.
 */
public data class UserInput(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val shift: Boolean = false,
) {

}