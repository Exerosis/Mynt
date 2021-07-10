package com.github.exerosis.mynt.base

interface Connection {
    val read: Read
    val write: Write
    val isOpen: Boolean

    suspend fun close()
}