
@file:JsModule("net")
@file:JsNonModule
package com.github.exerosis.mynt

external interface SocketAddressOptions {
    var address: String
    var family: String
    var flowlabel: Int
    var port: Int
}
external class SocketAddress(
    options: SocketAddressOptions
): SocketAddressOptions {
    override var address: String
    override var family: String
    override var flowlabel: Int
    override var port: Int
}
