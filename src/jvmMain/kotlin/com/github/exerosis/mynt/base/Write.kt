package com.github.exerosis.mynt.base

import java.nio.ByteBuffer
import kotlin.coroutines.Continuation

actual interface Write {
    suspend fun buffer(
        buffer: ByteBuffer
    )
    actual suspend fun skip(
        amount: Int
    )
    actual suspend fun bytes(
        bytes: ByteArray,
        amount: Int,
        offset: Int
    )

    actual suspend fun byte(byte: Byte)
    actual suspend fun short(short: Short)
    actual suspend fun int(int: Int)
    actual suspend fun long(long: Long)

    actual suspend fun float(float: Float)
    actual suspend fun double(double: Double)
}