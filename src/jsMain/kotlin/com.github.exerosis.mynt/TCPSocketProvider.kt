package com.github.exerosis.mynt

import com.github.exerosis.mynt.base.*
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

class TCPSocketProvider(private val allocator: () -> dynamic) : Provider {
    private val turbo = js("require('turbo-net')")
    private val servers = mutableMapOf<Address, dynamic>()
    private val clients = mutableMapOf<Address, Connection>()
    private var open = 0

    private open class Handler(
        allocator: () -> dynamic,
        val channel: dynamic
    ) : Connection {
        var open = false

        init {
            channel.on("error") { err -> throw RuntimeException(err.toString()) }
            channel.on("close") { open = false; Unit }
        }

        override val read = object : Read {
            override suspend fun skip(amount: Int) {
                TODO("Not yet implemented")
            }

            override suspend fun bytes(bytes: ByteArray, amount: Int, offset: Int): ByteArray {
                TODO("Not yet implemented")
            }

            override suspend fun byte(): Byte {
                TODO("Not yet implemented")
            }

            override suspend fun short(): Short {
                TODO("Not yet implemented")
            }

            override suspend fun int(): Int {
                TODO("Not yet implemented")
            }

            override suspend fun float(): Float {
                TODO("Not yet implemented")
            }

            override suspend fun long(): Long {
                TODO("Not yet implemented")
            }

            override suspend fun double(): Double {
                TODO("Not yet implemented")
            }

        }

        override val write = object : Write {
            override suspend fun bytes(bytes: ByteArray, amount: Int, offset: Int) {
                TODO("Not yet implemented")
            }

            override suspend fun byte(byte: Byte) {
                TODO("Not yet implemented")
            }

            override suspend fun short(short: Short) {
                TODO("Not yet implemented")
            }

            override suspend fun int(int: Int) {
                TODO("Not yet implemented")
            }

            override suspend fun float(float: Float) {
                TODO("Not yet implemented")
            }

            override suspend fun long(long: Long) {
                TODO("Not yet implemented")
            }

            override suspend fun double(double: Double) {
                TODO("Not yet implemented")
            }

            override suspend fun skip(amount: Int) {
                TODO("Not yet implemented")
            }
        }
        override val address: Address
            get() = TODO("Not yet implemented")

        override val isOpen = open

        override suspend fun close() = continued<Unit> {
            if (!open) Unit else {
                channel.close { it.resume(Unit) }
                COROUTINE_SUSPENDED
            }
        }
    }

    override suspend fun accept(address: Address) = continued<Connection> {
        val server = servers.getOrPut(address) {
            turbo.createServer().listen(address.port, address.address) { open++ }
        }
        var listener: dynamic = null
        listener = { channel: dynamic ->
            it.resume(Handler(allocator, channel))
            server.removeEventListener("connection", listener)
        }
        server.addEventListener("connection", listener)
        COROUTINE_SUSPENDED
    }

    override suspend fun connect(address: Address) = continued<Connection> { continuation ->
        val connection = clients[address]
        if (connection == null) {
            val channel = turbo.connect(address.port, address.address)
            val handler = Handler(allocator, channel)
            channel.on("connect") { continuation.resume(handler) }
            clients[address] = handler; COROUTINE_SUSPENDED
        } else connection
    }

    override val isOpen = clients.size + servers.size > 0

    override suspend fun close() {
        clients.values.forEach { it.close() }
        servers.values.forEach { server ->
            continued<Unit> {
                server.close { it.resume(Unit) }
                COROUTINE_SUSPENDED
            }
        }
        clients.clear()
        servers.clear()
    }
}