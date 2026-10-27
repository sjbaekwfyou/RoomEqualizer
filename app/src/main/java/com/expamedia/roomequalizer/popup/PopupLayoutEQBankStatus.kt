package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.expamedia.roomequalizer.databinding.PopupLayoutEqBankStatusBinding
import com.expamedia.roomequalizer.device.BleDeviceManager

class PopupLayoutEQBankStatus(private val context: Context) {
    private val binding: PopupLayoutEqBankStatusBinding by lazy {
        PopupLayoutEqBankStatusBinding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            }
    }

    fun show(eqModeType: BleDeviceManager.EqModeType, listener: (() -> Boolean), listenerCancel: (() -> Boolean)) {
        if (dialog.isShowing) {
            return
        }

        when (eqModeType) {
            BleDeviceManager.EqModeType.EQ_MODE_USER1 -> binding.tvMessage.text = "Do you want to overwrite to User1?"
            BleDeviceManager.EqModeType.EQ_MODE_USER2 -> binding.tvMessage.text = "Do you want to overwrite to User2?"
            else -> return
        }

        binding.btnOK.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            if (listenerCancel.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}