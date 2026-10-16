package com.example.arduinocan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var usb: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mensagens = mutableStateListOf<String>()
        val status = mutableStateOf("Desconectado")

        usb = UsbSerialManager(
            context = this,
            onMessage = { mensagem ->
                runOnUiThread {
                    mensagens.add("RX: $mensagem")
                }
            },
            onStatus = { novoStatus ->
                runOnUiThread {
                    status.value = novoStatus
                }
            }
        )

        usb.iniciar()

        setContent {
            MaterialTheme {
                var comando by remember {
                    mutableStateOf("PING")
                }

                fun enviar(texto: String) {
                    mensagens.add("TX: $texto")

                    lifecycleScope.launch(Dispatchers.IO) {
                        usb.enviar(texto)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Automação CAN",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text("Status: ${status.value}")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { usb.conectar() }
                        ) {
                            Text("Conectar")
                        }

                        OutlinedButton(
                            onClick = {
                                usb.desconectar()
                                status.value = "Desconectado"
                            }
                        ) {
                            Text("Desconectar")
                        }
                    }

                    HorizontalDivider()

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { enviar("PING") }
                        ) {
                            Text("PING")
                        }

                        Button(
                            onClick = { enviar("LIST LAMPS") }
                        ) {
                            Text("Lâmpadas")
                        }

                        Button(
                            onClick = { enviar("LIST ACTUATORS") }
                        ) {
                            Text("Acionadores")
                        }
                    }

                    OutlinedTextField(
                        value = comando,
                        onValueChange = { comando = it },
                        label = { Text("Comando Serial") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = { enviar(comando) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enviar comando")
                    }

                    Text(
                        "Monitor Serial",
                        style = MaterialTheme.typography.titleMedium
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(mensagens.toList()) { mensagem ->
                            Text(
                                mensagem,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        usb.finalizar()
        super.onDestroy()
    }
}