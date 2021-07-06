@file:Suppress("NOTHING_TO_INLINE")
package com.gitlab.mynt

import com.gitlab.mynt.base.Connection
import com.gitlab.mynt.base.Read
import com.gitlab.mynt.base.Write
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

suspend inline fun <Type> continued(
    crossinline block: (Continuation<Type>) -> (Any?)
) = suspendCoroutineUninterceptedOrReturn(block)

suspend inline fun Read.bytes(amount: Int, offset: Int = 0)
    = bytes(ByteArray(amount), amount, offset)
suspend inline fun Read.bytes(bytes: ByteArray, offset: Int = 0)
    = bytes(bytes, bytes.size, offset)
suspend inline fun Write.bytes(bytes: ByteArray, offset: Int = 0)
    = bytes(bytes, bytes.size, offset)

inline operator fun Connection.component1() = read
inline operator fun Connection.component2() = write