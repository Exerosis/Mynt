@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")
package com.github.exerosis.mynt

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.nio.channels.WritePendingException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.math.abs

typealias Handled = (ByteBuffer, CompletionHandler<Int, ByteBuffer>) -> (Unit)
abstract class Handler<Type>() : CompletionHandler<Int, ByteBuffer> {
    @Volatile var continuation: Continuation<Type>? = null
    @Volatile var required = 0

    inline fun canContinue() = this.continuation == null
    inline fun suspend(continuation: Continuation<Type>, block: () -> (Unit)): Any {
        this.continuation = continuation; block(); return SUSPENDED
    }
    inline fun complete(value: Type) {
        val current = continuation!!
        continuation = null
        current.resumeWith(Result.success(value))
    }

    final override inline fun failed(reason: Throwable, buffer: ByteBuffer) {
        val current = continuation!!
        continuation = null
        current.resumeWith(Result.failure(reason))
    }
}

class SkipReadHandler(val read: Handled) : Handler<Unit>() {
    inline operator fun invoke(
        using: ByteBuffer,
        amount: Int,
        continuation: Continuation<Unit>
    ): Any {
        if (!canContinue()) throw ReadPendingException()
        val remaining = using.remaining()
        required = amount - remaining
        return if (required < 1) Unit.apply {
            using.position(using.position() + amount)
        } else suspend(continuation) { read(using, this) }
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return failed(ClosedChannelException(), buffer)
        required -= count
        if (required < 1) {
            buffer.position(buffer.position() + abs(required))
            complete(Unit)
        }
        else read(buffer.clear() as ByteBuffer, this)
    }
}
class ArrayReadHandler(val read: Handled) : Handler<ByteArray>() {
    @Volatile var offset = 0
    @Volatile var array: ByteArray? = null

    operator fun invoke(
            using: ByteBuffer,
            array: ByteArray,
            amount: Int,
            offset: Int,
            continuation: Continuation<ByteArray>
    ): Any {
        if (!canContinue()) throw ReadPendingException()
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
        return suspend(continuation) { read(using, this) }
    }

    //OLD handle current somehow
    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return failed(ClosedChannelException(), buffer)
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
            continuation: Continuation<ByteBuffer>
    ): Any {
        //OLD we could use required instead, but maybe nulling out is good?
        if (!canContinue()) throw ReadPendingException()
        if (using.hasRemaining()) buffer.put(using)
        //OLD this won't hard limit anything...
        required = buffer.remaining()
        return if (required < 1) buffer
        else suspend(continuation) { read(buffer, this) }
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return failed(ClosedChannelException(), buffer)
        required -= count
        if (required < 1) complete(buffer)
        else read(buffer, this)
    }
}
class NumberReadHandler<Type : Number>(
    val read: Handled, val converter: (ByteBuffer) -> (Type)
) : Handler<Type>() {
    @Volatile var mark = 0

    operator fun invoke(
            using: ByteBuffer,
            amount: Int,
            continuation: Continuation<Type>
    ): Any {
        if (!canContinue()) throw ReadPendingException()
        val remaining = using.remaining()
        return if (remaining < amount) {
            required = amount - remaining
            suspend(continuation) { read(using.apply {
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
            }, this) }
        } else converter(using)
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return failed(ClosedChannelException(), buffer)
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
        continuation: Continuation<Unit>
    ): Any {
        if (!canContinue()) throw WritePendingException()
        required = buffer.flip().remaining()
        return if (required < 1) Unit.apply { buffer.clear() }
        else suspend(continuation) { write(buffer, this) }
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return failed(ClosedChannelException(), buffer)
        required -= count
        if (required < 1) {
            buffer.clear()
            complete(Unit)
        } else write(buffer, this)
    }
}
class NumberWriteHandler<Type : Number>(
    write: Handled, val converter: ByteBuffer.(Type) -> (ByteBuffer)
) : BufferWriteHandler(write) {
    operator fun invoke(
        using: ByteBuffer,
        value: Type,
        continuation: Continuation<Unit>
    ): Any {
        using.clear()
        return invoke(using, converter(using, value), continuation)
    }
}