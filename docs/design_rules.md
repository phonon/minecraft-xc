Components classes should not directly reference any data in another 
component class. Components should be pure data with zero knowledge
of another component. Examples of where this comes up:

### On despawn, want to drop a Fuel item, how to find world location?
We need two components:
- `FuelComponent` (contains fuel amount and item type)
- `TransformComponent` (contains world position state)

We need some `delete` handler. While it's tempting and convenient to
attach an `onDespawn` handler into `FuelComponent` which contains a
vehicle, then use the vehicle to reference the `TransformComponent`:

```kotlin
TransformComponent(val x: Double, val y: Double, val z: Double)

FuelComponent(
    var current: Int,
    var item: ItemStack,
) {
    fun onDespawn(vehicle: VehicleElement) {
        if ( vehicle.has<TransformComponent>() ) {
            val transform = vehicle.component<TransformComponent>()
            dropItem(transform.location, this.item)
        }
    }
}
```
Bad practice, avoid this. Do not have any cross-component coupling
within components. Instead, move all this into system functions.
System functions will become more complicated, but at least we can remove
vehicle coupling logic from components and leave them as pure data. 