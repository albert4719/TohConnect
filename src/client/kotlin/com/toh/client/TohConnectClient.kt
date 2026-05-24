package com.toh.client

import ConfigManager
import com.toh.TohConnect
import net.fabricmc.api.ClientModInitializer

object TohConnectClient : ClientModInitializer {
    override fun onInitializeClient() {
        // 从配置文件中读取动态的 proxyUrl
        val proxyAddress = ConfigManager.currentConfig.proxyUrl
        TohConnect.logger.info("Connecting to $proxyAddress")
        // 启动本地代理服务器
        LocalProxyServer(proxyAddress).start()
    }
}