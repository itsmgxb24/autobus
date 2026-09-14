@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pl.walbrzych.autobus.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import pl.walbrzych.autobus.R
import pl.walbrzych.autobus.data.CityConfig
import pl.walbrzych.autobus.data.TransitTime

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

private val ticketsNerdFont = FontFamily(Font(R.font.commit_mono_nerd_font_propo_regular))
private val demoNoticeGlyph = String(Character.toChars(0xF449)) // nf-oct-unverified

@Composable
fun TicketsScreen(
    city: CityConfig,
    purchaseVersion: Int,
    onTicketClick: (Ticket) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val purchaseStore = remember(context) { TicketPurchaseStore(context) }
    var ticketListVersion by remember(city.id) { mutableIntStateOf(0) }
    val purchasedTickets = remember(city.id, purchaseVersion, ticketListVersion) { purchaseStore.readAll(city.id) }
    var detailsPurchase by remember(city.id, purchaseVersion) { mutableStateOf<PurchasedTicket?>(null) }
    val issuer = remember(city.id) { ticketIssuer(city) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bilety", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "ticket-notice-placeholder") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = demoNoticeGlyph,
                                fontFamily = ticketsNerdFont,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "WERSJA DEMONSTRACYJNA!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "Poniższe bilety nie stanowią dokumentu przejazdu. Ta demonstracja ma jedynie zobrazować, jak mogłaby wyglądać przyszła funkcja biletów w naszej aplikacji. Niestety, możliwość kupowania biletów w autoBus nie jest zależna od nas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Niestety, uruchomienie tej funkcji wymaga współpracy z operatorem sprzedaży biletów i nie zależy wyłącznie od nas. Skontaktowaliśmy się już z czterema operatorami i obecnie czekamy na ich odpowiedź.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(purchasedTickets, key = { purchase -> "purchased-${purchase.purchasedAtMillis}" }) { purchase ->
                ticketById(purchase.ticketId)?.let { ticket ->
                    PurchasedTicketBanner(ticket, purchase, issuer) { detailsPurchase = purchase }
                }
            }
            allTickets.groupBy { it.category }.forEach { (category, tickets) ->
                item {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(tickets, key = { it.id }) { ticket -> TicketCard(ticket, onClick = { onTicketClick(ticket) }) }
            }
        }
    }

    detailsPurchase?.let { purchase ->
        ticketById(purchase.ticketId)?.let { ticket ->
            TicketDetailsSheet(
                ticket = ticket,
                purchase = purchase,
                issuer = issuer,
                onDismiss = { detailsPurchase = null },
                onDelete = {
                    purchaseStore.delete(purchase)
                    detailsPurchase = null
                    ticketListVersion++
                },
            )
        }
    }
}

@Composable
private fun PurchasedTicketBanner(
    ticket: Ticket,
    purchase: PurchasedTicket,
    issuer: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().height(250.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Bilet ${ticket.category.lowercase()} · ${ticket.name} (${ticket.concession.lowercase()})",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TicketQrCode(ticketQrPayload(ticket, purchase, issuer), 146.dp)
            }
            IssuerMarquee("GMINA WAŁBRZYCH")
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
fun TicketDetailScreen(
    ticket: Ticket,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onContinue: () -> Unit,
) {
    var purchaseCompleted by rememberSaveable(ticket.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Wybrany bilet") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } },
        )
        if (purchaseCompleted) {
            TicketPurchaseSuccess(onContinue = onContinue)
        } else {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Default.ConfirmationNumber, null, Modifier.size(52.dp), MaterialTheme.colorScheme.primary)
                Text(ticket.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                TicketProperty("Rodzaj", ticket.category)
                TicketProperty("Ulga", ticket.concession)
                TicketProperty("Ważność", ticket.validity)
                TicketProperty("Cena", ticket.price)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onPurchase()
                        purchaseCompleted = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Kup bilet")
                }
            }
        }
    }
}

@Composable
private fun TicketPurchaseSuccess(onContinue: () -> Unit) {
    var animationFinished by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedPurchaseCheck(onFinished = { animationFinished = true })
            if (animationFinished) {
                Text(
                    "Bilet kupiony!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (animationFinished) {
            Button(
                onClick = onContinue,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp),
            ) {
                Text("Kontynuuj")
            }
        }
    }
}

/** Android Compose rendition of the animated two-stroke Lucide Animated Check icon. */
@Composable
private fun AnimatedPurchaseCheck(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val checkColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
        onFinished()
    }
    Canvas(modifier = Modifier.size(112.dp)) {
        val strokeWidth = size.minDimension * 0.105f
        val firstStart = Offset(size.width * 0.20f, size.height * 0.53f)
        val joint = Offset(size.width * 0.42f, size.height * 0.74f)
        val secondEnd = Offset(size.width * 0.80f, size.height * 0.28f)
        val firstProgress = (progress.value * 2f).coerceIn(0f, 1f)
        val secondProgress = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
        drawLine(
            color = checkColor,
            start = firstStart,
            end = Offset(
                x = firstStart.x + (joint.x - firstStart.x) * firstProgress,
                y = firstStart.y + (joint.y - firstStart.y) * firstProgress,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        if (secondProgress > 0f) {
            drawLine(
                color = checkColor,
                start = joint,
                end = Offset(
                    x = joint.x + (secondEnd.x - joint.x) * secondProgress,
                    y = joint.y + (secondEnd.y - joint.y) * secondProgress,
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TicketDetailsSheet(
    ticket: Ticket,
    purchase: PurchasedTicket,
    issuer: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bilet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń bilet")
                }
            }
            TicketQrCode(ticketQrPayload(ticket, purchase, issuer), 220.dp)
            TicketInformationCard(
                title = "Bilet ${ticket.category.lowercase()} · ${ticket.name}",
                body = buildString {
                    append("${ticket.concession}\n")
                    append("${ticket.validity}\n")
                    append("Pozostało: ${ticketRemaining(ticket, purchase)}\n")
                    append("ID: ${ticketIdentifier(purchase)}")
                },
            )
            TicketInformationCard(
                title = "Informacje o zakupie",
                body = "Zakupiono: ${purchaseTime(purchase)}\n" +
                    "Operator płatności: autoBus\n" +
                    "Wydawca: $issuer\n" +
                    "Kwota: 0 PLN",
            )
        }
    }
}

@Composable
private fun TicketProperty(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TicketInformationCard(title: String, body: String) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IssuerMarquee(issuer: String) {
    val transition = rememberInfiniteTransition(label = "issuer-banner")
    val gradientPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "gold-gradient",
    )
    val textPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "issuer-marquee",
    )
    // Two identical tracks form a closed loop.  Each track is deliberately
    // longer than the banner, so the first frame is already fully populated
    // and resetting the infinite transition cannot expose a clipped glyph.
    var cycleWidth by remember { mutableIntStateOf(0) }
    val label = remember(issuer) { "$issuer  •  ".repeat(12) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8B5A00),
                        Color(0xFFFFE08A),
                        Color(0xFFFFC107),
                        Color(0xFF9B6700),
                        Color(0xFFFFE08A),
                    ),
                    start = Offset(gradientPhase * 420f, 0f),
                    end = Offset(gradientPhase * 420f + 520f, 80f),
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .offset {
                    IntOffset(
                        -(cycleWidth * textPhase).roundToInt(),
                        0,
                    )
                },
            maxLines = 1,
            softWrap = false,
            onTextLayout = { cycleWidth = it.size.width },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = Color(0xFF211500),
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            modifier = Modifier
                .offset {
                    IntOffset(
                        cycleWidth - (cycleWidth * textPhase).roundToInt(),
                        0,
                    )
                },
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = Color(0xFF211500),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun TicketQrCode(payload: String, size: androidx.compose.ui.unit.Dp) {
    val image = remember(payload) { qrCodeImage(payload) }
    Image(
        bitmap = image,
        contentDescription = "Kod QR biletu",
        modifier = Modifier.size(size).clip(MaterialTheme.shapes.small).background(Color.White),
    )
}

private fun qrCodeImage(payload: String) = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512).let { matrix ->
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        repeat(matrix.width) { x ->
            repeat(matrix.height) { y ->
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }.asImageBitmap()
}

private fun ticketIssuer(city: CityConfig): String =
    if (city.name.equals("Świebodzice", ignoreCase = true)) "GMINA ŚWIEBODZICE" else "GMINA WAŁBRZYCH"

private fun ticketQrPayload(ticket: Ticket, purchase: PurchasedTicket, issuer: String): String =
    """
        autoBus DEMO!
        Jest to bilet DEMONSTRACYJNY!
        Nie pobrano opłaty!
        ${ticket.category}: ${ticket.name}, ${ticket.concession}
        Wydawca: $issuer
        ID: ${ticketIdentifier(purchase)}
    """.trimIndent()

private fun ticketIdentifier(purchase: PurchasedTicket): String =
    "#DB ${purchase.purchasedAtMillis.toString().takeLast(7).padStart(7, '0')}"

private fun purchaseTime(purchase: PurchasedTicket): String =
    Instant.ofEpochMilli(purchase.purchasedAtMillis)
        .atZone(TransitTime.zone)
        .format(DateTimeFormatter.ofPattern("dd.MM.yy, HH:mm:ss"))

private fun ticketRemaining(ticket: Ticket, purchase: PurchasedTicket): String {
    val purchasedAt = Instant.ofEpochMilli(purchase.purchasedAtMillis).atZone(TransitTime.zone)
    val expiry = ticketExpiry(ticket, purchasedAt)
    val duration = Duration.between(TransitTime.now().atZone(TransitTime.zone), expiry).coerceAtLeast(Duration.ZERO)
    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
    return when {
        days > 0 -> "$days dni, $hours godzin"
        hours > 0 -> "$hours godzin, $minutes minut"
        else -> "$minutes minut"
    }
}

private fun ticketExpiry(ticket: Ticket, purchasedAt: ZonedDateTime): ZonedDateTime =
    if (ticket.category == "Okresowe") {
        purchasedAt.plusDays(ticket.name.substringBefore(' ').toLongOrNull() ?: 0L)
    } else {
        purchasedAt.plusMinutes(ticket.name.substringBefore(' ').toLongOrNull() ?: 0L)
    }
