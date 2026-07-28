package com.vectr

enum class ClickButton { LEFT, RIGHT }

interface TouchpadCommandSender {
    fun sendMove(dx: Float, dy: Float)
    fun sendClick(button: ClickButton)
    fun sendScroll(deltaY: Float)
}
