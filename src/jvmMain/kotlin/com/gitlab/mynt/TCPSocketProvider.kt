package com.gitlab.mynt

import com.gitlab.mynt.fixed.FixedBufferWrite
import com.gitlab.mynt.base.Connection
import com.gitlab.mynt.base.Provider
import com.gitlab.mynt.fixed.FixedBufferRead
import com.gitlab.mynt.sequential.SequentialReadCoordinator
import com.gitlab.mynt.sequential.SequentialWriteCoordinator
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@Suppress("BlockingMethodInNonBlockingContext")
class TCPSocketProvider(
        private val group: AsynchronousChannelGroup,
        private val allocator: () -> ByteBuffer
) : Provider<SocketAddress> {
    private val servers = HashMap<SocketAddress, AsynchronousServerSocketChannel>()
    private val clients = HashMap<SocketAddress, Connection>()
    private val serverFactory = { address: SocketAddress ->
        AsynchronousServerSocketChannel.open(group).bind(address)
    }

    private open class Handler(
            allocator: () -> ByteBuffer
    ) : Connection {
        open lateinit var channel: AsynchronousSocketChannel

        override val read = FixedBufferRead(SequentialReadCoordinator { buffer, handler ->
            channel.read(buffer, buffer, handler)
        }, allocator())

        override val write = FixedBufferWrite(SequentialWriteCoordinator { buffer, handler ->
            channel.write(buffer, buffer, handler)
        }, allocator().flip() as ByteBuffer)

        override val isOpen
            get() = channel.isOpen

        override suspend fun close() = channel.close()
    }

    //--Accept--
    private class AcceptHandler(
            allocator: () -> ByteBuffer
    ) : Handler(allocator), CompletionHandler<AsynchronousSocketChannel, Continuation<Connection>> {
        override fun completed(channel: AsynchronousSocketChannel, continuation: Continuation<Connection>) {
            this.channel = channel
            continuation.resume(this)
        }

        override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
                continuation.resumeWithException(reason)
    }

    override suspend fun accept(address: SocketAddress) = continued<Connection> {
        servers.computeIfAbsent(address, serverFactory).accept(it,
            AcceptHandler(allocator)
        )
        COROUTINE_SUSPENDED
    }




    //--Connect--
    private class ConnectHandler(
            allocator: () -> ByteBuffer,
            override var channel: AsynchronousSocketChannel
    ) : Handler(allocator), CompletionHandler<Void?, Continuation<Connection>> {

        override fun completed(ignored: Void?, continuation: Continuation<Connection>) =
                continuation.resume(this)

        override fun failed(reason: Throwable, continuation: Continuation<Connection>) =
                continuation.resumeWithException(reason)
    }

    //TODO some slight issues with open vs connected.
    override suspend fun connect(address: SocketAddress) = continued<Connection> {
        val connection = clients[address]
        if (connection == null) {
            val channel = AsynchronousSocketChannel.open(group)
            val handler = ConnectHandler(allocator, channel)
            channel.connect(address, it, handler)
            clients[address] = handler; COROUTINE_SUSPENDED
        } else connection
    }


    //--State--
    override val isOpen get() = !group.isTerminated && !group.isShutdown

    override suspend fun close() {
        group.shutdownNow()
        servers.clear()
        clients.clear()
    }
}