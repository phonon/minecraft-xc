# Performance Optimization

Use spark:
https://www.spigotmc.org/resources/spark.57242/


## Current issues
The performance can be divided into two categories:
1.  Projectile + collision optimization
2.  Controls optimization

Currently user controls is the biggest component.

The issue is currently due to adjusting `ItemMeta`.
Bukkit's wrappers for `ItemMeta` are very inefficient.
`itemStack.getItemMeta()` always clones item metadata:
https://hub.spigotmc.org/stash/projects/SPIGOT/repos/bukkit/browse/src/main/java/org/bukkit/inventory/ItemStack.java#548

This adds up since the plugin views item metadata and persistent
data containers frequently.

Current solution is just to use raw NMS to read items whenever possible.
And cache `ItemMeta` when possible.