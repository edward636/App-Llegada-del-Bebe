package com.example.llegadadelbebe.presentation.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    googleSheetUrl: String,
    onBack: () -> Unit
) {
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Inventario del Bebé 📋", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("←", fontSize = 16.sp)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.syncWithGoogleSheets(googleSheetUrl) { success, msg ->
                                syncMessage = msg
                            }
                        },
                        enabled = !isSyncing && googleSheetUrl.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Text("Sincronizar Sheets")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 8.dp, end = 4.dp)
            ) {
                Text("➕", fontSize = 22.sp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Buscar producto o marca...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Chips de filtro por categoría
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("Todos") }
                )
                ProductCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text("${category.icon} ${category.displayName}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lista de productos
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products) { product ->
                    ProductItemCard(
                        product = product,
                        onStatusToggle = { viewModel.toggleProductStatus(product, googleSheetUrl) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newProduct ->
                viewModel.addProduct(newProduct, googleSheetUrl)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProductItemCard(product: BabyProduct, onStatusToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${product.category.icon} ${product.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (product.status) {
                        ProductStatus.PURCHASED -> Color(0xFF2E7D32)
                        ProductStatus.PENDING -> Color(0xFFC62828)
                        ProductStatus.GIFTED -> Color(0xFF1565C0)
                        ProductStatus.HOSPITAL_PACKED -> Color(0xFF6A1B9A)
                    }
                ) {
                    Text(
                        text = product.status.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${product.brand} ${product.model} • Talla: ${product.stageSize.ifBlank { "N/A" }} • Cantidad: ${product.quantityCurrent} ud(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (product.geminiOpinion.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🤖 ${product.geminiOpinion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Por: ${product.registeredBy.ifBlank { "Familia" }}",
                    style = MaterialTheme.typography.labelSmall
                )
                Button(
                    onClick = onStatusToggle,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cambiar Estado", fontSize = 12.sp)
                }
            }
        }
    }
}
