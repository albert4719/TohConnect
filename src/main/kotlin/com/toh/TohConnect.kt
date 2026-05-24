package com.toh

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object TohConnect : ModInitializer {
    val logger = LoggerFactory.getLogger("tohconnect")

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

	}
}