package com.example.llegadadelbebe.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.llegadadelbebe.core.update.UpdateInfo

@Composable
fun OtaUpdateDialog(
    updateInfo: UpdateInfo,
    downloadProgress: Int,
    statusMessage: String = "",
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
    onInstallCached: () -> Unit = onConfirmUpdate
) {
    val isDownloading = downloadProgress in 0..99
    val isReadyToInstall = downloadProgress >= 100

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎉 ¡Nueva Versión para el Bebé! (v${updateInfo.latestVersionName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Hay una nueva actualización disponible para la app de Britney y Papá con mejoras importantes.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Novedades: ${updateInfo.changelog}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (downloadProgress >= 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isReadyToInstall) {
                            "✅ Descarga completada (100%)"
                        } else {
                            "Descargando actualización: $downloadProgress%"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { (downloadProgress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Text("Más tarde")
                    }
                    Button(
                        onClick = {
                            if (isReadyToInstall) onInstallCached() else onConfirmUpdate()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isDownloading
                    ) {
                        Text(if (isReadyToInstall) "📲 Instalar" else "Actualizar")
                    }
                }
            }
        }
    }
}
