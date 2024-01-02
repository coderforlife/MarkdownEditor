package edu.moravian.markdowneditor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform