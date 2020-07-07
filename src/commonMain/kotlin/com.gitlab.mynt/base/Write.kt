package com.gitlab.mynt.base

expect interface Write {
    suspend fun bytes(
        bytes: ByteArray,
        amount: Int = bytes.size,
        offset: Int = 0
    )

    suspend fun byte(byte: Byte)

    suspend fun short(short: Short)

    suspend fun int(int: Int)

    suspend fun float(float: Float)

    suspend fun long(long: Long)

    suspend fun double(double: Double)
}