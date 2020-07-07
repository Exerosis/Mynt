package com.gitlab.mynt.base

interface Provider<Address> {
    suspend fun accept(address: Address): Connection

    suspend fun connect(address: Address): Connection

    val isOpen: Boolean

    suspend fun close()
}