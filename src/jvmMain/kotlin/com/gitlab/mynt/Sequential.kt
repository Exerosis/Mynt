@file:Suppress("NOTHING_TO_INLINE")
package com.gitlab.mynt

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.CompletionHandler
import java.nio.channels.ReadPendingException
import java.nio.channels.WritePendingException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.math.abs

typealias Handled = (ByteBuffer, CompletionHandler<Int, ByteBuffer>) -> (Unit)

abstract class Handler<Type>(val error: (Throwable) -> (Unit)) : CompletionHandler<Int, ByteBuffer> {
    var continuation: Continuation<Type>? = null
    var required = 0

    inline fun canContinue() = this.continuation == null
    inline fun suspend(continuation: Continuation<Type>): Any {
        this.continuation = continuation
        return COROUTINE_SUSPENDED
    }

    inline fun complete(value: Type) {
        val current = continuation!!
        continuation = null
        current.resumeWith(Result.success(value))
    }

    inline fun fail(reason: Throwable) {
        val current = continuation!!
        continuation = null
        current.resumeWith(Result.failure(reason))
    }

    override fun failed(reason: Throwable, buffer: ByteBuffer) = fail(reason)
}

class SkipReadHandler(val read: Handled, error: (Throwable) -> (Unit)) : Handler<Unit>(error) {
    inline operator fun invoke(
        using: ByteBuffer,
        amount: Int,
        continuation: Continuation<Unit>
    ): Any {
        if (!canContinue()) throw ReadPendingException()
        val remaining = using.remaining()
        required = amount - remaining
        if (required < 1) {
            using.position(using.position() + amount)
            return Unit
        }
        read(using, this)
        return suspend(continuation)
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            buffer.position(buffer.position() + abs(required))
            complete(Unit)
        }
        else read(buffer.clear() as ByteBuffer, this)
    }
}
class ArrayReadHandler(val read: Handled, error: (Throwable) -> (Unit)) : Handler<ByteArray>(error) {
    var offset = 0
    var array: ByteArray? = null

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
        read(using, this)
        return suspend(continuation)
    }

    //OLD handle current somehow
    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return fail(ClosedChannelException())
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
class BufferReadHandler(val read: Handled, error: (Throwable) -> (Unit)) : Handler<ByteBuffer>(error) {
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
        return if (required >= 1) {
            read(buffer, this)
            suspend(continuation)
        } else buffer
    }

    override fun completed(count: Int, destination: ByteBuffer) {
        if (count < 0) return fail(ClosedChannelException())
        required -= count
        if (required < 1) complete(destination)
        else read(destination, this)
    }
}
class NumberReadHandler<Type : Number>(
        val read: Handled,
        error: (Throwable) -> (Unit),
        val converter: (ByteBuffer) -> (Type)
) : Handler<Type>(error) {
    var mark = 0

    operator fun invoke(
            using: ByteBuffer,
            amount: Int,
            continuation: Continuation<Type>
    ): Any {
        if (!canContinue()) throw ReadPendingException()
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
            suspend(continuation)
        } else converter(using)
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            buffer.limit(buffer.position()).position(mark)
            mark = 0
            complete(converter(buffer))
        } else read(buffer, this)
    }
}

open class BufferWriteHandler(val write: Handled, error: (Throwable) -> (Unit)) : Handler<Unit>(error) {
    operator fun invoke(
        using: ByteBuffer,
        buffer: ByteBuffer,
        continuation: Continuation<Unit>
    ): Any {
        if (!canContinue()) throw WritePendingException()
        required = buffer.flip().remaining()
        return if (required < 1) {
            buffer.clear()
            Unit
        } else {
            write(buffer, this)
            suspend(continuation)
        }
    }

    override fun completed(count: Int, buffer: ByteBuffer) {
        if (count < 0) return fail(ClosedChannelException())
        required -= count
        if (required < 1) {
            buffer.clear()
            complete(Unit)
        } else write(buffer, this)
    }
}
class NumberWriteHandler<Type : Number>(
        write: Handled,
        error: (Throwable) -> (Unit),
        val converter: ByteBuffer.(Type) -> (ByteBuffer)
) : BufferWriteHandler(write, error) {
    operator fun invoke(
        using: ByteBuffer,
        value: Type,
        continuation: Continuation<Unit>
    ): Any {
        using.clear()
        return invoke(using, converter(using, value), continuation)
    }
}