@file:Suppress("BlockingMethodInNonBlockingContext", "NOTHING_TO_INLINE")
package com.github.exerosis.mynt

import com.github.exerosis.mynt.base.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.AsynchronousServerSocketChannel.open
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation

private typealias Channel = AsynchronousSocketChannel

abstract class ChannelHandler(size: Int) : Connection {
    val input = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer
    val output = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer
    abstract val channel: Channel

    inline fun doRead(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
        channel.read(buffer, buffer, handler)
    inline fun doWrite(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
        channel.write(buffer, buffer, handler)

    override val read = object : Read {
        val skip = SkipReadHandler(::doRead)
        val buffer = BufferReadHandler(::doRead)
        val array = ArrayReadHandler(::doRead)
        val byte = NumberReadHandler(::doRead) { it.get() }
        val short = NumberReadHandler(::doRead) { it.short }
        val int = NumberReadHandler(::doRead) { it.int }
        val long = NumberReadHandler(::doRead) { it.long }
        val float = NumberReadHandler(::doRead) { it.float }
        val double = NumberReadHandler(::doRead) { it.double }

        override suspend fun skip(amount: Int) = continued<Unit> { skip(input, amount, it) }

        override suspend fun buffer(
            buffer: ByteBuffer,
        ) = continued<ByteBuffer> { buffer(input, buffer, it) }
        override suspend fun bytes(
            bytes: ByteArray,
            amount: Int,
            offset: Int
        ) = continued<ByteArray> { array(input, bytes, amount, offset, it) }

        override suspend fun byte() = continued<Byte> { byte(input, 1, it) }
        override suspend fun short() = continued<Short> { short(input, 2, it) }
        override suspend fun int() = continued<Int> { int(input, 4, it) }
        override suspend fun long() = continued<Long> { long(input, 8, it) }
        override suspend fun float() = continued<Float> { float(input, 4, it) }
        override suspend fun double() = continued<Double> { double(input, 8, it) }
    }
    override val write = object : Write {
        val buffer = BufferWriteHandler(::doWrite)
        val byte = NumberWriteHandler<Byte>(::doWrite) { put(it) }
        val short = NumberWriteHandler<Short>(::doWrite) { putShort(it) }
        val int = NumberWriteHandler<Int>(::doWrite) { putInt(it) }
        val long = NumberWriteHandler<Long>(::doWrite) { putLong(it) }
        val float = NumberWriteHandler<Float>(::doWrite) { putFloat(it) }
        val double = NumberWriteHandler<Double>(::doWrite) { putDouble(it) }
        override suspend fun skip(amount: Int) = TODO("Not yet implemented")

        override suspend fun buffer(
                buffer: ByteBuffer,
        ) = continued<Unit> { this.buffer(output, buffer, it) }
        override suspend fun bytes(
            bytes: ByteArray, amount: Int, offset: Int
        ) = continued<Unit> {
            val buffer = ByteBuffer.wrap(bytes, offset, amount)
            buffer.position(amount)
            this.buffer(output, buffer, it)
        }

        override suspend fun byte(byte: Byte) = continued<Unit> { byte(output, byte, it) }
        override suspend fun short(short: Short) = continued<Unit> { short(output, short, it) }
        override suspend fun int(int: Int) = continued<Unit> { int(output, int, it) }
        override suspend fun long(long: Long) = continued<Unit> { long(output, long, it) }
        override suspend fun float(float: Float) = continued<Unit> { float(output, float, it) }
        override suspend fun double(double: Double) = continued<Unit> { double(output, double, it) }
    }

    override val address get() = channel.remoteAddress as Address

    override val isOpen get() = channel.isOpen
    override suspend fun close() = channel.close()
}

class AcceptChannelHandler(size: Int, val configure: (Channel) -> (Unit)) : ChannelHandler(size),
    CompletionHandler<Channel, Continuation<Connection>> {
    @Volatile override lateinit var channel: Channel
    override val address get() = channel.remoteAddress as Address
    override fun completed(channel: Channel, continuation: Continuation<Connection>) {
        configure(channel); this.channel = channel
        continuation.resumeWith(Result.success(this))
    }
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.failure(reason))
}
class ConnectChannelHandler(
    size: Int, override val channel: Channel
) : ChannelHandler(size), CompletionHandler<Void?, Continuation<Connection>> {
    override val address = channel.remoteAddress as Address
    override fun completed(ignored: Void?, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.success(this))
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.failure(reason))
}

fun SocketProvider(
    size: Int, group: AsynchronousChannelGroup,
    configure: (Channel) -> (Unit) = {}
) = object : Provider {
    private val servers = ConcurrentHashMap<Address, AsynchronousServerSocketChannel>()
    private val serverFactory = { address: Address -> open(group).bind(address) }

    override suspend fun accept(address: Address) = continued<Connection> {
        if (group.isShutdown) throw ShutdownChannelGroupException()
        servers.computeIfAbsent(address, serverFactory).accept(it, AcceptChannelHandler(size, configure))
        SUSPENDED
    }
    override suspend fun connect(address: Address) = continued<Connection> {
        if (group.isShutdown) throw ShutdownChannelGroupException()
        val channel = Channel.open(group)
        configure(channel)
        val handler = ConnectChannelHandler(size, channel)
        channel.connect(address, it, handler); SUSPENDED
    }

    override val isOpen get() = !group.isShutdown
    override suspend fun close() {
        group.shutdownNow()
        servers.clear()
    }
}