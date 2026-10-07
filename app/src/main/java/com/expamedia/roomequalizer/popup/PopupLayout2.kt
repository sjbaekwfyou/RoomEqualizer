package com.expamedia.roomequalizer.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.LifecycleOwner
import com.expamedia.roomequalizer.AudioDeviceManager
import com.expamedia.roomequalizer.R
import com.expamedia.roomequalizer.databinding.PopupLayout2Binding
import androidx.lifecycle.lifecycleScope
import com.expamedia.roomequalizer.api.NativeEqualizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopupLayout2(private val context: Context, private val audioDeviceManager: AudioDeviceManager, private val nativeEqualizer: NativeEqualizer) {
    enum class ButtonType(val message: String) {
        NONE("NONE"),
        QUIT("QUIT"),
        OK("OK"),
        RESTART("RESTART");
    }

    enum class MessageType(
        @param:DrawableRes val colorBackground: Int,
        @param:StringRes val tvMessage: Int,
        @param:StringRes val btMessage: Int,
        val btVisibility: Int,
        val buttonType: ButtonType
    ) {
        NONE(R.color.white, R.string.no_message, R.string.no_message, View.GONE, ButtonType.NONE),
        Message1(R.drawable.bg_popup_white, R.string.noti_message1, R.string.no_message, View.GONE, ButtonType.NONE),
        Message2(R.drawable.bg_popup_white, R.string.noti_message2, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message3(R.drawable.bg_popup_white, R.string.noti_message3, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message4(R.drawable.bg_popup_orange, R.string.noti_message4, R.string.ok, View.VISIBLE, ButtonType.OK),
        Message5(R.drawable.bg_popup_orange, R.string.noti_message5, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message6(R.drawable.bg_popup_orange, R.string.noti_message6, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message7(R.drawable.bg_popup_orange, R.string.noti_message7, R.string.restart, View.VISIBLE, ButtonType.RESTART);
    }

   private val binding: PopupLayout2Binding by lazy {
       PopupLayout2Binding.inflate(LayoutInflater.from(context))
    }

    private val dialog: AlertDialog by lazy {
        AlertDialog.Builder(context)
            .setView(binding.root)
            .create().apply {
                // 필요시 배경을 투명하게 만들어 둥근 모서리 적용 가능
                window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            }
    }

    private var messageType: MessageType = MessageType.NONE

    private fun setMessage(messageType: MessageType) {
        this.messageType = messageType

        binding.background.background = AppCompatResources.getDrawable(context, this.messageType.colorBackground)
        binding.tvMessage.text = context.getString(this.messageType.tvMessage)
        binding.btnButtonID.visibility = this.messageType.btVisibility
        binding.btnButtonID.text = context.getString(this.messageType.btMessage)
    }

    private fun readRecordBuffer(floatBuffer: FloatArray, listener: (FloatArray) -> Unit) {
        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val recordedFloatBuffer: FloatArray = audioDeviceManager.playAndRecord(floatBuffer)

            withContext(Dispatchers.Main) {
                listener.invoke(recordedFloatBuffer)
            }
        }
    }

    fun showMessage1(listenerOK: () -> Boolean, listenerNG: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        setMessage(MessageType.Message1)

        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val ambientRecoding = FloatArray(24000)

            readRecordBuffer(ambientRecoding) { recordedDoubleBuffer ->
                var level = nativeEqualizer.ambientLevel(recordedDoubleBuffer)
                when (level) {
                    0 -> {     //OK
                        if (listenerOK.invoke()) {
                            return@readRecordBuffer
                        }
                        dismiss()
                    }
                    else -> {     //NG
                        if (listenerNG.invoke()) {
                            return@readRecordBuffer
                        }
                        dismiss()
                    }
                }
            }
        }
    }

    fun showMessage2(chirp: FloatArray, listener: (result: FloatArray) -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        var isCanceled = false
        binding.btnButtonID.setOnClickListener {
            isCanceled = true
            dismiss()
        }

        setMessage(MessageType.Message2)

        readRecordBuffer(chirp) { result ->
            if(!isCanceled) {
                if (listener.invoke(result)) {
                    return@readRecordBuffer
                }
            }
            dismiss()
        }
    }

    fun showMessage3(chirp: FloatArray, result: FloatArray, listenerCanceled: () -> Unit, listener: (value: Int, IIRcoef: FloatArray) -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            listenerCanceled.invoke()
            dismiss()
        }

        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val IIRcoef = FloatArray(chirp.size)
            val value = nativeEqualizer.calculatePEQ(chirp, result, IIRcoef)

            withContext(Dispatchers.Main) {
                if (listener.invoke(value, IIRcoef)) {
                    return@withContext
                }
                dismiss()
            }
        }

        setMessage(MessageType.Message3)
    }

    fun showMessage4(listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message4)
    }

    fun showMessage5(listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message5)
    }

    fun showMessage6(listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message6)
    }

    fun showMessage7(listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message7)
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}