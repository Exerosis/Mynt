package com.github.exerosis.mynt


external fun require(module: String): dynamic

inline fun js(block: dynamic.() -> (Unit)): dynamic {
    val instance = js("{}")
    block(instance)
    return instance
}

fun allocate(size: Int) = js("Buffer.alloc(size);")

fun main() {
    val net = require("net")
    println("Hello, World!")
//    println(SocketAddress(js{
//        port = 25577
//        address = "localhost"
//    }))
    println(js("Object.getOwnPropertyNames(net);"))
    val server = js("""
        new net.SocketAddress({
           address: 'localhost',
           port: 25577
        });
    """)
    println(server)
}