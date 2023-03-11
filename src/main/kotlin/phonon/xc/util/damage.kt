/**
 * Damage calculations
 */

package phonon.xc.util.damage

import kotlin.math.max
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.Attributable
import org.bukkit.enchantments.Enchantment
import phonon.xc.XC


/**
 * Damage type metadata for weapons. This is used
 * for custom damage handlers (e.g. vehicles) to adjust
 * damage based on type.
 */
public enum class DamageType {
    ANTI_TANK_RIFLE,      // dedicated anti tank rifle damage type
    ARMOR_PIERCING,       // armor piercing round
    ARMOR_PIERCING_SHELL, // armor piercing shell, e.g. tank ap shell
    BULLET,               // regular guns
    EXPLOSIVE,            // generic explosive like grenade
    EXPLOSIVE_BOMB,       // bomb from plane
    EXPLOSIVE_SHELL,      // e.g. artillery or tank vehicle type damage
    FIRE,                 // generic fire damage type
    FLAK,                 // flak (anti air) gun damage
    FLAMETHROWER,         // flamethrower damage type
    MELEE,                // melee weapons
    MOLOTOV,              // molotov weapons
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
                "EXPLOSIVE_BOMB" -> EXPLOSIVE_BOMB
                "EXPLOSIVE_SHELL" -> EXPLOSIVE_SHELL
                "FIRE" -> FIRE
                "FLAK" -> FLAK
                "FLAMETHROWER" -> FLAMETHROWER
                "MELEE" -> MELEE
                "MOLOTOV" -> MOLOTOV
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
 * 
 * TODO: make armor reduction factor a table of protection for damage
 * types, configured for players.
 */
public fun XC.damageAfterArmorAndResistance(
    baseDamage: Double,
    entity: LivingEntity,
    armorReductionFactor: Double,
    _resistReductionFactor: Double,
): Double {
    val xc = this

    // get total entity armor value
    // note: LivingEntity extends Attributable, so attribute should always exist
    val armor = entity.getAttribute(Attribute.GENERIC_ARMOR)?.getValue() ?: 0.0

    // get custom vehicle armor
    val vehicle = entity.getVehicle()
    val vehicleArmor = if ( vehicle !== null && vehicle.type == EntityType.ARMOR_STAND ) {
        xc.vehiclePassengerArmor[vehicle.uniqueId] ?: 0.0
    } else {
        0.0
    }
    
    // if applying vehicle armor, make minimum damage 0.0 (no damage)
    // to allow fully protected passengers. otherwise, clamp damage to 1.0
    val damage = if ( vehicleArmor > 0.0 ) {
        max(0.0, baseDamage - ((armor + vehicleArmor) * armorReductionFactor))
    } else {
        max(1.0, baseDamage - (armor * armorReductionFactor))
    }

    // potion resistance/increase damage
    // UNNEEDED: vanilla will already apply potion modifier to damage
    
    // double potionModifier = 1.0
    // PotionEffect dmgResistance = entity.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)
    // if ( dmgResistance != null ) {
    //     double magnitude = (double) dmgResistance.getAmplifier()
    //     potionModifier -= magnitude * resistReductionFactor
    // }

    return damage
}


/**
 * Calculate explosion damage after armor.
 * This is used after calculating explosion damage from
 * distance using baseExplosionDamage().
 * 
 * TODO: make armor reduction factor a table of protection for damage
 * types, configured for players.
 */
public fun XC.explosionDamageAfterArmor(
    baseDamage: Double,
    entity: LivingEntity,
    armorReductionFactor: Double,
    blastProtReductionFactor: Double,
): Double {
    val xc = this

    // get total entity armor value
    // note: LivingEntity extends Attributable, so attribute should always exist
    val armor = entity.getAttribute(Attribute.GENERIC_ARMOR)?.getValue() ?: 0.0

    // get custom vehicle armor
    val vehicle = entity.getVehicle()
    val vehicleArmor = if ( vehicle !== null && vehicle.type == EntityType.ARMOR_STAND ) {
        xc.vehiclePassengerArmor[vehicle.uniqueId] ?: 0.0
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

    // if applying vehicle armor, make minimum damage 0.0 (no damage)
    // to allow fully protected passengers. otherwise, clamp damage to 1.0
    val damage = if ( vehicleArmor > 0.0 ) {
        max(0.0, baseDamage - ((armor + vehicleArmor) * armorReductionFactor) - (totalBlastProtectionLevel * blastProtReductionFactor))
    } else {
        max(1.0, baseDamage - (armor * armorReductionFactor) - (totalBlastProtectionLevel * blastProtReductionFactor))
    }

    return damage
}