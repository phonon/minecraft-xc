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


# Archetype and Component Codegen
Vehicle ECS relies on auto-generating tuple types, and vehicle
component enum generation/hard-coding in files:
```
GENERATED SOURCE FILES:
src/main/kotlin/phonon/xv/core/archetype.kt
src/main/kotlin/phonon/xv/core/iterator.kt
src/main/kotlin/phonon/xv/core/prototype.kt

GENERATED COMPONENT CONFIG EXAMPLES:
src/main/resource/example/_____.toml
```
This uses python templating script to output these files.
Whenever a component is adding, these need to be re-built.

## Usage:
*Note: run all these from root of the project*

Installation (using python virtual env):
```
python -m venv venv
```

```
pip install -r requirements.txt
```

Scripts:
```
scripts/
 ├─ templates/                 - Python jinja templates
 ├─ archetype_gen.py           - Generates kotlin source files
 ├─ components.py              - Component Enum -> Class name mappings
 └─ documentation_gen.py       - Generates component config examples
```

1. When modifying components, add enum mappings in `scripts/components.py`

2. Generate new kotlin source files with
```
python scripts/archetype_gen.py
```

3. Generate new component `.toml` config documentation files with command below:
```
python scripts/documentation_gen.py
```

## Component Config Documentation
The `documentation_gen.py` parses the component class in source `*.kt` files
and generates their `*.toml` config example. It assumes all constructor
parameters are configurable options, but there are custom tokens defined
inside `documentation_gen.py` to modify the documentation generation, look
inside to see details and look at `component.kt` source files as reference.


# Resourcepack
For built-in debug vehicles, this contains a test resourcepack contained
in the `resourcepack` folder. Copypaste that folder into your mineman
resource packs.