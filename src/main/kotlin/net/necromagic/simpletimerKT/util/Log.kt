package net.necromagic.simpletimerKT.util

import net.dv8tion.jda.api.entities.TextChannel

object Log {
    val logChannels = mutableListOf<TextChannel>()

    fun sendLog(log: String) {
        logChannels.forEach { channel ->
            try {
                channel.sendMessage(log)
            } catch (ignore: Exception) {
            }
        }
    }
}