/**
 * Damage calculations
 */

package phonon.xc.util.damage

import kotlin.math.max
import org.bukkit.entity.LivingEntity
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.Attributable
import org.bukkit.enchantments.Enchantment


/**
 * Damage type metadata for weapons. This is used
 * for custom damage handlers (e.g. vehicles) to adjust
 * damage based on type.
 */
public enum class DamageType {
    ANTI_TANK_RIFLE,      // dedicated anti tank rifle damage type
    ARMOR_PIERCING,       // armor piercing round, e.g. anti-tank rifle
    ARMOR_PIERCING_SHELL, // armor piercing shell, e.g. tank ap shell
    BULLET,               // regular guns
    EXPLOSIVE,            // generic explosive like grenade
    EXPLOSIVE_SHELL,      // e.g. artillery or tank vehicle type damage
    FIRE,                 // fire, like flamethrower
    MELEE,                // melee weapons
    UNKNOWN,              // placeholder or none specified
    ; // end enums

    companion object {
        /**
         * Match name to damage type. Case-insensitive.
         * If none found, will return null.
         */
        public fun match(name: String): DamageType? {
            return when (name.uppercase()) {
                "ANTI_TANK_RIFLE" -> ANTI_TANK_RIFLE
                "ARMOR_PIERCING" -> ARMOR_PIERCING
                "ARMOR_PIERCING_SHELL" -> ARMOR_PIERCING_SHELL
                "BULLET" -> BULLET
                "EXPLOSIVE" -> EXPLOSIVE
                "EXPLOSIVE_SHELL" -> EXPLOSIVE_SHELL
                "FIRE" -> FIRE
                "MELEE" -> MELEE
                "UNKNOWN" -> UNKNOWN
                else -> null
            }
        }
    }
} 


/**
 * Return entity damage after armor and damage resistance calculation.
 * This only factors in armor points and potion damage resistance.
 * This does not include armor toughness or armor enchant protection.
 * This also does not include location-dependent damage (like headshots).
 */
public fun damageAfterArmorAndResistance(
    baseDamage: Double,
    entity: LivingEntity,
    armorReductionFactor: Double,
    resistReductionFactor: Double,
): Double {
    // get total entity armor value
    val armor = if ( entity is Attributable ) {
        entity.getAttribute(Attribute.GENERIC_ARMOR)?.getValue() ?: 0.0
    } else {
        0.0
    }

    // potion resistance/increase damage
    // UNNEEDED: vanilla will already apply potion modifier to damage
    
    // double potionModifier = 1.0
    // PotionEffect dmgResistance = entity.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)
    // if ( dmgResistance != null ) {
    //     double magnitude = (double) dmgResistance.getAmplifier()
    //     potionModifier -= magnitude * resistReductionFactor
    // }

    return max(1.0, baseDamage - (armor * armorReductionFactor))
}


/**
 * Calculate explosion damage after armor.
 * This is used after calculating explosion damage from
 * distance using baseExplosionDamage().
 */
public fun explosionDamageAfterArmor(
    baseDamage: Double,
    entity: LivingEntity,
    armorReductionFactor: Double,
    blastProtReductionFactor: Double,
): Double {
    // get total entity armor value
    val armor = if ( entity is Attributable ) {
        entity.getAttribute(Attribute.GENERIC_ARMOR)?.getValue() ?: 0.0
    } else {
        0.0
    }

    var totalBlastProtectionLevel = 0.0

    val equipment = entity.getEquipment()
    if ( equipment != null ) {
        equipment.getHelmet()?.getItemMeta()?.let { it -> totalBlastProtectionLevel += it.getEnchantLevel(Enchantment.PROTECTION_EXPLOSIONS).toDouble() }
        equipment.getChestplate()?.getItemMeta()?.let { it -> totalBlastProtectionLevel += it.getEnchantLevel(Enchantment.PROTECTION_EXPLOSIONS).toDouble() }
        equipment.getLeggings()?.getItemMeta()?.let { it -> totalBlastProtectionLevel += it.getEnchantLevel(Enchantment.PROTECTION_EXPLOSIONS).toDouble() }
        equipment.getBoots()?.getItemMeta()?.let { it -> totalBlastProtectionLevel += it.getEnchantLevel(Enchantment.PROTECTION_EXPLOSIONS).toDouble() }
    }

    return max(1.0, baseDamage - (armor * armorReductionFactor) - (totalBlastProtectionLevel * blastProtReductionFactor))
}