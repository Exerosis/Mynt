package com.gitlab.mynt.base

import java.nio.ByteBuffer
import kotlin.coroutines.Continuation

//TODO do we really want these?
interface ReadCoordinator {
    fun skip(
            using: ByteBuffer,
            amount: Int,
            continuation: Continuation<Unit>
    ): Any
    fun buffer(
            using: ByteBuffer,
            buffer: ByteBuffer,
            amount: Int,
            continuation: Continuation<ByteBuffer>
    ): Any

    fun array(
            using: ByteBuffer,
            array: ByteArray,
            amount: Int,
            offset: Int,
            continuation: Continuation<ByteArray>
    ): Any

    //TODO maybe clean this up somehow?
    fun <Type : Number> number(
            using: ByteBuffer,
            amount: Int,
            reader: (ByteBuffer) -> Type,
            continuation: Continuation<Type>
    ): Any
}