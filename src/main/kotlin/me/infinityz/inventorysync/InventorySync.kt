package me.infinityz.inventorysync

import com.mongodb.reactivestreams.client.MongoClients
import io.lettuce.core.RedisClient
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

class InventorySync : JavaPlugin() {
    /*Connect to redisPubSub with reactive to allow async processing*/
    private val client = RedisClient.create("redis://p1p2p3p4p5p6@panel.noobsters.net:6379/0")
    private val connection = client.connectPubSub()
    private val reactive = connection.reactive()
    private val mongo = MongoClients.create("mongodb://admin:p1p2p3p4p5p6@panel.noobsters.net:27017/?authSource=admin&connectTimeoutMS=5000")

    override fun onEnable() {
        reactive.subscribe("communication-channel").subscribe()
        reactive.observeChannels().doOnNext { Bukkit.broadcastMessage(it.message) }.subscribe()
        saveWorld(3000)
    }

    private fun getVectors(rad: Int) : ArrayList<Vector>{
        val vectorList = arrayListOf<Vector>()
        val radius = (ceil(rad * 2 / 32.00 / 16.00) / 2).toInt()
        var x = -radius
        while (abs(x) <= radius) {
            var z = -radius
            while (abs(z) <= radius) {
                vectorList.add(Vector(x, z))
                z++
            }
            x++
        }
        return vectorList
    }
     private fun saveWorld(radius: Int) {
        val vectorList = getVectors(radius)
        val world = Bukkit.getWorlds()[0]
        val folder = File("${this.server.worldContainer.absolutePath}\\backup_${world.name}")
        val worldFolderList = world.worldFolder.listFiles().toList()

        worldFolderList.stream().filter { it.name.equals("region", true) }.findFirst().ifPresent { present -> present.listFiles().toList().parallelStream().forEach {
            val vector = Vector(Integer.parseInt(it.name.split(".")[1]), Integer.parseInt(it.name.split(".")[2]))
            vectorList.stream().filter{vec-> vector.isSimilar(vec.x, vec.z)}.findFirst().ifPresent {_ -> it.copyTo(File("${folder.absolutePath}\\region\\${it.name}"))}
        } }
        worldFolderList.stream().filter { it.name.equals("poi", true) }.findFirst().ifPresent { present -> present.listFiles().toList().parallelStream().forEach {
            val vector = Vector(Integer.parseInt(it.name.split(".")[1]), Integer.parseInt(it.name.split(".")[2]))
            vectorList.stream().filter{vec-> vector.isSimilar(vec.x, vec.z)}.findFirst().ifPresent {_ -> it.copyTo(File("${folder.absolutePath}\\poi\\${it.name}"))}
        } }
        worldFolderList.stream().filter { it.name.equals("level.dat", true) || it.name.equals("data", true) || it.name.equals("playerdata", true) || it.name.equals("advancements", true) || it.name.equals("stats", true) }.forEach { it.copyRecursively(File("${folder.absolutePath}\\${it.name}")) }
    }
    private fun saveEnd(radius: Int) {
        val vectorList = getVectors(radius)
        val world = Bukkit.getWorld(Bukkit.getWorlds()[0].name + "_the_end")!!
        val folder = File("${this.server.worldContainer.absolutePath}\\backup_${world.name}")
        val worldFolderList = world.worldFolder.listFiles().toList()
    }

    override fun onDisable() {

    }


}