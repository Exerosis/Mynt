package com.gitlab.mynt.sequential

import com.gitlab.mynt.base.WriteCoordinator
import java.nio.ByteBuffer
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

//TODO Impl fully.
class SequentialWriteCoordinator(
        private val write: (ByteBuffer, CompletionHandler<Int, ByteBuffer>) -> Unit
) : WriteCoordinator {
    private val bufferHandler = object :
            Holder<Continuation<Unit>>(),
            CompletionHandler<Int, ByteBuffer> {
        var required = 0

        operator fun invoke(
                using: ByteBuffer,
                buffer: ByteBuffer,
                continuation: Continuation<Unit>
        ): Any {
            if (holding())
                throw InProgressException()
            required = buffer.flip().remaining()
            return if (required < 1) {
                buffer.clear()
                Unit
            } else {
                hold(continuation)
                write(buffer, this)
                COROUTINE_SUSPENDED
            }
        }

        override fun completed(count: Int, buffer: ByteBuffer) {
            required -= count
            if (required < 1) {
                buffer.clear()
                release().resume(Unit)
            } else
                write(buffer, this)
        }

        override fun failed(reason: Throwable, buffer: ByteBuffer) {
            release().resumeWithException(reason)
        }
    }

    override fun array(
            using: ByteBuffer,
            array: ByteArray,
            amount: Int,
            offset: Int,
            continuation: Continuation<Unit>
    ): Any {
        val buffer = ByteBuffer.wrap(array, offset, amount)
        buffer.position(amount)
        return bufferHandler(using, buffer, continuation)
    }

    override fun buffer(
            using: ByteBuffer,
            buffer: ByteBuffer,
            continuation: Continuation<Unit>
    ) = bufferHandler(using, buffer, continuation)

    override fun <Type : Number> number(
            using: ByteBuffer,
            value: Type,
            writer: (Type, ByteBuffer) -> ByteBuffer,
            continuation: Continuation<Unit>
    ): Any {
        using.clear()
        return bufferHandler(using, writer(value, using), continuation)
    }
}