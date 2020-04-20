package me.infinityz.inventorysync

import com.github.benmanes.caffeine.cache.Caffeine
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubListener
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.TimeUnit

class InventorySync : JavaPlugin() {
    /*Connect to redisPubSub with reactive to allow async processing*/
    val client = RedisClient.create("redis://p1p2p3p4p5p6@panel.noobsters.net:6379/0")
    val pubSub = client.connectPubSub().reactive()
    val reactive = client.connect().reactive()
    val cacheOther = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build<String, String>()
    //private val mongo = MongoClients.create("mongodb://admin:p1p2p3p4p5p6@panel.noobsters.net:27017/?authSource=admin&connectTimeoutMS=5000")

    override fun onEnable() {
       val key = reactive.get("oimate")
        pubSub.subscribe("com-channel").subscribe()

        Bukkit.getPluginManager().registerEvents(EventsListener(this), this)

        println("REDIS GET: ${key.block()}")
    }


    override fun onDisable() {

    }


}