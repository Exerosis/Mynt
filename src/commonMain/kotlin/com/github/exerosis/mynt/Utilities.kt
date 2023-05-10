@file:Suppress("NOTHING_TO_INLINE")
package com.github.exerosis.mynt

import com.github.exerosis.mynt.base.Connection
import com.github.exerosis.mynt.base.Read
import com.github.exerosis.mynt.base.Write
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted

suspend inline fun <Type> continued(
    crossinline block: (Continuation<Type>) -> (Any?)
) = suspendCoroutineUninterceptedOrReturn(block)

@OptIn(InternalCoroutinesApi::class)
inline fun <Type> Continuation<Type>.intercept(
    noinline handler: CompletionHandler
) = intercepted().apply {
    context[Job]!!.invokeOnCompletion(
        onCancelling = true, invokeImmediately = true, handler
    )
}
inline val SUSPENDED get() = COROUTINE_SUSPENDED

suspend inline fun Read.bytes(amount: Int, offset: Int = 0)
    = bytes(ByteArray(amount), amount, offset)
suspend inline fun Read.bytes(bytes: ByteArray, offset: Int = 0)
    = bytes(bytes, bytes.size, offset)
suspend inline fun Write.bytes(bytes: ByteArray, offset: Int = 0)
    = bytes(bytes, bytes.size, offset)

inline operator fun Connection.component1() = read
inline operator fun Connection.component2() = write