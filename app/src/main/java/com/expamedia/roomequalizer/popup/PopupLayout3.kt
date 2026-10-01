package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
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
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    // 콜백 함수 정의 (다이얼로그 안에서 발생하는 이벤트를 외부로 전달할 때 사용)
    private var onConfirmListener: ((EqUser) -> Unit)? = null

    init {
        setupListeners()
    }

    private fun setupListeners() {
        // 서브 레이아웃 내부의 닫기 버튼 이벤트 (ID가 btnClose인 경우)
        binding.btnUser1.setOnClickListener {
            onConfirmListener?.invoke(EqUser.User1)
            dismiss()
        }

        binding.btnUser2.setOnClickListener {
            onConfirmListener?.invoke(EqUser.User2)
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            onConfirmListener?.invoke(EqUser.Cancel)
            dismiss()
        }
    }

    /**
     * 다이얼로그 표시
     */
    fun show() {
        if (!dialog.isShowing) {
            dialog.show()
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

    /**
     * 외부에서 다이얼로그 이벤트 결과값을 전달받는 리스너 설정
     */
    fun setOnConfirmListener(listener: (EqUser) -> Unit) {
        this.onConfirmListener = listener
    }
}