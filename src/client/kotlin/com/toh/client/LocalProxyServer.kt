package com.toh.client

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocalProxyServer constructor(private val wssUrl: String) {
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    var serverPort = 0
        private set

    companion object {
        @Volatile
        var instance: LocalProxyServer? = null
            private set

        @Synchronized
        fun startProxy(wssUrl: String): Int {
            if (instance == null || instance?.running == false) {
                instance = LocalProxyServer(wssUrl).apply { start() }
            }
            return instance?.serverPort ?: -1
        }

        @Synchronized
        fun stopProxy() {
            instance?.let {
                it.stop()
                instance = null
            }
        }

        val isRunning: Boolean
            @Synchronized get() = instance != null && instance!!.running
    }

    fun start() {
        running = true

        // 分配独立线程去监听本地端口
        Executors.newSingleThreadExecutor().submit {
            try {
                serverSocket = ServerSocket(25565, 50, InetAddress.getByName("127.0.0.1"))
                serverPort = serverSocket!!.localPort
                println("[LocalProxyServer] 代理已启动，监听本地动态端口: $serverPort")

                while (running) {
                    val clientSocket = serverSocket?.accept() ?: break
                    // 每一个连入的客户端，单独开一个单线程线程池处理（防止阻塞主循环）
                    Executors.newSingleThreadExecutor().submit { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                if (running) {
                    println("[LocalProxyServer] 监听本地端口失败: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        println("[LocalProxyServer] 代理服务已关闭")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            println("[LocalProxyServer] 正在连接远端 WebSocket 服务器: $wssUrl")

            // 1. 构建 Java-WebSocket 客户端
            val wsClient = object : WebSocketClient(URI(wssUrl)) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    println("[LocalProxyServer] 远端 WebSocket 连接已建立")
                }

                override fun onMessage(message: String) {
                    // 忽略文本消息，MC 协议全是二进制
                }

                override fun onMessage(message: ByteBuffer) {
                    // 逻辑 B：远端 WS -> 本地 MC 客户端
                    try {
                        val out: OutputStream = clientSocket.getOutputStream()

                        // 💡 优化：安全提取 ByteBuffer 数据，避免 message.array() 读到脏数据
                        val bytes = ByteArray(message.remaining())
                        message.get(bytes)

                        out.write(bytes)
                        out.flush()
                    } catch (e: Exception) {
                        println("[LocalProxyServer] 写入本地 TCP 失败: ${e.message}")
                        this.close() // 断开 WS
                    }
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    println("[LocalProxyServer] 远端 WebSocket 关闭，代码: $code, 原因: $reason")
                    try { clientSocket.close() } catch (_: Exception) {}
                }

                override fun onError(ex: Exception) {
                    println("[LocalProxyServer] WebSocket 异常: ${ex.message}")
                }
            }

            // 设置连接超时，并阻塞直到握手完成
            wsClient.connectBlocking(10, TimeUnit.SECONDS)

            // 2. 逻辑 A：本地 MC 客户端 -> 远端 WS
            // 采用新模组的逻辑，再开一个独立的单线程死循环阻塞读取 InputStream
            Executors.newSingleThreadExecutor().submit {
                try {
                    clientSocket.getInputStream().use { input ->
                        val buf = ByteArray(32 * 1024) // 32KB 缓冲区
                        var len = 0
                        while (running && !clientSocket.isClosed && input.read(buf).also { len = it } != -1) {
                            if (len > 0) {
                                val dataToSend = ByteArray(len)
                                System.arraycopy(buf, 0, dataToSend, 0, len)
                                wsClient.send(dataToSend)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[LocalProxyServer] 读取本地 TCP 失败: ${e.message}")
                    wsClient.close()
                }
            }

        } catch (e: Exception) {
            println("[LocalProxyServer] 处理客户端连接时发生异常: ${e.message}")
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }
}