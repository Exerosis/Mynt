@file:Suppress("NOTHING_TO_INLINE")
package com.gitlab.mynt

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.nio.channels.WritePendingException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

typealias Handled = (ByteBuffer, CompletionHandler<Int, ByteBuffer>) -> (Unit)

abstract class Handler<Type> : CompletionHandler<Int, ByteBuffer> {
    var continuation: Continuation<Type>? = null
    var required = 0

    inline fun canContinue(continuation: Continuation<Type>): Boolean {
        if (this.continuation != null) return false
        this.continuation = continuation; return true
    }

    inline fun complete(value: Type) {
        continuation!!.resumeWith(Result.success(value))
        continuation = null
    }

    inline fun fail(reason: Throwable) {
        continuation!!.resumeWith(Result.failure(reason))
        continuation = null
    }

    override fun failed(reason: Throwable, buffer: ByteBuffer) = fail(reason)
}

class SkipReadHandler(val read: Handled) : Handler<Unit>() {
    inline operator fun invoke(
        using: ByteBuffer,
        amount: Int,
        continuation: Continuation<Unit>
    ): Any {
        if (!canContinue(continuation)) throw ReadPendingException()
        val remaining = using.remaining()
        required = amount - remaining
        if (required < 1) return Unit
        read(using, this)
        return COROUTINE_SUSPENDED
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            buffer.position(buffer.position() + kotlin.math.abs(required))
            complete(Unit)
        }
        else read(buffer.clear() as ByteBuffer, this)
    }
}
class ArrayReadHandler(val read: Handled) : Handler<ByteArray>() {
    var offset = 0
    var array: ByteArray? = null

    operator fun invoke(
            using: ByteBuffer,
            array: ByteArray,
            offset: Int,
            amount: Int,
            continuation: Continuation<ByteArray>
    ): Any {
        if (canContinue(continuation)) throw ReadPendingException()
        val remaining = using.remaining()
        if (remaining > 0) {
            if (remaining >= amount) {
                using.get(array, offset, amount)
                return array
            }
            using.get(array, offset, remaining)
        }
        required = amount - remaining
        this.offset = offset + remaining
        this.array = array
        using.clear()
        read(using, this)
        return COROUTINE_SUSPENDED
    }

    //OLD handle current somehow
    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            val remaining = buffer.flip().remaining()
            buffer.get(array, offset, remaining + required)
            complete(array!!)
        } else {
            if (buffer.remaining() < required) {
                val remaining = buffer.flip().remaining()
                buffer.get(array, offset, remaining).clear()
                offset += remaining
            }
            read(buffer, this)
        }
    }
}
class BufferReadHandler(val read: Handled) : Handler<ByteBuffer>() {
    operator fun invoke(
            using: ByteBuffer,
            buffer: ByteBuffer,
            amount: Int,
            continuation: Continuation<ByteBuffer>
    ): Any {
        //OLD we could use required instead, but maybe nulling out is good?
        if (canContinue(continuation)) throw ReadPendingException()
        if (using.hasRemaining()) buffer.put(using)
        //OLD this won't hard limit anything...
        required = amount
        return if (required >= 1) {
            read(buffer, this)
            COROUTINE_SUSPENDED
        } else buffer
    }

    override fun completed(count: Int, destination: ByteBuffer) {
        if (count < 0) fail(ClosedChannelException())
        required -= count
        if (required < 1) complete(destination)
        else read(destination, this)
    }
}
class NumberReadHandler<Type : Number>(
        val read: Handled,
        val converter: (ByteBuffer) -> (Type)
) : Handler<Type>() {
    var mark = 0

    operator fun invoke(
            using: ByteBuffer,
            amount: Int,
            continuation: Continuation<Type>
    ): Any {
        if (canContinue(continuation)) throw ReadPendingException()
        val remaining = using.remaining()
        return if (remaining < amount) {
            required = amount - remaining
            read(using.apply {
                val capacity = capacity()
                val limit = limit()
                when {
                    remaining == 0 -> position(0)
                    capacity - limit < required -> compact()
                    else -> {
                        mark = position()
                        position(limit)
                    }
                }.limit(capacity)
            }, this)
            COROUTINE_SUSPENDED
        } else converter(using)
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            buffer.limit(buffer.position()).position(mark)
            mark = 0
            complete(converter(buffer))
        } else read(buffer, this)
    }
}

open class BufferWriteHandler(val write: Handled) : Handler<Unit>() {
    operator fun invoke(
        using: ByteBuffer,
        buffer: ByteBuffer,
        amount: Int,
        continuation: Continuation<Unit>
    ): Any {
        if (canContinue(continuation)) throw WritePendingException()
        required = buffer.flip().remaining()
        return if (required < 1) {
            buffer.clear()
            Unit
        } else {
            write(buffer, this)
            COROUTINE_SUSPENDED
        }
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        required -= count
        if (required < 1) {
            buffer.clear()
            complete(Unit)
        } else write(buffer, this)
    }
}
class NumberWriteHandler<Type : Number>(
        write: Handled,
        val converter: ByteBuffer.(Type) -> (ByteBuffer)
) : BufferWriteHandler(write) {
    operator fun invoke(
        using: ByteBuffer,
        value: Type,
        continuation: Continuation<Unit>
    ): Any {
        using.clear()
        return invoke(using, converter(using, value), 0, continuation)
    }
}