package com.gitlab.mynt

import com.gitlab.mynt.base.Read
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup.withThreadPool
import java.util.concurrent.Executors

typealias VarInt = Int
const val ERR_VAR_INT = "Corrupted variable length integer.";
suspend fun Read.varInt(offset: Int = 0): VarInt {
    if (offset > 36) throw IllegalStateException(ERR_VAR_INT)
    val value = byte().toInt(); val part = value and 127
    return part shl offset or (if (value == part) 0 else varInt(offset + 7))
}

const val ERR_STRING = "Failed to read corrupted string."
suspend fun Read.string(): String {
    val length = varInt()
    if (length !in 0..32767) throw IllegalStateException(ERR_STRING)
    return bytes(length).toString(Charsets.UTF_8)
}

fun main() = runBlocking {
    val executor = Executors.newFixedThreadPool(3)
    val provider = SocketProvider(1048576, withThreadPool(executor))
    println("Ready")
    val (read, write) = provider.accept(InetSocketAddress("localhost", 25577))
    read.varInt() //Handshake length. (Ignored)
    read.varInt() //Handshake id. (Ignored)
    val version = read.varInt()
    println(version)
    read.skip(read.varInt())
    val port = read.short()
    println(port)
}