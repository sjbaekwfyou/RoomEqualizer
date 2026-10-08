package com.expamedia.roomequalizer.api

import android.content.Context
import android.util.Log
import com.expamedia.roomequalizer.device.BleDeviceManager
import java.io.ByteArrayOutputStream

class BleApi(val context: Context, val bleDeviceManager: BleDeviceManager) {
    private final val TAG = "BleApi"

    enum class ModeType {
        PLAY_MODE,
        MEASUREMENT_MODE
    }

    enum class EqModeType {
        EQ_MODE_DEFAULT,
        EQ_MODE_USER1,
        EQ_MODE_USER2
    }

    private val parseCmdDatas = mutableListOf<Byte>()
    fun parseCmd(cmd: ByteArray, listenerMode: (ModeType) -> Unit, listenerEqMode: (EqModeType) -> Unit, listenerGetEqMode: (EqModeType) -> Unit) {
        parseCmdDatas.addAll(cmd.toList())

        //* App → BT {0xec, 0xb0, 0x05, 0x5e}, BT → App Return {0xed, 0xb0, 0x05, 0x5d}
        while(parseCmdDatas.size >= 4) {
            val extracted = parseCmdDatas.subList(0, 4).toByteArray()
            parseCmdDatas.subList(0, 4).clear()

            Log.d(TAG, "parseCmd. extracted: ${extracted.toList()}")

            if (extracted[0] == 0xed.toByte()) {
                if (extracted[1] == 0xa0.toByte()) {
                    if (extracted[2] == 0x01.toByte() && extracted[3] == 0x71.toByte()) {
                        Log.d(TAG, "parseCmd. ModeType.PLAY_MODE")

                        listenerMode(ModeType.PLAY_MODE)
                    }
                    else if (extracted[2] == 0x02.toByte() && extracted[3] == 0x70.toByte()) {
                        Log.d(TAG, "parseCmd. ModeType.MEASUREMENT_MODE")

                        listenerMode(ModeType.MEASUREMENT_MODE)
                    }
                }
                else if (extracted[1] == 0xb0.toByte()) {
                    if (extracted[2] == 0x01.toByte() && extracted[3] == 0x61.toByte()) {
                        Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_DEFAULT")

                        listenerEqMode(EqModeType.EQ_MODE_DEFAULT)
                    } else if (extracted[2] == 0x02.toByte() && extracted[3] == 0x60.toByte()) {
                        Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_USER1")

                        listenerEqMode(EqModeType.EQ_MODE_USER1)
                    } else if (extracted[2] == 0x03.toByte() && extracted[3] == 0x5f.toByte()) {
                        Log.d(TAG, "parseCmd. EqModeType.EQ_MODE_USER2")

                        listenerEqMode(EqModeType.EQ_MODE_USER2)
                    } else {
                        if (extracted[3] == 0x5d.toByte()) {
                            val eqMode = extracted[2] - 0x04
                            Log.d(TAG, "parseCmd. getEQMode. received:$eqMode eqMode:$eqMode")

                            when (eqMode) {
                                0 -> listenerGetEqMode(EqModeType.EQ_MODE_DEFAULT)
                                1 -> listenerGetEqMode(EqModeType.EQ_MODE_USER1)
                                2 -> listenerGetEqMode(EqModeType.EQ_MODE_USER2)
                            }
                        }
                    }
                }
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte handshaking 데이터를 수신
    suspend fun modeSetting(modeType: ModeType) {
        Log.d(TAG, "modeSetting. $modeType")

        when(modeType) {
            ModeType.PLAY_MODE -> {
                /*
                 * App → BT {0xec, 0xa0, 0x01, 0x72}, BT → App Return {0xed, 0xa0, 0x01, 0x71}
                 * 통신 성공하면 AI Music Box 위의 Mic Reverb LED 중 Low 가 켜짐
                 */
                val cmd = byteArrayOf(
                    0xec.toByte(),
                    0xa0.toByte(),
                    0x01.toByte(),
                    0x72.toByte()
                )
                bleDeviceManager.write(cmd)
            }
            ModeType.MEASUREMENT_MODE -> {
                /*
                 * App → BT {0xec, 0xa0, 0x02, 0x71}, BT → App Return {0xed, 0xa0, 0x02, 0x70}
                 * 통신 성공하면 AI Music Box 위의 Mic Reverb LED 중 Mid 가 켜짐
                 */
                val cmd = byteArrayOf(
                    0xed.toByte(),
                    0xa0.toByte(),
                    0x01.toByte(),
                    0x71.toByte()
                )
                bleDeviceManager.write(cmd)
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte handshaking 데이터를 수신
    suspend fun setEQMode(eqModeType: EqModeType) {
        Log.d(TAG, "setEQMode. $eqModeType")

        when(eqModeType) {
            EqModeType.EQ_MODE_DEFAULT -> {
                /*
                 * App → BT {0xec, 0xb0, 0x01, 0x62}, BT → App Return {0xed, 0xb0, 0x01, 0x61}
                 * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Minions 가 켜짐
                 */
                val cmd = byteArrayOf(
                    0xec.toByte(),
                    0xb0.toByte(),
                    0x01.toByte(),
                    0x62.toByte()
                )
                bleDeviceManager.write(cmd)
            }
            EqModeType.EQ_MODE_USER1 -> {
                /*
                 * App → BT {0xec, 0xb0, 0x02, 0x61}, BT → App Return {0xed, 0xb0, 0x02, 0x60}
                 * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Monster 가 켜짐
                 */
                val cmd = byteArrayOf(
                    0xec.toByte(),
                    0xb0.toByte(),
                    0x02.toByte(),
                    0x61.toByte()
                )
                bleDeviceManager.write(cmd)
            }
            EqModeType.EQ_MODE_USER2 -> {
                /*
                 * App → BT {0xec, 0xb0, 0x03, 0x60}, BT → App Return {0xed, 0xb0, 0x03, 0x5f}
                 * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Monster 가 켜짐
                 */
                val cmd = byteArrayOf(
                    0xec.toByte(),
                    0xb0.toByte(),
                    0x03.toByte(),
                    0x60.toByte()
                )
                bleDeviceManager.write(cmd)
            }
        }
    }

    // Bluetooth control(BLE)을 통하여 4byte 제어 데이터를 전송하고 4byte 회신을 수신
    suspend fun getEQMode() {
        /*
         * return value : 0(default), 1(User1), 2(User2)
         * App → BT {0xec, 0xb0, 0x05, 0x5e}, BT → App Return {0xed, 0xb0, 0x05, 0x5d}
         * 통신 성공하면 AI Music Box 위의 Vocal Effect LED 중 Duet  켜짐
         * 회신된 4 byte 데이터 중 (3번째 바이트-0x04) return 함 (현재 AI Music Box 펌웨어에서는 회신값이 고정으므로 return 값 항상 1임)
         */
        val cmd = byteArrayOf(
            0xec.toByte(),
            0xb0.toByte(),
            0x05.toByte(),
            0x5e.toByte()
        )
        bleDeviceManager.write(cmd)
    }
}