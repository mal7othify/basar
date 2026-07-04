package com.mal7othify.basar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
