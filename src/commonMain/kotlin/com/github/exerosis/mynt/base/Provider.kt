package com.github.exerosis.mynt.base

interface Provider {
    suspend fun accept(address: Address): Connection

    suspend fun connect(address: Address): Connection

    val isOpen: Boolean

    suspend fun close()
}