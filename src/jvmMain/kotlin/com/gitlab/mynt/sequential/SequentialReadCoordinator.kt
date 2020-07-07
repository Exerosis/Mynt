package com.gitlab.mynt.sequential

import com.gitlab.mynt.base.ReadCoordinator
import java.nio.ByteBuffer
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("UNCHECKED_CAST")
class SequentialReadCoordinator(
        private val read: (ByteBuffer, CompletionHandler<Int, ByteBuffer>) -> Unit
) : ReadCoordinator {
    private abstract class Handler<Type> : Holder<Continuation<Type>>(), CompletionHandler<Int, ByteBuffer> {
        var required = 0

        override fun failed(reason: Throwable, buffer: ByteBuffer)
            = release().resumeWithException(reason)
    }

    private val skipHandler = object : Handler<Unit>() {
        operator fun invoke(
                using: ByteBuffer,
                amount: Int,
                continuation: Continuation<Unit>
        ): Any {
            if (holding()) throw InProgressException()

            val remaining = using.remaining()
            required = amount - remaining
            if (required < 1) return Unit
            hold(continuation); read(using, this)
            return COROUTINE_SUSPENDED
        }

        override fun completed(count: Int, buffer: ByteBuffer) {
            required -= count;
            if (required < 1) {
                buffer.position(buffer.position() + kotlin.math.abs(required))
                release().resume(Unit)
            }
            else read(buffer.clear() as ByteBuffer, this)
        }
    }
    private val arrayHandler = object : Handler<ByteArray>() {
        var offset = 0
        var array: ByteArray? = null

        operator fun invoke(
                using: ByteBuffer,
                array: ByteArray,
                offset: Int,
                amount: Int,
                continuation: Continuation<ByteArray>
        ): Any {
            if (holding())
                throw InProgressException()
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

            hold(continuation); read(using, this)
            return COROUTINE_SUSPENDED
        }

        //TODO handle current somehow
        override fun completed(count: Int, buffer: ByteBuffer) {
            required -= count
            if (required < 1) {
                val remaining = buffer.flip().remaining()
                buffer.get(array, offset, remaining + required)
                release().resume(array!!)
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
    private val bufferHandler = object : Handler<ByteBuffer>() {
        operator fun invoke(
                using: ByteBuffer,
                buffer: ByteBuffer,
                amount: Int,
                continuation: Continuation<ByteBuffer>
        ): Any {
            //TODO we could use required instead, but maybe nulling out is good?
            if (holding())
                throw InProgressException()
            if (using.hasRemaining())
                buffer.put(using)
            //FIXME this won't hard limit anything...
            required = amount
            return if (required < 1)
                buffer
            else {
                hold(continuation)
                read(buffer, this)
                COROUTINE_SUSPENDED
            }
        }

        override fun completed(count: Int, destination: ByteBuffer) {
            required -= count
            if (required < 1) {
                release().resume(destination)
            } else {
                read(destination, this)
            }
        }
    }
    private val numberHandler = object : Handler<Number>() {
        var converter: ((ByteBuffer) -> Number)? = null
        var mark = 0

        operator fun invoke(
                using: ByteBuffer,
                amount: Int,
                converter: (ByteBuffer) -> Number,
                continuation: Continuation<Number>
        ): Any {
            if (holding())
                throw InProgressException()
            val remaining = using.remaining()
            return if (remaining >= amount)
                converter(using)
            else {
                hold(continuation)
                required = amount - remaining
                this.converter = converter
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
            }
        }

        override fun completed(count: Int, buffer: ByteBuffer) {
            required -= count
            if (required < 1) {
                buffer.limit(buffer.position()).position(mark)
                mark = 0
                val number = converter!!(buffer)
                converter = null
                release().resume(number)
            } else
                read(buffer, this)
        }
    }

    override fun skip(
            using: ByteBuffer,
            amount: Int,
            continuation: Continuation<Unit>
    ) = skipHandler(using, amount, continuation)

    override fun buffer(
            using: ByteBuffer,
            buffer: ByteBuffer,
            amount: Int,
            continuation: Continuation<ByteBuffer>
    ) = bufferHandler(using, buffer, amount, continuation)

    override fun array(
            using: ByteBuffer,
            array: ByteArray,
            amount: Int,
            offset: Int,
            continuation: Continuation<ByteArray>
    ) = arrayHandler(using, array, offset, amount, continuation)

    override fun <Type : Number> number(
            using: ByteBuffer,
            amount: Int,
            reader: (ByteBuffer) -> Type,
            continuation: Continuation<Type>
    ) = numberHandler(using, amount, reader, continuation as Continuation<Number>)
}