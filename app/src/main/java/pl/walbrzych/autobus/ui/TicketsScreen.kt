@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.walbrzych.autobus.ui.theme.AutoBusTheme

data class Ticket(
    val id: String,
    val category: String,
    val name: String,
    val concession: String,
    val validity: String,
    val price: String,
)

private val allTickets = listOf(
    Ticket("7-normalny", "Okresowe", "7 dni", "Normalny", "7 dni od skasowania", "40,00 zł"),
    Ticket("7-ulgowy", "Okresowe", "7 dni", "Ulgowy", "7 dni od skasowania", "20,00 zł"),
    Ticket("30-normalny", "Okresowe", "30 dni", "Normalny", "30 dni od skasowania", "110,00 zł"),
    Ticket("30-ulgowy", "Okresowe", "30 dni", "Ulgowy", "30 dni od skasowania", "55,00 zł"),
    Ticket("30min-normalny", "Czasowe", "30 minut", "Normalny", "30 minut od skasowania", "4,40 zł"),
    Ticket("30min-ulgowy", "Czasowe", "30 minut", "Ulgowy", "30 minut od skasowania", "2,20 zł"),
    Ticket("60min-normalny", "Czasowe", "60 minut", "Normalny", "60 minut od skasowania", "5,60 zł"),
    Ticket("60min-ulgowy", "Czasowe", "60 minut", "Ulgowy", "60 minut od skasowania", "2,80 zł"),
)

fun ticketById(id: String?): Ticket? = allTickets.firstOrNull { it.id == id }

@Composable
fun TicketsScreen(onTicketClick: (Ticket) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bilety", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text("To jest wersja DEMONSTRACYJNA!", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            allTickets.groupBy { it.category }.forEach { (category, tickets) ->
                item { Text(category, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
                items(tickets, key = { it.id }) { ticket -> TicketCard(ticket, onClick = { onTicketClick(ticket) }) }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ticket.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(ticket.concession, style = MaterialTheme.typography.bodyMedium)
            }
            Text(ticket.price, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TicketDetailScreen(ticket: Ticket, onBack: () -> Unit, onBuy: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Wybrany bilet") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
        )
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(Icons.Default.ConfirmationNumber, null, Modifier.size(52.dp), MaterialTheme.colorScheme.primary)
            Text(ticket.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TicketProperty("Rodzaj", ticket.category)
            TicketProperty("Ulga", ticket.concession)
            TicketProperty("Ważność", ticket.validity)
            TicketProperty("Cena demonstracyjna", ticket.price)
            Spacer(Modifier.weight(1f))
            Button(onClick = onBuy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kup bilet") }
            Text("Zakup jest symulowany — bez płatności.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun TicketProperty(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun DemoTicketScreen(ticket: Ticket, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Bilet demonstracyjny") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
        )
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(56.dp), MaterialTheme.colorScheme.primary)
            Text("DEMO — BILET NIEWAŻNY", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
            Text("${ticket.name} · ${ticket.concession}", style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DemoQrCode(ticket.id)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode2, null)
                        Spacer(Modifier.width(8.dp))
                        Text("KOD DEMONSTRACYJNY", fontWeight = FontWeight.Bold)
                    }
                    Text("Ważność: ${ticket.validity}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Nie jest dokumentem przewozu. Nie wykonano płatności.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Zamknij") }
        }
    }
}

@Composable
private fun DemoQrCode(seed: String) {
    Canvas(
        modifier = Modifier
            .size(188.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color.White),
    ) {
        val modules = 25
        val module = size.width / modules
        fun isFinder(row: Int, col: Int): Boolean {
            val origins = listOf(0 to 0, 0 to 18, 18 to 0)
            return origins.any { (startRow, startCol) ->
                row in startRow until startRow + 7 && col in startCol until startCol + 7
            }
        }
        fun finderPixel(row: Int, col: Int): Boolean {
            val rowIn = row % 18
            val colIn = col % 18
            return rowIn == 0 || rowIn == 6 || colIn == 0 || colIn == 6 || (rowIn in 2..4 && colIn in 2..4)
        }
        repeat(modules) { row ->
            repeat(modules) { col ->
                val filled = if (isFinder(row, col)) finderPixel(row, col) else ((row * 31 + col * 17 + seed.hashCode()) and 3) != 0
                if (filled) drawRect(Color(0xFF111412), topLeft = androidx.compose.ui.geometry.Offset(col * module, row * module), size = androidx.compose.ui.geometry.Size(module, module))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun TicketsPreview() {
    AutoBusTheme { TicketsScreen({}) }
}
