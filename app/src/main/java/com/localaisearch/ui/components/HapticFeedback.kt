package com.localaisearch.ui.components

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Small, system-aware tactile feedback used for important UI interactions.
 * Uses Android's haptic feedback channel, so it follows the user's system
 * vibration/haptics settings and does not require VIBRATE permission.
 */
object AppHaptics {
    fun tap(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun confirm(view: View?) {
        val constant = if (android.os.Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view?.performHapticFeedback(constant)
    }

    fun reject(view: View?) {
        val constant = if (android.os.Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view?.performHapticFeedback(constant)
    }
}
