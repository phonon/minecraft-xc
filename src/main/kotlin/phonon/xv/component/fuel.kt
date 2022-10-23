package phonon.xv.component

import phonon.xv.core.VehicleComponent

public data class FuelComponent(
    var current: Double,
    val max: Double,
): VehicleComponent {
    
    init {
        current = current.coerceIn(0.0, max)
    }
}