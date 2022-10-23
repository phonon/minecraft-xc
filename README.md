# XV: Vehicles extension to XC


# Build
Requirements:
- Java JDK 17 (current plugin target java version)

Build for paper 1.16.5:
```
./gradlew build -P 1.16
```

Build for paper 1.18.2:
```
./gradlew build -P 1.18
```

Built `.jar` will appear in `build/libs/*.jar`.


# Archetype Codegen
Vehicle ECS relies on auto-generating tuple types, and vehicle
component enum generation/hard-coding in files:
```
GENERATED:
src/main/kotlin/phonon/xv/core/archetype.kt
src/main/kotlin/phonon/xv/core/iterator.kt
src/main/kotlin/phonon/xv/core/prototype.kt
```
This uses python templating script to output these files.
Whenever a component is adding, these need to be re-built.

Installation (using python virtual env):
```
python -m venv venv
```

```
pip install -r requirements.txt
```

Usage:
1. When modifying components, add enum mappings in `scripts/archetype_gen.py`

2. Generate new kotlin source files with
```
python scripts/archetype_gen.py
```