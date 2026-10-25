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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.llegadadelbebe.core.update.UpdateInfo

@Composable
fun OtaUpdateDialog(
    updateInfo: UpdateInfo,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Descargando actualización: $downloadProgress%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = downloadProgress < 0
                    ) {
                        Text("Más tarde")
                    }
                    Button(
                        onClick = onConfirmUpdate,
                        modifier = Modifier.weight(1f),
                        enabled = downloadProgress < 0
                    ) {
                        Text("Actualizar")
                    }
                }
            }
        }
    }
}
