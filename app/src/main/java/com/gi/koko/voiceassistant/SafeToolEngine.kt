package com.gi.koko.voiceassistant

import android.content.Context
import android.content.Intent

object SafeToolEngine {
    private val termuxCommands = mapOf(
        "pwd" to listOf("pwd"),
        "ls" to listOf("ls", "-la"),
        "python --version" to listOf("python", "--version")
    )

    fun calculate(expression: String): Double {
        return ExpressionParser(expression).parse()
    }

    fun openApp(context: Context, packageName: String): String {
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "Invalid package name" }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("App not found: $packageName")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return "App launch requested: $packageName"
    }

    fun runTermuxAllowlisted(context: Context, command: String) {
        val args = termuxCommands[command] ?: throw IllegalArgumentException("Command is not on the safe allowlist")
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setPackage("com.termux")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/" + args.first())
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", ArrayList(args.drop(1)))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        context.sendBroadcast(intent, "com.termux.permission.RUN_COMMAND")
    }
}

private class ExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val result = expression()
        skipSpaces()
        require(index == source.length) { "Unexpected character in expression" }
        require(result.isFinite()) { "Result is not finite" }
        return result
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipSpaces()
            value = when {
                take('+') -> value + term()
                take('-') -> value - term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = factor()
        while (true) {
            skipSpaces()
            value = when {
                take('*') -> value * factor()
                take('/') -> {
                    val divisor = factor()
                    require(divisor != 0.0) { "Division by zero" }
                    value / divisor
                }
                else -> return value
            }
        }
    }

    private fun factor(): Double {
        skipSpaces()
        if (take('+')) return factor()
        if (take('-')) return -factor()
        if (take('(')) {
            val value = expression()
            skipSpaces()
            require(take(')')) { "Missing closing parenthesis" }
            return value
        }
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        require(index > start) { "Number expected" }
        return source.substring(start, index).toDouble()
    }

    private fun take(char: Char): Boolean {
        if (index < source.length && source[index] == char) {
            index++
            return true
        }
        return false
    }

    private fun skipSpaces() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
}
