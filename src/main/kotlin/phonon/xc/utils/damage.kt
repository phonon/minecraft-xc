/**
 * Damage calculations
 */

package phonon.xc.utils.damage

import kotlin.math.max
import org.bukkit.entity.LivingEntity
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.Attributable
import org.bukkit.enchantments.Enchantment


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

    return max(0.0, baseDamage - (armor * armorReductionFactor))
}


/**
 * Return damage from explosion based on distance, armor,
 * and blast protection enchant level.
 * 
 * Explosion damage done by a "hat" function:
 *          ___________  baseDamage
 *         /           \
 *     ___/             \____ 0
 *          <--> c <-->
 *                 radius
 * Within center radius, baseDamage is applied.
 * Outside of radius, linearly falloff factor applied, clamped to 0.
 */
public fun explosionDamageAfterArmor(
    baseDamage: Double,
    distance: Double,
    radius: Double,
    falloff: Double,
    entity: LivingEntity,
    armorReductionFactor: Double,
    blastProtReductionFactor: Double,
): Double {
    val distanceBeyondRadius = max(0.0, distance - radius)
    val damage = max(0.0, baseDamage - (distanceBeyondRadius * falloff))

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

    return max(0.0, damage - (armor * armorReductionFactor) - (totalBlastProtectionLevel * blastProtReductionFactor))
}