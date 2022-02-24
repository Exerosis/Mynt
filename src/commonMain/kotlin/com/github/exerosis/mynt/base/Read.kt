package com.github.exerosis.mynt.base

//TODO Add number overloads?
expect interface Read {
    suspend fun skip(amount: Int)

    suspend fun bytes(
        bytes: ByteArray,
        amount: Int,
        offset: Int
    ): ByteArray

    suspend fun byte(): Byte

    suspend fun short(): Short

    suspend fun int(): Int

    suspend fun float(): Float

    suspend fun long(): Long

    suspend fun double(): Double
}