package edu.moravian.markdowneditor.android.css

import co.touchlab.kermit.Logger

internal fun warn(message: String): Nothing? {
    Logger.w(message)
    return null
}
