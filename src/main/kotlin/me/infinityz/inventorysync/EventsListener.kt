package me.infinityz.inventorysync

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.lettuce.core.SetArgs
import me.infinityz.inventorysync.Conversion.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import java.util.concurrent.TimeUnit


/* Constructor that takes in instance to communicate with databases when necessary */
class EventsListener(val instance: InventorySync) : Listener {
    val cacheMap = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build<String, String>()
    val map = mutableMapOf<String, Int>()

    init {
        instance.pubSub.observeChannels().doOnNext {
            instance.cacheOther.put(Gson().fromJson(it.message, JsonObject::class.java).get("uuid").asString, it.message)
        }.subscribe()
    }

    @EventHandler
    fun onAsyncPreLogin(e: AsyncPlayerPreLoginEvent) {
        /*Cache the data in a asynchronous way*/
        val uuid = e.uniqueId
        /*Check if redis has any data on this player, and if so, cache it into a map*/
        val cache = instance.reactive.get(uuid.toString()).block()
        /*Check for null */
        if (cache.isNullOrBlank()) return
        cacheMap.put(uuid.toString(), cache)
    }

    @EventHandler
    fun onLogin(e: PlayerJoinEvent) {

        /*Cache the data in a asynchronous way*/
        val uuid = e.player.uniqueId
        /*Check if redis has any data on this player, and if so, cache it into a map*/
        val cache = cacheMap.getIfPresent(uuid.toString())
        /*Check for null */
        if (cache.isNullOrBlank()) {
            map[e.player.uniqueId.toString()] = 0
            Bukkit.getScheduler().runTaskLater(instance, Runnable {
                val secondCache = instance.cacheOther.getIfPresent(uuid.toString())
                map.remove(e.player.uniqueId.toString())
                if (!secondCache.isNullOrBlank()) processData(e.player, Gson().fromJson(secondCache, JsonObject::class.java))
            }, 20L)
            return
        }
        /*If we get this far, the data has been processed*/
        processData(e.player, Gson().fromJson(cache, JsonObject::class.java))
        /*Now, since the data was used it can be removed from the cache manually. (To avoid duplicates of items)*/
        cacheMap.invalidate(uuid)


    }

    fun processData(p: Player, json: JsonObject) {
        if (json.get("from_server_name").asString.equals(Bukkit.getMotd(), true)) return
        /*Process Inventory*/
        val inv = json.getAsJsonObject("inventory")
        p.inventory.heldItemSlot = inv.get("held_item_slot").asInt
        p.inventory.contents = itemStackArrayFromBase64(inv.get("contents").asString)
        p.enderChest.contents = itemStackArrayFromBase64(inv.get("ender_chest").asString)
        p.inventory.setArmorContents(itemStackArrayFromBase64(inv.get("armor_contents").asString))
        /*Process effects*/
        val potionEffect = json.getAsJsonObject("potion_effects")
        potionEffect.entrySet().forEach {
            val pot = potionEffect.getAsJsonObject(it.key)
            val type = PotionEffectType.getByName(it.key)!!
            val amplifier = pot.get("amplifier").asInt
            val duration = pot.get("duration").asInt
            p.activePotionEffects.forEach { typ -> p.removePotionEffect(typ.type) }
            p.addPotionEffect(PotionEffect(type, duration, amplifier))
        }
        /*Process Status*/
        val status = json.getAsJsonObject("status")
        p.health = status.get("health").asDouble
        p.absorptionAmount = status.get("absorption_hearts").asDouble
        p.foodLevel = status.get("food_level").asInt
        p.saturation = status.get("saturation").asFloat
        p.level = status.get("exp_level").asInt
        p.exp = status.get("exp_percentage").asFloat
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val data = getProcessData(e.player)
        instance.reactive.set(e.player.uniqueId.toString(), data,
                SetArgs.Builder.ex(15)).subscribe()
        instance.reactive.publish("com-channel", data).subscribe()

    }

    fun getProcessData(p: Player): String {
        return serializePlayerInfo(p)
    }

    fun serializePlayerInfo(player: Player): String {
        val obj = JsonObject()
        val content: String = toBase64(player.inventory)
        val ender_chest: String = toBase64(player.enderChest)
        val armor: String = itemStackArrayToBase64(player.inventory.armorContents)
        obj.add("uuid", JsonPrimitive(player.uniqueId.toString()))
        obj.add("last_known_name", JsonPrimitive(player.name))
        obj.add("from_server_name", JsonPrimitive(Bukkit.getServer().motd.toLowerCase()))
        /**
         * Serialize inventory and enderchest inventory and add them to the json object
         */
        val jsonInventory = JsonObject()
        jsonInventory.add("held_item_slot", JsonPrimitive(player.inventory.heldItemSlot))
        jsonInventory.add("contents", JsonPrimitive(content))
        jsonInventory.add("armor_contents", JsonPrimitive(armor))
        jsonInventory.add("ender_chest", JsonPrimitive(ender_chest))
        obj.add("inventory", jsonInventory)
        /** Turn active potion effects into a json object  */
        val active_effects = JsonObject()
        player.activePotionEffects.stream().forEach { potion: PotionEffect ->
            val potionJson = JsonObject()
            potionJson.addProperty("amplifier", potion.amplifier)
            potionJson.addProperty("duration", potion.duration)
            active_effects.add(potion.type.name, potionJson)
        }
        obj.add("potion_effects", active_effects)
        /** Get health, absoption, food, saturation, level, and add them to the json  */
        val player_status = JsonObject()
        player_status.add("health", JsonPrimitive(player.health))
        player_status.add("absorption_hearts", JsonPrimitive(player.absorptionAmount))
        player_status.add("food_level", JsonPrimitive(player.foodLevel))
        player_status.add("saturation", JsonPrimitive(player.saturation))
        player_status.add("exp_level", JsonPrimitive(player.level))
        player_status.add("exp_percentage", JsonPrimitive(player.exp))
        obj.add("status", player_status)
        // Use this to print a pretty Gson
        val gson = GsonBuilder().setPrettyPrinting().create()
        // Change this
        return gson.toJson(obj)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent){
        if(map.containsKey(e.player.uniqueId.toString())) e.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent){
        if(map.containsKey(e.player.uniqueId.toString())) e.isCancelled = true

    }
    @EventHandler(ignoreCancelled = true)
    fun onPlace(e: PlayerDropItemEvent){
        if(map.containsKey(e.player.uniqueId.toString())) e.isCancelled = true
    }
    @EventHandler(ignoreCancelled = true)
    fun onInteract(e: InventoryOpenEvent){
        if(map.containsKey(e.player.uniqueId.toString())) e.isCancelled = true
    }

}
