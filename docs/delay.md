# Firing Delay System

## Goals
- Players should be able to carry multiple guns in the hotbar
- Delay system must punish fast swapping between guns
- Delay system must support benefical fast swapping from slow to fast gun,
  e.g. rifle to pistol, but punish trying to swap between slow guns,
  e.g. rifle to shotgun.

## Delay System

```
    After firing gun:
    
       Fire       Delay
    ----x-----------|-------------------> time


    After swapping to pistol:

       Fire       Delay Pistol
    ----x-----------|-----|-------------> time

    
    After swapping to rifle:

       Fire       Delay         Rifle
    ----x-----------|-------------|-----> time


    After swapping to beneficial swap delay pistol (delay < 0):

       Fire       Delay
    ----x-------|---|-------------------> time
              Pistol
```

Therefore we store a delay as two values:
```
ShootDelay {
    timestampShootDelay: Long, // = fire time + gun's delay
    timestampCanShoot: Long,   // = actual time player can shoot again
}
```

Immediately after shooting we create an object:
```
tShoot = Time.now()
dtGunDelay = gun.delay()

delay = ShootDelay {
    timestampShootDelay: tShoot + dtGunDelay,
    timestampCanShoot: tShoot + dtGunDelay,
}
```

Initially these are set to the same value. After trying to swap a gun,
we have:
```
newDelay = ShootDelay {
    timestampShootDelay: delay.timestampShootDelay,
    timestampCanShoot: delay.timestampShootDelay + newGun.swapDelay,
}
```
