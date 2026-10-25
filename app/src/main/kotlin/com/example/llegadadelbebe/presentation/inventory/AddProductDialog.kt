package com.example.llegadadelbebe.presentation.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import java.util.UUID

@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onSave: (BabyProduct) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ProductCategory.CLOTHING) }
    var stageSize by remember { mutableStateOf("RN") }
    var quantity by remember { mutableIntStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var registeredBy by remember { mutableStateOf("Mamá") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Registrar Producto del Bebé 👶",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del artículo") },
                    placeholder = { Text("Ej: Bodies manga larga") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo/Línea") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stageSize,
                        onValueChange = { stageSize = it },
                        label = { Text("Talla / Etapa") },
                        placeholder = { Text("RN / 0-3M") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = { quantity = it.toIntOrNull() ?: 1 },
                        label = { Text("Cantidad") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Selector de categoría rápido
                Text("Categoría:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickCategories = listOf(ProductCategory.CLOTHING, ProductCategory.DIAPERS, ProductCategory.WIPES_HYGIENE, ProductCategory.FEEDING)
                    quickCategories.forEach { cat ->
                        OutlinedButton(
                            onClick = { selectedCategory = cat },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(cat.icon, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Registrado por (Papá o Mamá)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Registrado por: $registeredBy", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { registeredBy = if (registeredBy == "Mamá") "Papá" else "Mamá" }) {
                        Text("Alternar a ${if (registeredBy == "Mamá") "Papá" else "Mamá"}")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Detalles adicionales") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    BabyProduct(
                                        id = UUID.randomUUID().toString(),
                                        name = name,
                                        brand = brand,
                                        model = model,
                                        category = selectedCategory,
                                        stageSize = stageSize,
                                        quantityCurrent = quantity,
                                        quantityTarget = quantity,
                                        status = ProductStatus.PURCHASED,
                                        notes = notes,
                                        registeredBy = registeredBy
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
