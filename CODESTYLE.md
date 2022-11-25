# Plugin Coding Style

## General Aesthetics
- Use `camelCase` for kotlin file names and variables. this isnt java,
u can export single functions from files so `JavaClassName.java`
doesnt really make sense in kotlin
- Use 4 spaces for tabs
- Use `/** **/` comments for function or class block comments
- Add spacing after if, for, when operators
e.g. `if ( x == true )` instead of `if(x == true)`
e.g. `for ( x in 0 until 420 )` instead of `for(x in 0 until 420)`


## Prefer Dependency Injection (aka pass XC around)
Use dependency injection for XC global state object, as in pass it around
to functions that need it. (Instead of using a global static XC state).


## Use Extension Functions on XC for things that need XC state
To manage dependency injection, this uses extensive amounts of
[Kotlin Extension functions](https://kotlinlang.org/docs/extensions.html),
```kotlin
public fun XC.createExplosion(location: Location, damage: Double) {
    // check if region allows explosions using XC config
    // where "this" in this context is an "XC" class instance.
    if ( !this.canExplodeAt(location) ) {
        return
    }

    doExplosion(location, damage)
}
```

This `XC.function` is an extension function on class `XC` and is written
as if it were a method on `XC`, so it has access to `this`. 
Reason for writing things as extension functions rather than directly
as a method on `XC` is just cleaner code separation. `XC` state object
class can be stored in package `phonon.xc` while systems can be separated
into modules. E.g. gun controls (which need `XC` state) can be stored in
package `phonon.xc.gun.controls`, but still be written as if operating
on `XC` as `this`. 

### When using Extension Functions on XC, always use `this.`
Extension functions on `XC` have access to its internal class variables
without needing to use this, as if the function were a method of `XC`.
This can create ambiguity if a local variable name shadows a `XC` class
variable. **So always use `this` for properties on XC inside extension
functions.**

## Writing Systems
General structure of plugin is "system"-based:
```kotlin
class XC {
    val state: Storage

    fun update() {
        val timestamp = getCurrentTime()

        gunSystem(state)
        reloadSystem(state, timestamp)
        projectileSystem(state)
    }
}
```

The `XC` object is a global storage of state. Systems are written out in
order inside an `update()` function. Each system operates on `XC` state
and/or other resources (e.g. a `timestamp` during the update).

Systems are just functions written as an extension function on XC. 
A typical system can be located in its module package and is structured as:
```kotlin
package xc.gun

fun XC.gunSystem(
    // INCLUDE ALL STATE RESOURCES AS INPUTS:
    shootRequests: List<GunShootRequest>, 
    playerFiringState: HashMap<Player, ShootingState>,
) {
    for ( req in shootRequests ) {
        // use try/catch otherwise a single bad request will
        // cause an exception, which would be uncatched in outer
        // update and thus cause entire update to fail...
        // so "harden" the overall XC update schedule by always
        // including try/catch per request handling.
        // only avoid if the system can be GUARANTEED to never
        // reach any error state. 
        try {
            // unpacks request
            val (player, gun) = req
    
            playerFiringState[player] = ShootingState(gun)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
        }
    }

    // clear requests for next update
    this.shootRequests = ArrayList<GunShootRequest>
}
```

Inside the update schedule, this system would appear is
```kotlin
import xc.gun.gunSystem

class XC {
    // ...
    // state contained inside XC:
    var shootRequests: List<GunShootRequest>,
    var playerFiringState: HashMap<Player, ShootingState>,
    // ...

    fun update() {
        // ...
        gunSystem(shootRequests, playerFiringState)
        // ...
    }
}
```

This XC internal structure has following pros/cons:

### Pros
- `update()` gives a very clear list of sub systems, their ordering, and
their state resources used. There is no ambiguity on where updates
are occuring through a web of events handlers or "managers". All systems
are just functions.
- If we write out system resources explicity and whether they are read/write,
we will have easier time parallelizing our update with a task graph, if we
need to in the future.
- Using kotlin extension functions, we can split systems into different
packages to avoid cluttering the `XC` class.

### Cons
- Since each system still has access to full `XC` state, we can still
use resources not explicitly written as function args. We have to enforce
coding style that we explicitly list resources used, and that any other
access to `XC` is only to access immutable objects like `config`
(immutable during scope of the update).
- Fragile update. Since we don't do any outer try/catch around systems, 
a single system with an uncaught internal exception will kill the 
update. We have to manually internally add try/catch if there is ever
a chance of exception occuring.
- We need to remember to clear a system's request queue at the end
of a system. We are kind of passing in `XC` fields into its own method,
instead of using accessors on `this`. But we still need to do a dirty
`this.shootRequests = ArrayList<GunShootRequest>` as shown above,
to clear that queue at the end. This is ugly/dirty since we are doing
some `XC` mutation inside a system that can't be explicity determined
from the function args. Ideally we would want systems to have no
side effects on the `XC` state.
