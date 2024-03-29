"""
EACH TIME COMPONENT IS ADDED, ADD THE ENUM MAPPING TO
`components` MAP BELOW.

Common list of all components for code generation.
"""

# Mappings from ENUM => ComponentType
components = {
    "AIRPLANE": "AirplaneComponent",
    "AMMO": "AmmoComponent",
    "AMMO_FIRE_WHEN_LOADED": "AmmoFireWhenLoadedComponent",
    "FUEL": "FuelComponent",
    "GRAVITY": "GravityComponent",
    "GUN_BARREL": "GunBarrelComponent",
    "GUN_TURRET": "GunTurretComponent",
    "HEALTH": "HealthComponent",
    "LAND_MOVEMENT_CONTROLS": "LandMovementControlsComponent",
    "SHIP_MOVEMENT_CONTROLS": "ShipMovementControlsComponent",
    "MODEL": "ModelComponent",
    "MODEL_GROUP": "ModelGroupComponent",
    "PARTICLES": "ParticlesComponent",
    "SEATS": "SeatsComponent",
    "SEATS_RAYCAST": "SeatsRaycastComponent",
    "SMOKE_PARTICLES": "SmokeParticlesComponent",
    "TRANSFORM": "TransformComponent",
}

from dataclasses import dataclass
from re import sub

def to_camelcase(s):
    s = sub(r"(_|-)+", " ", s).title().replace(" ", "").replace("*","")
    return "".join([s[0].lower(), s[1:]])

@dataclass
class ComponentType():
    """Wrapper for component type data for template with more field
    names:
    - .enum: enum type name (in capital snake case, e.g. LAND_MOVEMENT_CONTROLS)
    - .classname: component class type name (e.g. LandMovementControlsComponent)
    - .storage: component storage field archetype, prototype in camelCase (e.g. landMovementControls)
    - .filename: component class file, camelCase.kt (e.g. landMovementControls.kt)
    - .config_name: field name for toml config in snake case (e.g. land_movement_controls)
    """
    enum: str
    classname: str
    storage: str
    filename: str
    config_name: str

    def __init__(self, enum, classname):
        self.enum = enum
        self.classname = classname
        self.storage = to_camelcase(enum)
        self.filename = self.storage + ".kt"
        self.config_name = enum.lower()

def get_components() -> list[ComponentType]:
    """Return list of ComponentType objects for all components."""
    return [ComponentType(enum, component) for enum, component in components.items()]
