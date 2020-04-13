package me.infinityz.inventorysync

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.reactive.ChannelMessage
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class InventorySync : JavaPlugin() {
    /*Connect to redisPubSub with reactive to allow async processing*/
    private val client: RedisClient = RedisClient.create("redis://p1p2p3p4p5p6@panel.noobsters.net:6379/0")
    private val connection: StatefulRedisPubSubConnection<String, String> = client.connectPubSub()
    private val reactive: RedisPubSubReactiveCommands<String, String> = connection.reactive()
    private val mongo: MongoClient = MongoClients.create("mongodb://admin:p1p2p3p4p5p6@panel.noobsters.net:27017/?authSource=admin&connectTimeoutMS=5000")

    override fun onEnable() {
        reactive.subscribe("communication-channel").subscribe()
        reactive.observeChannels().doOnNext { msg: ChannelMessage<String, String> -> Bukkit.broadcastMessage(msg.message) }.subscribe()

    }

    override fun onDisable() {

    }


}