package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toDrawable
import com.expamedia.roomequalizer.databinding.PopupLayout3Binding

class PopupLayout3(private val context: Context) {
    enum class EqUser(val modeName: String) {
        User1("User1"),
        User2("User2"),
        Cancel("Cancel");
    }

    private val binding: PopupLayout3Binding by lazy {
        PopupLayout3Binding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                // 필요시 배경을 투명하게 만들어 둥근 모서리 적용 가능
                window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            }
    }

    private var onConfirmListener: ((EqUser) -> Boolean)? = null

    init {
        setupListeners()
    }

    private fun setupListeners() {
        // 서브 레이아웃 내부의 닫기 버튼 이벤트 (ID가 btnClose인 경우)
        binding.btnUser1.setOnClickListener {
            if (onConfirmListener?.invoke(EqUser.User1) == true) {
                return@setOnClickListener
            }
            dismiss()
        }

        binding.btnUser2.setOnClickListener {
            if (onConfirmListener?.invoke(EqUser.User2) == true) {
                return@setOnClickListener
            }
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            if (onConfirmListener?.invoke(EqUser.Cancel) == true) {
                return@setOnClickListener
            }
            dismiss()
        }
    }

    fun show(listener: (EqUser) -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        onConfirmListener = listener
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}