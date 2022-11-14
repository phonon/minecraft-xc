# General Plugin Coding Style

## Prefer Dependency Injection (aka pass XC around)
Use dependency injection for XC global state object, as in pass it around
to functions that need it. (Instead of using a global static XC state).

## Use Extension Functions on XC for things that need XC state
To manage dependency injection, this uses extensive amounts of
[Kotlin Extension functions](https://kotlinlang.org/docs/extensions.html),
e.g. most user controls systems are written in format
```kotlin
public fun XC.gunControls() {
    for ( req in this.gunShootRequests ) {
        ...
    }
}
```

This `XC.function` is an extension function on class `XC` and is written
as if it were a method on `XC`, so it has access to `this`. 
Reason for writing things as extension functions rather than directly
as a method on `XC` is just cleaner code separation. `XC` state object
class can be stored in package `phonon.xc` while gun controls (which need
`XC` state) can be stored in package `phonon.xc.gun.controls`. 

### When using Extension Functions on XC, always use `this.`
Extension functions on `XC` have access to its internal class variables
without needing to use this, as if the function were a method of `XC`.
This can create ambiguity if a local variable name shadows a `XC` class
variable. **So always use `this` for properties on XC inside extension
functions.**