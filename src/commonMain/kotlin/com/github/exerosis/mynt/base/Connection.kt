package com.github.exerosis.mynt.base

interface Connection {
    val read: Read
    val write: Write

    val address: Address

    val isOpen: Boolean
    suspend fun close()
}