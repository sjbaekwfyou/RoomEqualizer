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
import com.expamedia.roomequalizer.device.AudioDeviceManager
import com.expamedia.roomequalizer.R
import com.expamedia.roomequalizer.databinding.PopupLayout2Binding
import androidx.lifecycle.lifecycleScope
import com.expamedia.roomequalizer.native.NativeEqualizer
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
        @param:StringRes val btMessage: Int,
        val btVisibility: Int,
        val buttonType: ButtonType
    ) {
        NONE(R.color.white, R.string.no_message, View.GONE, ButtonType.NONE),
        Message1(R.drawable.bg_popup_white, R.string.no_message, View.GONE, ButtonType.NONE),
        Message2(R.drawable.bg_popup_white, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message3(R.drawable.bg_popup_white, R.string.quit, View.VISIBLE, ButtonType.QUIT),
        Message4(R.drawable.bg_popup_orange, R.string.ok, View.VISIBLE, ButtonType.OK),
        Message5(R.drawable.bg_popup_orange, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message6(R.drawable.bg_popup_orange, R.string.restart, View.VISIBLE, ButtonType.RESTART),
        Message7(R.drawable.bg_popup_orange, R.string.restart, View.VISIBLE, ButtonType.RESTART);
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

    private fun getTVMessage(messageType: MessageType, value: Int): String {
        return when (messageType) {
            MessageType.NONE -> ""
            MessageType.Message1 -> context.getString(R.string.noti_message1)
            MessageType.Message2 -> context.getString(R.string.noti_message2)
            MessageType.Message3 -> context.getString(R.string.noti_message3)
            MessageType.Message4 -> context.getString(R.string.noti_message4)
            MessageType.Message5 -> context.getString(R.string.noti_message5)
            MessageType.Message6 -> "Please increase main volume by $value dB and try again."
            MessageType.Message7 -> "Please decrease main volume by $value dB and try again."
        }
    }

    private fun setMessage(messageType: MessageType, value: Int) {
        this.messageType = messageType

        binding.background.background = AppCompatResources.getDrawable(context, this.messageType.colorBackground)
        binding.tvMessage.text = getTVMessage(this.messageType, value)
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

        setMessage(MessageType.Message1,0)

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

    fun showMessage2(chirp: FloatArray, listenerQuit: () -> Boolean, listener: (result: FloatArray) -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        var isCanceled = false
        binding.btnButtonID.setOnClickListener {
            isCanceled = true

            if (listenerQuit.invoke()) {
                return@setOnClickListener
            }

            dismiss()
        }

        setMessage(MessageType.Message2,0)

        readRecordBuffer(chirp) { result ->
            if(!isCanceled) {
                if (listener.invoke(result)) {
                    return@readRecordBuffer
                }
            }
            dismiss()
        }
    }

    fun showMessage3(chirp: FloatArray, result: FloatArray, listenerCanceled: () -> Boolean, listener: (value: Int, dbValue: Int, IIRcoef: FloatArray) -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listenerCanceled.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val IIRcoef = FloatArray(chirp.size)
            val (value, dbValue) = nativeEqualizer.calculatePEQ(chirp, result, IIRcoef)

            withContext(Dispatchers.Main) {
                if (listener.invoke(value, dbValue, IIRcoef)) {
                    return@withContext
                }
                dismiss()
            }
        }

        setMessage(MessageType.Message3,0)
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

        setMessage(MessageType.Message4,0)
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

        setMessage(MessageType.Message5,0)
    }

    fun showMessage6(dbValue: Int, listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message6, dbValue)
    }

    fun showMessage7(dbValue: Int, listener: () -> Boolean) {
        if (!dialog.isShowing) {
            dialog.show()
        }

        binding.btnButtonID.setOnClickListener {
            if (listener.invoke()) {
                return@setOnClickListener
            }
            dismiss()
        }

        setMessage(MessageType.Message7, dbValue)
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}