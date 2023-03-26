/**
 * Custom armor stand with gravity override
 * 
 * https://www.spigotmc.org/threads/armorstand-velocity-doesnt-work-with-gravity.279410/
 */

package phonon.xv.util

//import net.minecraft.server.v1_18_R2.EntityArmorStand
//import net.minecraft.server.v1_18_R2.EnumMoveType
//import net.minecraft.server.v1_18_R2.Vector3f
//import net.minecraft.server.v1_18_R2.World

import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.MoverType
import net.minecraft.core.Rotations
import net.minecraft.world.level.Level

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_18_R2.CraftEquipmentSlot
import org.bukkit.craftbukkit.v1_18_R2.CraftServer
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftArmorStand
import org.bukkit.craftbukkit.v1_18_R2.persistence.CraftPersistentDataContainer
//import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import org.bukkit.World as BukkitWorld

private class InternalCustomEntityArmorstand(
    world: Level,
    d0: Double,
    d1: Double,
    d2: Double
): ArmorStand(world, d0, d1, d2) {

    init {
        this.setNoGravity(true)
    }

    // 1.16._: function is f
    protected override fun tickHeadTurn(f: Float, f1: Float): Float {
        if ( !isNoGravity() ) { // hasGravity() actually means hasNoGravity(), probably a mistake in deobfuscating.
            super.tickHeadTurn(f, f1)
        } else {
            move(MoverType.SELF, this.deltaMovement) // Give them some velocity anyways 3
        }

        return 0f
    }
}

public class CustomArmorStand(
    server: CraftServer,
    entity: ArmorStand,
    val craftArmorStand: CraftArmorStand
): CraftArmorStand(server, entity), org.bukkit.entity.ArmorStand {

    init {
        this.setCollidable(false)
        this.setRemoveWhenFarAway(false)
    }

    override public fun toString(): String {
        return "CraftArmorStand"
    }

    override public fun getType(): EntityType {
        return EntityType.ARMOR_STAND
    }

    override public fun getHandle(): ArmorStand {
        return super.getHandle() as ArmorStand
    }

    override public fun getItemInHand(): ItemStack {
        return getEquipment().getItemInMainHand()
    }

    override public fun setItemInHand(item: ItemStack?) {
        getEquipment().setItemInMainHand(item)
    }

    override public fun getBoots(): ItemStack {
        return getEquipment().getBoots()!!
    }

    override public fun setBoots(item: ItemStack?) {
        getEquipment().setBoots(item)
    }

    override public fun getLeggings(): ItemStack {
        return getEquipment().getLeggings()!!
    }

    override public fun setLeggings(item: ItemStack?) {
        getEquipment().setLeggings(item)
    }

    override public fun getChestplate(): ItemStack {
        return getEquipment().getChestplate()!!
    }

    override public fun setChestplate(item: ItemStack?) {
        getEquipment().setChestplate(item)
    }

    override public fun getHelmet(): ItemStack {
        return getEquipment().getHelmet()!!
    }

    override public fun setHelmet(item: ItemStack?) {
        getEquipment().setHelmet(item)
    }

    override public fun getBodyPose(): EulerAngle {
        return fromNMS(getHandle().bodyPose)
    }

    override public fun setBodyPose(pose: EulerAngle) {
        getHandle().setBodyPose(toNMS(pose))
    }

    override public fun getLeftArmPose(): EulerAngle {
        return fromNMS(getHandle().leftArmPose)
    }

    override public fun setLeftArmPose(pose: EulerAngle) {
        getHandle().setLeftArmPose(toNMS(pose))
    }

    override public fun getRightArmPose(): EulerAngle {
        return fromNMS(getHandle().rightArmPose)
    }

    override public fun setRightArmPose(pose: EulerAngle) {
        getHandle().setRightArmPose(toNMS(pose))
    }

    override public fun getLeftLegPose(): EulerAngle {
        return fromNMS(getHandle().leftLegPose)
    }

    override public fun setLeftLegPose(pose: EulerAngle) {
        getHandle().setLeftLegPose(toNMS(pose))
    }

    override public fun getRightLegPose(): EulerAngle {
        return fromNMS(getHandle().rightLegPose)
    }

    override public fun setRightLegPose(pose: EulerAngle) {
        getHandle().setRightLegPose(toNMS(pose))
    }

    override public fun getHeadPose(): EulerAngle {
        return fromNMS(getHandle().headPose)
    }

    override public fun setHeadPose(pose: EulerAngle) {
        getHandle().setHeadPose(toNMS(pose))
    }

    override public fun hasBasePlate(): Boolean {
        return !getHandle().isNoBasePlate()
    }

    override public fun setBasePlate(basePlate: Boolean) {
        getHandle().setNoBasePlate(!basePlate)
    }

    // idk how to do this
    // override public fun getDisabledSlots(): Set<EquipmentSlot> {
    //     return HashSet()
    // }

    // // idk how to implement this properly
    // override public fun isSlotDisabled(es: EquipmentSlot): Boolean {
    //     return false
    // }

    override public fun setGravity(gravity: Boolean) {
        super.setGravity(gravity)
        getHandle().noPhysics = true
        // Potential problem line above due to invert in naming
        // Armor stands are special
        // OVERRIDEN GET FUKT
        // getHandle().noclip = !gravity
    }

    override public fun isVisible(): Boolean {
        return !getHandle().isInvisible()
    }

    override public fun setVisible(visible: Boolean) {
        getHandle().setInvisible(!visible)
    }

    override public fun hasArms(): Boolean {
        return getHandle().isShowArms()
    }

    override public fun setArms(arms: Boolean) {
        getHandle().setShowArms(arms)
    }

    override public fun isSmall(): Boolean {
        return getHandle().isSmall()
    }

    override public fun setSmall(small: Boolean) {
        getHandle().setSmall(small)
    }

    override public fun isMarker(): Boolean {
        return getHandle().isMarker()
    }

    override public fun setMarker(marker: Boolean) {
        getHandle().setMarker(marker)
    }

    // Probe Xeth over what this did on 1.16.5, looking through code and cannot find a use case, probably related to
    // preventing putting a sword on the vehicle or something.
    // T. Rurik
    /*
    override public fun addEquipmentLock(equipmentSlot: EquipmentSlot, lockType: ArmorStand.LockType) {
        getHandle().disabledSlots = getHandle().disabledSlots or (1 shl CraftEquipmentSlot.getNMS(equipmentSlot).getFilterFlag() + lockType.ordinal * 8)
    }

    override public fun removeEquipmentLock(equipmentSlot: EquipmentSlot, lockType: ArmorStand.LockType) {
        getHandle().disabledSlots = getHandle().disabledSlots and ((1 shl CraftEquipmentSlot.getNMS(equipmentSlot).getFilterFlag() + lockType.ordinal * 8).inv())
    }

    override public fun hasEquipmentLock(equipmentSlot: EquipmentSlot, lockType: ArmorStand.LockType): Boolean {
        return (getHandle().disabledSlots and (1 shl CraftEquipmentSlot.getNMS(equipmentSlot).getFilterFlag() + lockType.ordinal * 8)) != 0
    }
    */

    // custom teleport, allow vehicles to teleport
    // see: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java#156,455,459,472,485-486,490-491
    override public fun teleport(location: Location): Boolean {
        location.checkFinite()

        if ( !entity.isAlive ) {
            return false
        }

        // If this entity is riding another entity, we must dismount before teleporting.
        // entity.stopRiding()

        // Let the server handle cross world teleports
        entity.level = (location.getWorld() as CraftWorld).getHandle()

        // entity.setLocation() throws no event, and so cannot be cancelled
        // entity.setPos(location.getX(), location.getY(), location.getZ())
        // entity.setRot(location.getYaw(), location.getPitch())
        entity.absMoveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());

        // SPIGOT-619: Force sync head rotation also
        entity.setYHeadRot(location.getYaw())
        
        return true
    }

    override public fun remove() {
        super.remove()
    }

    // have to use persistentDataContainer attached to the CraftArmorStand
    // thats actually added to World. Otherwise, the data container wont
    // be the same for a CustomArmorStand and a CraftArmorStand.
    // This is required when iterating all entities in world (CraftArmorStand
    // is data container actually used)
    override public fun getPersistentDataContainer(): CraftPersistentDataContainer {
        return this.craftArmorStand.getPersistentDataContainer()
    }

    /**
     * static manager functions
     */
    companion object {
        
        // register with NMS
        @Throws(IllegalStateException::class)
        public fun register() {
            // val dataTypes: Map<Object, Type<?>> = (Map<Object, Type<?>>)DataConverterRegistry.a()
            //     .getSchema(DataFixUtils.makeKey(SharedConstants.getGameVersion().getWorldVersion()))
            //     .findChoiceType(DataConverterTypes.ENTITY_TREE).types();
            // dataTypes.put(key.toString(), dataTypes.get(parentType.h().toString().replace("entity/", "")));
            // EntityTypes.a<T> a = EntityTypes.a.a(maker, EnumCreatureType.CREATURE);
            // entityType = a.a(key.getKey());
            // IRegistry.a(IRegistry.ENTITY_TYPE, key.getKey(), entityType);
            
            // registered = true;

            // if (registered || IRegistry.ENTITY_TYPE.getOptional(key).isPresent()) {
            //     throw IllegalStateException(String.format("Unable to register entity with key '%s' as it is already registered.", key))
            //     return
            // }

            // val dataTypes = DataConverterRegistry.a()
            //     .getSchema(DataFixUtils.makeKey(SharedConstants.getGameVersion().getWorldVersion()))
            //     .findChoiceType(DataConverterTypes.ENTITY_TREE).types() as Map<Any, Type<*>>
            // dataTypes.put(key.toString(), dataTypes.get(parentType.h().toString().replace("entity/", "")))
            // val a = EntityTypes.a.a(maker, EnumCreatureType.CREATURE)
            
            // entityType = a.a(key.getKey())

            // IRegistry.a(IRegistry.ENTITY_TYPE, key.getKey(), entityType)

            // registered = true
        }

        public fun create(world: BukkitWorld, loc: Location): CustomArmorStand {
            val craftServer = Bukkit.getServer() as CraftServer
            val craftWorld = world as CraftWorld

            // create entity
            val armorstand = InternalCustomEntityArmorstand(
                craftWorld.getHandle(),
                loc.x,
                loc.y,
                loc.z
            )
            val craftArmorStand: CraftArmorStand = craftWorld.addEntity(armorstand, CreatureSpawnEvent.SpawnReason.CUSTOM)
            
            val customArmorStand = CustomArmorStand(
                craftServer,
                craftArmorStand.getHandle(),
                craftArmorStand
            )
            customArmorStand.setPersistent(true)
 
            return customArmorStand
        }
    }
}

private fun fromNMS(old: Rotations): EulerAngle {
    return EulerAngle(
        Math.toRadians(old.getX().toDouble()),
        Math.toRadians(old.getY().toDouble()),
        Math.toRadians(old.getZ().toDouble())
    )
}

private fun toNMS(old: EulerAngle): Rotations {
    return Rotations(
        Math.toDegrees(old.getX()).toFloat(),
        Math.toDegrees(old.getY()).toFloat(),
        Math.toDegrees(old.getZ()).toFloat()
    )
}
