package com.gitlab.mynt.base

actual interface Write {
    actual suspend fun bytes(bytes: ByteArray, amount: Int, offset: Int)
    actual suspend fun byte(byte: Byte)
    actual suspend fun short(short: Short)
    actual suspend fun int(int: Int)
    actual suspend fun float(float: Float)
    actual suspend fun long(long: Long)
    actual suspend fun double(double: Double)
}