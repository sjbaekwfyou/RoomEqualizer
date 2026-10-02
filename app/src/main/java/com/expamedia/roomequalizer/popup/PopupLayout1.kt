package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.expamedia.roomequalizer.databinding.PopupLayout1Binding

class PopupLayout1(private val context: Context) {
    private val binding: PopupLayout1Binding by lazy {
        PopupLayout1Binding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            }
    }

    fun show(listener: (() -> Unit)) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnStartMeasurement.setOnClickListener {
                listener.invoke()
                dismiss()
            }

            binding.btnCancelMeasurement.setOnClickListener {
                dismiss()
            }
        }
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}