package com.gitlab.mynt

import com.gitlab.mynt.base.Connection
import com.gitlab.mynt.base.Read
import com.gitlab.mynt.base.Write
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

suspend inline fun <Type> continued(
        crossinline block: (Continuation<Type>) -> Any?
) = suspendCoroutineUninterceptedOrReturn(block)

suspend fun Read.bytes(
        amount: Int,
        offset: Int = 0
) = bytes(ByteArray(amount), amount, offset)

typealias ReadBlock = suspend Read.() -> Unit
typealias WriteBlock = suspend Write.() -> Unit

inline fun Connection.read(
        block: Read.() -> Unit
) = block(read)

inline fun Connection.write(
        block: Write.() -> Unit
) = block(write)