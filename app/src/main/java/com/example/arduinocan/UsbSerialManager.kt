package com.example.arduinocan

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.nio.charset.StandardCharsets

class UsbSerialManager(private val context: Context, private val onMessage: (String) -> Unit, private val onStatus: (String) -> Unit)
{
    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.example.arduinocan.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val buffer = StringBuilder()
    private val lock = Any()

    private var registered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            if (intent.action != ACTION_USB_PERMISSION) return

            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    UsbManager.EXTRA_DEVICE,
                    UsbDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<UsbDevice>(
                    UsbManager.EXTRA_DEVICE
                )
            }

            val granted = intent.getBooleanExtra(
                UsbManager.EXTRA_PERMISSION_GRANTED,
                false
            )

            if (granted && device != null) {
                abrir(device)
            } else {
                onStatus("Permissão USB negada")
            }
        }
    }

    fun iniciar() {
        if (registered) return

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                permissionReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        registered = true
    }

    fun conectar() {
        val drivers =
            UsbSerialProber.getDefaultProber()
                .findAllDrivers(usbManager)

        if (drivers.isEmpty()) {
            onStatus("Nenhum dispositivo USB Serial encontrado")
            return
        }

        // Primeira versão: seleciona o primeiro driver.
        val device = drivers.first().device

        if (usbManager.hasPermission(device)) {
            abrir(device)
            return
        }

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_MUTABLE
        )

        usbManager.requestPermission(
            device,
            permissionIntent
        )

        onStatus("Aguardando permissão USB...")
    }

    private fun abrir(device: UsbDevice) {
        try {
            desconectar()

            val driver =
                UsbSerialProber.getDefaultProber()
                    .probeDevice(device)
                    ?: throw Exception("Driver USB não encontrado")

            val connection =
                usbManager.openDevice(device)
                    ?: throw Exception("Não foi possível abrir USB")

            try {
                val port = driver.ports.firstOrNull()
                    ?: throw Exception("Nenhuma porta serial disponível")

                port.open(connection)
                port.setParameters(
                    115200,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )

                serialPort = port

                val listener =
                    object : SerialInputOutputManager.Listener {
                        override fun onNewData(data: ByteArray) {
                            receberDados(data)
                        }

                        override fun onRunError(e: Exception) {
                            onStatus("Erro USB: ${e.message}")
                            desconectar()
                        }
                    }

                ioManager = SerialInputOutputManager(
                    port,
                    listener
                ).also {
                    it.start()
                }

                onStatus("Conectado ao Arduino")
            } catch (e: Exception) {
                connection.close()
                throw e
            }
        } catch (e: Exception) {
            desconectar()
            onStatus("Falha: ${e.message}")
        }
    }

    private fun receberDados(data: ByteArray) {
        val texto = String(data, StandardCharsets.UTF_8)

        synchronized(lock) {
            buffer.append(texto)

            while (true) {
                val indice = buffer.indexOf("\n")
                if (indice < 0) break

                val linha = buffer.substring(0, indice).trim()
                buffer.delete(0, indice + 1)

                if (linha.isNotEmpty()) {
                    onMessage(linha)
                }
            }

            if (buffer.length > 2048) {
                buffer.clear()
                onStatus("Buffer serial excedido")
            }
        }
    }

    fun enviar(comando: String) {
        val port = serialPort

        if (port == null) {
            onStatus("Arduino não conectado")
            return
        }

        try {
            val dados = (
                    comando.trim() + "\n"
                    ).toByteArray(StandardCharsets.UTF_8)

            port.write(dados, 2000)
        } catch (e: Exception) {
            onStatus("Erro ao enviar: ${e.message}")
        }
    }

    fun desconectar() {
        ioManager?.stop()
        ioManager = null

        try {
            serialPort?.close()
        } catch (_: Exception) {
        }

        serialPort = null
    }

    fun finalizar() {
        desconectar()

        if (registered) {
            context.unregisterReceiver(permissionReceiver)
            registered = false
        }
    }
}