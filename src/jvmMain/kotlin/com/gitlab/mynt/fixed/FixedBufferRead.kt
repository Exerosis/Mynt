package com.gitlab.mynt.fixed

import com.gitlab.mynt.base.Read
import com.gitlab.mynt.base.ReadCoordinator
import com.gitlab.mynt.continued
import java.nio.ByteBuffer

//TODO this probably can't stay static forever.
open class FixedBufferRead(
    private val read: ReadCoordinator,
    private val buffer: ByteBuffer
) : Read {
    companion object {
        val GET_BYTE = { buffer: ByteBuffer -> buffer.get() }
        val GET_SHORT = { buffer: ByteBuffer -> buffer.short }
        val GET_INT = { buffer: ByteBuffer -> buffer.int }
        val GET_FLOAT = { buffer: ByteBuffer -> buffer.float }
        val GET_LONG = { buffer: ByteBuffer -> buffer.long }
        val GET_DOUBLE = { buffer: ByteBuffer -> buffer.double }
    }

    override suspend fun skip(amount: Int) = continued<Unit> {
        read.skip(buffer, amount, it)
    }

    override suspend fun buffer(
            buffer: ByteBuffer,
            amount: Int
    ) = continued<ByteBuffer> {
        read.buffer(this.buffer, buffer, amount, it)
    }

    override suspend fun bytes(
            bytes: ByteArray,
            amount: Int,
            offset: Int
    ) = continued<ByteArray> {
        read.array(buffer, bytes, amount, offset, it)
    }

    override suspend fun byte() = continued<Byte> {
        read.number(buffer, 1, GET_BYTE, it)
    }

    override suspend fun short() = continued<Short> {
        read.number(buffer, 2, GET_SHORT, it)
    }

    override suspend fun int() = continued<Int> {
        read.number(buffer, 4, GET_INT, it)
    }

    override suspend fun float() = continued<Float> {
        read.number(buffer, 4, GET_FLOAT, it)
    }

    override suspend fun long() = continued<Long> {
        read.number(buffer, 8, GET_LONG, it)
    }

    override suspend fun double() = continued<Double> {
        read.number(buffer, 8, GET_DOUBLE, it)
    }
}