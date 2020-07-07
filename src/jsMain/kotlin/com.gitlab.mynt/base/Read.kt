package com.gitlab.mynt.base

//TODO Add number overloads?
actual interface Read {
    actual suspend fun skip(amount: Int)
    actual suspend fun bytes(bytes: ByteArray, amount: Int, offset: Int): ByteArray
    actual suspend fun byte(): Byte
    actual suspend fun short(): Short
    actual suspend fun int(): Int
    actual suspend fun float(): Float
    actual suspend fun long(): Long
    actual suspend fun double(): Double
}