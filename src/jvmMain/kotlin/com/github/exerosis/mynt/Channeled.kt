@file:Suppress("BlockingMethodInNonBlockingContext", "NOTHING_TO_INLINE")
package com.github.exerosis.mynt

import com.github.exerosis.mynt.base.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.AsynchronousServerSocketChannel.open
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CompletionHandler as Cancelled

private typealias Channel = AsynchronousSocketChannel

open class ChannelHandler(size: Int, val channel: Channel) : Connection {
    val input = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer
    val output = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer

    inline fun doRead(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
        channel.read(buffer, buffer, handler)
    inline fun doWrite(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
        channel.write(buffer, buffer, handler)

    override val read = object : Read {
        val skip = SkipReadHandler(::doRead, channel)
        val buffer = BufferReadHandler(::doRead, channel)
        val array = ArrayReadHandler(::doRead, channel)
        val byte = NumberReadHandler(::doRead, channel) { it.get() }
        val short = NumberReadHandler(::doRead, channel) { it.short }
        val int = NumberReadHandler(::doRead, channel) { it.int }
        val long = NumberReadHandler(::doRead, channel) { it.long }
        val float = NumberReadHandler(::doRead, channel) { it.float }
        val double = NumberReadHandler(::doRead, channel) { it.double }

        override suspend fun skip(amount: Int) = if (amount == 0) Unit else
            continued { skip(input, amount, it) }

        override suspend fun buffer(
            buffer: ByteBuffer,
        ) = continued { buffer(input, buffer, it) }
        override suspend fun bytes(
            bytes: ByteArray,
            amount: Int,
            offset: Int
        ) = if (amount == 0) bytes else
            continued { array(input, bytes, amount, offset, it) }

        override suspend fun byte() = continued<Byte> { byte(input, 1, it) }
        override suspend fun short() = continued<Short> { short(input, 2, it) }
        override suspend fun int() = continued<Int> { int(input, 4, it) }
        override suspend fun long() = continued<Long> { long(input, 8, it) }
        override suspend fun float() = continued<Float> { float(input, 4, it) }
        override suspend fun double() = continued<Double> { double(input, 8, it) }
    }
    override val write = object : Write {
        val buffer = BufferWriteHandler(::doWrite, channel)
        val byte = NumberWriteHandler<Byte>(::doWrite, channel) { put(it) }
        val short = NumberWriteHandler<Short>(::doWrite, channel) { putShort(it) }
        val int = NumberWriteHandler<Int>(::doWrite, channel) { putInt(it) }
        val long = NumberWriteHandler<Long>(::doWrite, channel) { putLong(it) }
        val float = NumberWriteHandler<Float>(::doWrite, channel) { putFloat(it) }
        val double = NumberWriteHandler<Double>(::doWrite, channel) { putDouble(it) }
        override suspend fun skip(amount: Int) = TODO("Not yet implemented")

        override suspend fun buffer(
                buffer: ByteBuffer,
        ) = continued { this.buffer(output, buffer, it) }
        override suspend fun bytes(
            bytes: ByteArray, amount: Int, offset: Int
        ) = continued {
            val buffer = ByteBuffer.wrap(bytes, offset, amount)
            buffer.position(amount)
            this.buffer(output, buffer, it)
        }

        override suspend fun byte(byte: Byte) = continued { byte(output, byte, it) }
        override suspend fun short(short: Short) = continued { short(output, short, it) }
        override suspend fun int(int: Int) = continued { int(output, int, it) }
        override suspend fun long(long: Long) = continued { long(output, long, it) }
        override suspend fun float(float: Float) = continued { float(output, float, it) }
        override suspend fun double(double: Double) = continued { double(output, double, it) }
    }

    override val address get() = channel.remoteAddress as Address

    override val isOpen get() = channel.isOpen
    override suspend fun close() = channel.close()
}

class AcceptChannelHandler(private val size: Int, val configure: (Channel) -> (Unit)) :
    CompletionHandler<Channel, Continuation<Connection>> {
    override fun completed(channel: Channel, continuation: Continuation<Connection>) {
        configure(channel)
        continuation.resumeWith(Result.success(ChannelHandler(size, channel)))
    }
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.failure(reason))
}
class ConnectChannelHandler(
    size: Int, channel: Channel, private val target: Address
) : ChannelHandler(size, channel), Cancelled, CompletionHandler<Void?, Continuation<Connection>> {
    override fun invoke(cause: Throwable?) {
        if (cause != null) channel.close()
    }
    override fun completed(ignored: Void?, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.success(this))
    override fun failed(reason: Throwable, continuation: Continuation<Connection>)  {
        if (reason is TimeoutException) channel.connect(target, continuation, this)
        else continuation.resumeWith(Result.failure(reason))
    }
}

class ServerSocket(
    group: AsynchronousChannelGroup,
    private val address: Address,
    private val servers: ConcurrentHashMap<Address, ServerSocket>
) : Cancelled {
    val server = open(group).bind(address)!!
    override fun invoke(cause: Throwable?) {
        if (cause != null) {
            server.close()
            servers.remove(address)
        }
    }
}

fun SocketProvider(
    size: Int, group: AsynchronousChannelGroup,
    configure: (Channel) -> (Unit) = {}
) = object : Provider {
    private val servers = ConcurrentHashMap<Address, ServerSocket>()
    private val serverFactory = { address: Address -> ServerSocket(group, address, servers) }

    override suspend fun accept(address: Address) = continued {
        if (group.isShutdown) throw ShutdownChannelGroupException()
        servers.computeIfAbsent(address, serverFactory).apply {
            server.accept(it.intercept(this), AcceptChannelHandler(size, configure))
        }
        SUSPENDED
    }
    override suspend fun connect(address: Address) = continued {
        if (group.isShutdown) throw ShutdownChannelGroupException()
        val channel = Channel.open(group)
        configure(channel)
        val handler = ConnectChannelHandler(size, channel, address)
        channel.connect(address, it.intercept(handler), handler); SUSPENDED
    }

    override val isOpen get() = !group.isShutdown
    override suspend fun close() {
        group.shutdownNow()
        servers.clear()
    }
}