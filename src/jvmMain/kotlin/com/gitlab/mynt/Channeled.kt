@file:Suppress("BlockingMethodInNonBlockingContext", "NOTHING_TO_INLINE")
package com.gitlab.mynt

import com.gitlab.mynt.base.Connection
import com.gitlab.mynt.base.Provider
import com.gitlab.mynt.base.Read
import com.gitlab.mynt.base.Write
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousChannelGroup.*
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousServerSocketChannel.*
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ForkJoinPool.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

open class ChannelHandler(size: Int) : Connection {
    val input = ByteBuffer.allocateDirect(size)!!
    val output = ByteBuffer.allocateDirect(size)!!
    open lateinit var channel: AsynchronousSocketChannel

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
                amount: Int
        ) = continued<ByteBuffer> { buffer(input, buffer, amount, it) }
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
                amount: Int
        ) = continued<Unit> { this.buffer(output, buffer, amount, it) }
        override suspend fun bytes(
                bytes: ByteArray,
                amount: Int,
                offset: Int
        ) = continued<Unit> {
            val buffer = ByteBuffer.wrap(bytes, offset, amount)
            buffer.position(amount)
            this.buffer(output, buffer, amount, it)
        }

        override suspend fun byte(byte: Byte) = continued<Unit> { byte(output, byte, it) }
        override suspend fun short(short: Short) = continued<Unit> { short(output, short, it) }
        override suspend fun int(int: Int) = continued<Unit> { int(output, int, it) }
        override suspend fun long(long: Long) = continued<Unit> { long(output, long, it) }
        override suspend fun float(float: Float) = continued<Unit> { float(output, float, it) }
        override suspend fun double(double: Double) = continued<Unit> { double(output, double, it) }
    }

    override val isOpen get() = channel.isOpen
    override suspend fun close() = channel.close()
}

class AcceptChannelHandler(
        size: Int
) : ChannelHandler(size), CompletionHandler<AsynchronousSocketChannel, Continuation<Connection>> {
    override fun completed(channel: AsynchronousSocketChannel, continuation: Continuation<Connection>) {
        this.channel = channel
        continuation.resumeWith(Result.success(this))
    }
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
            continuation.resumeWith(Result.failure(reason))
}
class ConnectChannelHandler(
    size: Int, override var channel: AsynchronousSocketChannel
) : ChannelHandler(size), CompletionHandler<Void?, Continuation<Connection>> {
    override fun completed(ignored: Void?, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.success(this))
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
        continuation.resumeWith(Result.failure(reason))
}

fun SocketProvider(size: Int, group: AsynchronousChannelGroup) = object : Provider<SocketAddress> {
    private val servers = HashMap<SocketAddress, AsynchronousServerSocketChannel>()
    private val clients = HashMap<SocketAddress, Connection>()
    private val serverFactory = { address: SocketAddress -> open(group).bind(address) }

    override suspend fun accept(address: SocketAddress) = continued<Connection> {
        servers.computeIfAbsent(address, serverFactory).accept(it, AcceptChannelHandler(size))
        COROUTINE_SUSPENDED
    }
    override suspend fun connect(address: SocketAddress) = continued<Connection> {
        //OLD some slight issues with open vs connected.
        val connection = clients[address]
        if (connection == null) {
            val channel = AsynchronousSocketChannel.open(group)
            val handler = ConnectChannelHandler(size, channel)
            channel.connect(address, it, handler)
            clients[address] = handler; COROUTINE_SUSPENDED
        } else connection
    }

    override val isOpen get() = !group.isTerminated && !group.isShutdown
    override suspend fun close() {
        group.shutdownNow()
        servers.clear()
        clients.clear()
    }
}
fun SocketProvider(size: Int) = SocketProvider(size, withThreadPool(commonPool()))