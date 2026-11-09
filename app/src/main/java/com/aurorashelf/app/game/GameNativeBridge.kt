package com.aurorashelf.app.game

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Native bridge for high-fidelity tactile haptics, lifecycle hooks, and dialogs.
 */
class GameNativeBridge(private val activity: Activity) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @JavascriptInterface
    fun haptic(style: String?) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        activity.runOnUiThread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val effect = when (style?.lowercase()) {
                        "light", "selection" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        "heavy" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                        "success", "bigwin" -> VibrationEffect.createWaveform(longArrayOf(0, 45, 80, 70), -1)
                        "warning", "nearmiss" -> VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1)
                        "error", "defeat" -> VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120), -1)
                        else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    }
                    v.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    when (style?.lowercase()) {
                        "light", "selection" -> v.vibrate(15)
                        "heavy" -> v.vibrate(50)
                        "success", "bigwin" -> v.vibrate(longArrayOf(0, 45, 80, 70), -1)
                        else -> v.vibrate(30)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    @JavascriptInterface
    fun exitGame() {
        activity.runOnUiThread {
            activity.finish()
        }
    }

    @JavascriptInterface
    fun toast(msg: String?) {
        if (msg.isNullOrBlank()) return
        activity.runOnUiThread {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
