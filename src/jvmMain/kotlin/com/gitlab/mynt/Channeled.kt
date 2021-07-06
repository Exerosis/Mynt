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
import kotlin.coroutines.resumeWithException

open class ChannelHandler(size: Int, error: (Throwable) -> (Unit)) : Connection {
    val input = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer
    val output = ByteBuffer.allocateDirect(size)!!.flip() as ByteBuffer
    open lateinit var channel: AsynchronousSocketChannel

    inline fun doRead(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
            channel.read(buffer, buffer, handler)
    inline fun doWrite(buffer: ByteBuffer, handler: CompletionHandler<Int, ByteBuffer>) =
            channel.write(buffer, buffer, handler)

    override val read = object : Read {
        val skip = SkipReadHandler(::doRead, error)
        val buffer = BufferReadHandler(::doRead, error)
        val array = ArrayReadHandler(::doRead, error)
        val byte = NumberReadHandler(::doRead, error) { it.get() }
        val short = NumberReadHandler(::doRead, error) { it.short }
        val int = NumberReadHandler(::doRead, error) { it.int }
        val long = NumberReadHandler(::doRead, error) { it.long }
        val float = NumberReadHandler(::doRead, error) { it.float }
        val double = NumberReadHandler(::doRead, error) { it.double }

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
        val buffer = BufferWriteHandler(::doWrite, error)
        val byte = NumberWriteHandler<Byte>(::doWrite, error) { put(it) }
        val short = NumberWriteHandler<Short>(::doWrite, error) { putShort(it) }
        val int = NumberWriteHandler<Int>(::doWrite, error) { putInt(it) }
        val long = NumberWriteHandler<Long>(::doWrite, error) { putLong(it) }
        val float = NumberWriteHandler<Float>(::doWrite, error) { putFloat(it) }
        val double = NumberWriteHandler<Double>(::doWrite, error) { putDouble(it) }
        override suspend fun skip(amount: Int) = TODO("Not yet implemented")

        override suspend fun buffer(
                buffer: ByteBuffer,
        ) = continued<Unit> { this.buffer(output, buffer, it) }
        override suspend fun bytes(
                bytes: ByteArray,
                amount: Int,
                offset: Int
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

    override val isOpen get() = channel.isOpen
    override suspend fun close() = channel.close()
}

class AcceptChannelHandler(
    size: Int, error: (Throwable) -> (Unit)
) : ChannelHandler(size, error), CompletionHandler<AsynchronousSocketChannel, Continuation<Connection>> {
    override fun completed(channel: AsynchronousSocketChannel, continuation: Continuation<Connection>) {
        this.channel = channel
        continuation.resumeWith(Result.success(this))
    }
    override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
            continuation.resumeWith(Result.failure(reason))
}
class ConnectChannelHandler(
    size: Int, error: (Throwable) -> (Unit), override var channel: AsynchronousSocketChannel
) : ChannelHandler(size, error), CompletionHandler<Void?, Continuation<Connection>> {
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
        servers.computeIfAbsent(address, serverFactory).accept(it, AcceptChannelHandler(size) { reason ->
            it.resumeWithException(reason)
        })
        COROUTINE_SUSPENDED
    }
    override suspend fun connect(address: SocketAddress) = continued<Connection> {
        //OLD some slight issues with open vs connected.
        val connection = clients[address]
        if (connection == null) {
            val channel = AsynchronousSocketChannel.open(group)
            val handler = ConnectChannelHandler(size, { reason -> it.resumeWithException(reason) }, channel)
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