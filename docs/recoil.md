# Recoil System

Recoil system has ramp and recovery times. This is a value from `[0, 1]`
that multiplies a gun's max recoil rates. Guns have a recoil ramp rate
for single/burst and auto firing modes. Recoil recovery rate is a global
setting (so players dont benefit from trying to swap guns).

```
1 _  _  _  _   _______________  _  _  _  _
              /               \
             /                 \
            /                   \
0 _________/                     \________0
           ^                 ^
           |                 |
         starts             stops 
         shooting          shooting
         (ramp)            (recovery)
```

Recoil recovery only checks if player is not burst firing or auto firing.
Single shots are ignored and recovery will still occur in the same
tick as a single shot (for code simplicity).

Horizontal and vertical recoil are handled separately:
-   Vertical recoil is always makes gun move upwards,
    with the player recoil multiplier.
-   Horizontal recoil is ramped but random within
    a `[-r, +r]` range, where `r` is the horizontal recoil
    multiplied by current player recoil multiplier.

