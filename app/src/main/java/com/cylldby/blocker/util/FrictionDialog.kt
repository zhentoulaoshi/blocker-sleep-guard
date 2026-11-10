package com.cylldby.blocker.util

import android.app.AlertDialog
import android.content.Context
import android.os.CountDownTimer
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FrictionDialog {

    private const val COUNTDOWN_SECONDS = 5L

    /**
     * Show a confirmation dialog with a countdown before the destructive action is allowed.
     * [onConfirmed] fires only after the countdown completes AND the user taps Disable.
     * [onCancelled] fires if the user cancels or dismisses.
     */
    fun show(
        context: Context,
        appName: String,
        onConfirmed: () -> Unit,
        onCancelled: () -> Unit
    ) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Disable block?")
            .setMessage("Unblocking “$appName” — are you sure?")
            .setPositiveButton("Disable (${COUNTDOWN_SECONDS}s)") { _, _ -> onConfirmed() }
            .setNegativeButton("Keep blocked") { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .create()

        dialog.show()

        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false

        val timer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000 + 1
                btn.text = "Disable (${s}s)"
            }
            override fun onFinish() {
                btn.text = "Disable"
                btn.isEnabled = true
            }
        }
        timer.start()
        dialog.setOnDismissListener { timer.cancel() }
    }
}
