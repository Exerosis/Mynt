package com.gitlab.mynt.sequential

open class Holder<Type> {
    private var value: Type? = null

    fun holding(): Boolean {
        return value != null
    }

    fun hold(value: Type) {
        this.value = value
    }

    fun release(): Type {
        val temp = value!!
        value = null
        return temp
    }
}