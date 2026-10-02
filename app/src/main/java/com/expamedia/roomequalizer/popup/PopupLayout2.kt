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

    private fun readRecordBuffer(floatBuffer: FloatArray, listener: (DoubleArray) -> Unit) {
        (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
            val recordedFloatBuffer: FloatArray = audioDeviceManager.playAndRecord(floatBuffer)
            val recordedDoubleBuffer = DoubleArray(recordedFloatBuffer.size) { i -> recordedFloatBuffer[i].toDouble() }

            withContext(Dispatchers.Main) {
                listener.invoke(recordedDoubleBuffer)
            }
        }
    }

    fun showMessage1(listenerOK: () -> Unit, listenerNG: () -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            setMessage(MessageType.Message1)

            (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
                val ambientRecoding = FloatArray(24000)

                readRecordBuffer(ambientRecoding) { recordedDoubleBuffer ->
                    var level = nativeEqualizer.ambientLevel(recordedDoubleBuffer)
                    when (level) {
                        0 -> {     //OK
                            listenerOK.invoke()
                            dismiss()
                        }
                        else -> {     //NG
                            listenerNG.invoke()
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    fun showMessage2(chirp: DoubleArray, listener: (result: DoubleArray) -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            var isCanceled = false
            binding.btnButtonID.setOnClickListener {
                isCanceled = true
                dismiss()
            }

            setMessage(MessageType.Message2)

            val floatBuffer = FloatArray(chirp.size) { i -> chirp[i].toFloat() }
            readRecordBuffer(floatBuffer) { result ->
                if(!isCanceled) {
                    listener.invoke(result)
                }
                dismiss()
            }
        }
    }

    fun showMessage3(chirp: DoubleArray, result: DoubleArray, listenerCanceled: () -> Unit, listener: (value: Int, IIRcoef: DoubleArray) -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnButtonID.setOnClickListener {
                listenerCanceled.invoke()
                dismiss()
            }

            (context as? LifecycleOwner)?.lifecycleScope?.launch(Dispatchers.IO) {
                val IIRcoef = DoubleArray(chirp.size)
                val value = nativeEqualizer.calculatePEQ(chirp, result, IIRcoef)

                withContext(Dispatchers.Main) {
                    listener.invoke(value, IIRcoef)
                    dismiss()
                }
            }

            setMessage(MessageType.Message3)
        }
    }

    fun showMessage4(listener: () -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnButtonID.setOnClickListener {
                listener.invoke()
                dismiss()
            }

            setMessage(MessageType.Message4)
        }
    }

    fun showMessage5(listener: () -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnButtonID.setOnClickListener {
                listener.invoke()
                dismiss()
            }

            setMessage(MessageType.Message5)
        }
    }

    fun showMessage6(listener: () -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnButtonID.setOnClickListener {
                listener.invoke()
                dismiss()
            }

            setMessage(MessageType.Message6)
        }
    }

    fun showMessage7(listener: () -> Unit) {
        if (!dialog.isShowing) {
            dialog.show()

            binding.btnButtonID.setOnClickListener {
                listener.invoke()
                dismiss()
            }

            setMessage(MessageType.Message7)
        }
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}