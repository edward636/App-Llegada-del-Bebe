package com.example.llegadadelbebe.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cuadro de texto ergonómico y estilizado para "Llegada del Bebé".
 *
 * Características ergonómicas y de diseño:
 * - Esquinas curvas de 16dp para un aspecto moderno y orgánico.
 * - Contenedor con ligero tinte para contraste perfecto en Modo Claro y Modo Oscuro AMOLED puro.
 * - Botón de limpieza rápida de un toque (❌) cuando el usuario escribe.
 * - Soporte para leading icon, leading emoji o prefijos/sufijos monetarios.
 */
@Composable
fun BabyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    leadingEmoji: String? = null,
    prefixText: String? = null,
    suffixText: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    showClearButton: Boolean = true,
    cornerSize: Dp = 16.dp
) {
    val containerColor = if (MaterialTheme.colorScheme.background == Color.Black) {
        Color(0xFF0C1425) // Modo AMOLED: Azul obsidiana sutil sobre fondo negro
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Modo Claro
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it, fontWeight = FontWeight.Medium) } },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
        leadingIcon = when {
            leadingIcon != null -> {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            leadingEmoji != null -> {
                { Text(leadingEmoji, fontSize = 18.sp) }
            }
            else -> null
        },
        trailingIcon = if (showClearButton && value.isNotBlank() && enabled && !readOnly) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        prefix = prefixText?.let { { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } },
        suffix = suffixText?.let { { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        supportingText = errorMessage?.takeIf { isError }?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        isError = isError,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        maxLines = maxLines,
        shape = RoundedCornerShape(cornerSize),
        textStyle = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
