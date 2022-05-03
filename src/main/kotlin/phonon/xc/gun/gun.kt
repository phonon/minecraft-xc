
package phonon.xc.gun


/**
 * Common gun object used by all guns.
 * This is an immutable object. Should be constructed using
 * Gun.Builder() object.
 */
public data class Gun(
    // projectile velocity in blocks/tick => (20*vel) m/s
    // physical velocities of ~900 m/s would require vel ~ 45.0
    // but usually this is too fast ingame (makes bullets too hitscan-y)
    // so instead opt for lower velocity + lower gravity as default
    public val bulletVelocity: Float = 16.0f,

    // projectile gravity in blocks/tick^2 => (400*g) m/s^2
    // physical world gravity would be 0.025 to give 10 m/s^2
    // NOTE: THIS IS A POSITIVE NUMBER
    public val bulletGravity: Float = 0.025f,

    // max lifetime in ticks before despawning
    public val bulletLifetime: Int = 400, // ~20 seconds

    // max bullet distance in blocks before despawning
    public val bulletMaxDistance: Float = 128.0f, // = view distance of 8 chunks

    // bullet damage
    public val bulletDamage: Double = 4.0,
    public val bulletArmorReduction: Double = 0.5,
    public val bulletResistanceReduction: Double = 0.5,

    // explosion damage and radius and falloff (unused if no explosion)
    public val explosionDamage: Double = 8.0,
    public val explosionRadius: Double = 2.0,
    public val explosionFalloff: Double = 2.0, // damage/block falloff
    public val explosionArmorReduction: Double = 0.5, // damage/armor
    public val explosionBlastProtReduction: Double = 1.0, // damage/blast protection

    // handler on block hit
    public val hitBlockHandler: GunHitBlockHandler = noBlockHitHandler,

    // handler on entity hit
    public val hitEntityHandler: GunHitEntityHandler = entityDamageHitHandler,
) {

}
