# Custom Item System

Use specific custom model data to map to specific item categories.
**Don't allow material type as item parameter**, because this introduces
more difficult checks from HashMaps linking `(Material, Id) => Item`.
Instead, use fixed material for each item category

For materials, use items that are unstackable and don't have useful ingame 
mechanics. For simplicity, plugin will not do any manual item unstacking.

Available types for custom items are thus:

**Best**:
-   `WARPED_FUNGUS_ON_A_STICK`
-   `LEATHER_HORSE_ARMOR`
-   `IRON_HORSE_ARMOR`
-   `GOLDEN_HORSE_ARMOR`
-   `DIAMOND_HORSE_ARMOR`

**Lesser**:
-   `CARROT_ON_A_STICK`: Okay, but can cause pigs to follow. Okay if
    mob ai is disabled.
-   `SHEARS`: Okay, but is a tool,..
-   `MUSIC_DISC_*`: There are weird duplication possibilities with this...
    So try to avoid.


**Default mappings**:
- Guns => `WARPED_FUNGUS_ON_A_STICK`
- Melee => `IRON_HORSE_ARMOR`
- Misc / throwables => `GOLDEN_HORSE_ARMOR`
- Ammo => `LEATHER_HORSE_ARMOR`
- Hats => `LEATHER_HORSE_ARMOR`
- Vehicles => `DIAMOND_HORSE_ARMOR`


# Ammo
Ammo only category that allows configurable material type. This is okay since
ammo is not an item frequently used. And ammo must be configurable to be
stackable or unstackable.


# Custom model ID mapping to gun type
All gun custom model ids (regular, reloading, iron sights, etc.) are mapped to 
the same gun object in a flat Array:
```
guns: Array<Gun?> = [null; MAX_ID]

// load some gun g
g = Gun.from(config)

// map all variants to same gun object
guns[g.hold_model_id] = g
guns[g.reload_model_id] = g
guns[g.ironsights_model_id] = g
```

The config load must validate that guns do not overwrite each other,
and throw errors for invalid guns that overwrite existing id slots.

# Custom model ID mapping to other item types

## Melee
Use direct mapping `customModelId => meleeId`