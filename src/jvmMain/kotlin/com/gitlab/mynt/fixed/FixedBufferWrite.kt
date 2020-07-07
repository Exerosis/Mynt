package com.gitlab.mynt.fixed

import com.gitlab.mynt.base.Write
import com.gitlab.mynt.base.WriteCoordinator
import com.gitlab.mynt.continued
import java.nio.ByteBuffer

open class FixedBufferWrite(
    private val write: WriteCoordinator,
    private val buffer: ByteBuffer
) : Write {
    companion object {
        val PUT_BYTE = { value: Byte, buffer: ByteBuffer -> buffer.put(value) }
        val PUT_SHORT = { value: Short, buffer: ByteBuffer -> buffer.putShort(value) }
        val PUT_INT = { value: Int, buffer: ByteBuffer -> buffer.putInt(value) }
        val PUT_FLOAT = { value: Float, buffer: ByteBuffer -> buffer.putFloat(value) }
        val PUT_LONG = { value: Long, buffer: ByteBuffer -> buffer.putLong(value) }
        val PUT_DOUBLE = { value: Double, buffer: ByteBuffer -> buffer.putDouble(value) }
    }

    override suspend fun bytes(
            bytes: ByteArray,
            amount: Int,
            offset: Int
    ) = continued<Unit> {
        write.array(buffer, bytes, amount, offset, it)
    }

    override suspend fun buffer(
            buffer: ByteBuffer,
            amount: Int
    ) = continued<Unit> {
        write.buffer(this.buffer, buffer, it)
    }

    override suspend fun byte(byte: Byte) = continued<Unit> {
        write.number(buffer, byte, PUT_BYTE, it)
    }

    override suspend fun short(short: Short) = continued<Unit> {
        write.number(buffer, short, PUT_SHORT, it)
    }

    override suspend fun int(int: Int) = continued<Unit> {
        write.number(buffer, int, PUT_INT, it)
    }

    override suspend fun float(float: Float) = continued<Unit> {
        write.number(buffer, float, PUT_FLOAT, it)
    }

    override suspend fun long(long: Long) = continued<Unit> {
        write.number(buffer, long, PUT_LONG, it)
    }

    override suspend fun double(double: Double) = continued<Unit> {
        write.number(buffer, double, PUT_DOUBLE, it)
    }
}