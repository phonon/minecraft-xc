# XC: Minecraft combat

# Build
Requirements:
- Java JDK 16 (current plugin target java version)

Only supports minecraft 1.16.5 and 1.18.2. Building compiles a `.jar`
specific to that minecraft version. Server also requires paper as 
this uses the paper api.

## Source structure:
```
minecraft-xc/
 ├─ docs/               - Documentation
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
