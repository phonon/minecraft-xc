# XC: minecraft gun combat system

# Build
Requirements:
- Java JDK 17 (1.18.2 plugin target java version)
- Java JDK 16 (1.16.5 plugin target java version)

Only supports minecraft 1.16.5 and 1.18.2. Building compiles a `.jar`
specific to that minecraft version. Server also requires paper as 
this uses the paper api.

## Source structure:
```
xc/
 ├─ lib/                - Other .jar required, put spigot 1.16.5 in here
 └─ src/                - Plugin source
     ├─ main/           - Main plugin source
     ├─ nms/            - Minecraft version specific source
     |   ├─ v1_16_R3/   - 1.16.5
     |   └─ v1_18_R2/   - 1.18.2
     └─ test/           - Unit tests
```

### Minecraft 1.16.5

```
./gradlew build -P 1.16
```

### Minecraft 1.18.2:
```
./gradlew build -P 1.18
```

Built `.jar` will appear in `build/libs/*.jar`.


# Notes on Guns
*TODO: move this into docs*

## Gun Sway Settings
Gun projectile random direction is based on player movement speed,
calculated by an async background thread that calculates speed as a
moving average of instantaneous speed = difference in player position 
on each update tick divided by update tick time. The speed is calculated
in `blocks/s`. Internally it is also clamped to `[0, 20]` so that speed
does not become massive when players teleport to a new location.
Sway is directly multiplied against this calculated moving average speed.
Reference table of expected speed is below (<https://minecraft.fandom.com/wiki/Sprinting>).

| Movement    | Speed [blocks/s] |
| ----------- | ---------------- |
| Crouch      |  1.31            |
| Walk        |  4.32            |
| Sprint      |  5.61            |
| Jump-sprint |  7.13            |
| Max horse   | 14.57            |