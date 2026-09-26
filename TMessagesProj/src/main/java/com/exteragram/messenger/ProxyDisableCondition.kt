package com.exteragram.messenger

enum class ProxyDisableCondition(val flag: Int) {
    VPN(1),
    MOBILE_DATA(2),
    WIFI(4)
}
