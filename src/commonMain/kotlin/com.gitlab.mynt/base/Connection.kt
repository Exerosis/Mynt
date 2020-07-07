package com.gitlab.mynt.base

interface Connection {
    val read: Read
    val write: Write
    val isOpen: Boolean

    suspend fun close()
}