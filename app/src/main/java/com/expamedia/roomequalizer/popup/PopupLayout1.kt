package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import com.expamedia.roomequalizer.databinding.PopupLayout1Binding

class PopupLayout1(private val context: Context) {
    private val binding: PopupLayout1Binding by lazy {
        PopupLayout1Binding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                // 필요시 배경을 투명하게 만들어 둥근 모서리 적용 가능
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    /**
     * 다이얼로그 표시
     */
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

    /**
     * 다이얼로그 닫기
     */
    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}