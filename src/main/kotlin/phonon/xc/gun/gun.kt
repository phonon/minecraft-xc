/**
 * Contain gun definition.
 */
package phonon.xc.gun


/**
 * Common gun object used by all guns.
 * This is an immutable object. When properties need to change,
 * create a new gun using kotlin data class `copy()` which can
 * alter certain properties while leaving others the same.
 */
public data class Gun(
    // gun item/visual properties
    public val itemName: String = "gun",
    public val itemLore: List<String>? = null,
    public val itemModelDefault: Int = 0,     // normal model (custom model data id)
    public val itemModelEmpty: Int = -1,      // when gun out of ammo
    public val itemModelReload: Int = -1,     // when gun is reloading
    public val itemModelIronsights: Int = -1, // when using iron sights
    
    // sounds
    public val soundShoot: String = "gun_shot",
    public val soundReload: String = "gun_reload",
    public val soundEmpty: String = "gun_empty",

    // reload [ms]
    public val reloadTime: Long = 1500,

    // semiauto fire rate [ms]
    public val fireRate: Long = 500,

    // automatic fire rate properties
    public val autoFire: Boolean = false, // automatic weapon
    public val autoFireRate: Int = 2,    // auto fire rate in ticks

    // ammo
    public val ammoId: Int = -1,
    public val ammoMax: Int = 10,
    public val ammoPerReload: Int = -1, // if -1, reload to max. otherwise: ammo + ammoPerReload

    // sway
    // TODO

    // recoil
    public val recoilHorizontal: Float = 0.1f,
    public val recoilVertical: Float = 0.2f,
    public val autoFireTimeBeforeRecoil: Long = 200,
    public val autoFireHorizontalRecoilRamp: Float = 0.05f, // recoil ramp rate in recoil / millisecond
    public val autoFireVerticalRecoilRamp: Float = 0.05f, // recoil ramp rate in recoil / millisecond

    // slowness while equiped (if > 0)
    public val slowness: Int = 0,
    
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
)
