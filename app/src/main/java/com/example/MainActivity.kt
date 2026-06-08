package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.AccountBalance
import com.example.data.TransactionSMS
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = NearBlackBackground
                ) { innerPadding ->
                    SMSLedgerApp(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Data structures for enhanced visual metrics
data class MonthlyTrendData(
    val monthLabel: String,
    val income: Double,
    val expenses: Double
)

data class CategoryAgg(
    val category: String,
    val amount: Double,
    val count: Int,
    val color: Color,
    val icon: String
)

data class MerchantAgg(
    val merchant: String,
    val amount: Double,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMSLedgerApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()

    // Screen State selector (0 = Home/Dashboard, 1 = Analysis/Detailed, 2 = Trends)
    var currentScreenTabIndex by remember { mutableStateOf(0) }

    // Dialog state for viewing full text and verification BottomSheet
    var selectedTransaction by remember { mutableStateOf<TransactionSMS?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showCategorySheetForTransaction by remember { mutableStateOf<TransactionSMS?>(null) }
    var categorySheetOpenedFromDetail by remember { mutableStateOf(false) }

    // Local permission status holder
    var hasSMSPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permMap ->
        val readGranted = permMap[Manifest.permission.READ_SMS] ?: false
        val receiveGranted = permMap[Manifest.permission.RECEIVE_SMS] ?: false
        hasSMSPermission = readGranted && receiveGranted
        if (hasSMSPermission) {
            viewModel.scanDeviceInbox(context)
            Toast.makeText(context, "Permissions granted! Syncing SMS...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions denied. Real parsing disabled.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-scan inbox on start if permitted
    LaunchedEffect(hasSMSPermission) {
        if (hasSMSPermission) {
            viewModel.scanDeviceInbox(context)
        }
    }

    // Aggregate dynamic totals for present month (June 2026 based on metadata)
    val currentMonthKey = remember {
        SimpleDateFormat("MM-yyyy", Locale.US).format(Date())
    }

    val currentMonthExpenses = remember(transactions) {
        transactions.filter { tx ->
            val format = SimpleDateFormat("MM-yyyy", Locale.US).format(Date(tx.timestamp))
            format == currentMonthKey && tx.type != "Credit"
        }.sumOf { it.amount }
    }

    val currentMonthIncome = remember(transactions) {
        transactions.filter { tx ->
            val format = SimpleDateFormat("MM-yyyy", Locale.US).format(Date(tx.timestamp))
            format == currentMonthKey && tx.type == "Credit"
        }.sumOf { it.amount }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NearBlackBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main dynamic viewport based on active Screen Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreenTabIndex) {
                    0 -> DashboardMainScreen(
                        transactions = transactions,
                        accountBalances = accountBalances,
                        totalExpenses = currentMonthExpenses,
                        totalIncome = currentMonthIncome,
                        hasSMSPermission = hasSMSPermission,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                            )
                        },
                        onScanClick = {
                            viewModel.scanDeviceInbox(context) { count ->
                                Toast.makeText(context, "Successfully synced SMS inbox! Found $count transaction messages.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onClearCacheClick = { viewModel.clearCache() },
                        onTransactionClick = { tx ->
                            selectedTransaction = tx
                            showBottomSheet = true
                        },
                        onCategoryClick = { tx ->
                            categorySheetOpenedFromDetail = false
                            showCategorySheetForTransaction = tx
                        },
                        onNavigateToTab = { index -> currentScreenTabIndex = index }
                    )
                    1 -> AnalysisDetailedScreen(
                        transactions = transactions,
                        totalExpenses = currentMonthExpenses,
                        onTransactionClick = { tx ->
                            selectedTransaction = tx
                            showBottomSheet = true
                        },
                        onCategoryClick = { tx ->
                            categorySheetOpenedFromDetail = false
                            showCategorySheetForTransaction = tx
                        }
                    )
                    2 -> AdvancedTrendsScreen(
                        transactions = transactions,
                        totalExpenses = currentMonthExpenses,
                        totalIncome = currentMonthIncome
                    )
                }
            }

            // Unified Navigation Bar (respect of Notch & gesture bar layout guidelines)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = LightCharcoalSurface,
                border = BorderStroke(1.dp, BorderOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnifiedBottomNavItem(
                        icon = Icons.Default.Home,
                        label = "Home",
                        active = currentScreenTabIndex == 0,
                        onClick = { currentScreenTabIndex = 0 }
                    )
                    UnifiedBottomNavItem(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = "Analysis",
                        active = currentScreenTabIndex == 1,
                        onClick = { currentScreenTabIndex = 1 }
                    )
                    UnifiedBottomNavItem(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "Trends",
                        active = currentScreenTabIndex == 2,
                        onClick = { currentScreenTabIndex = 2 }
                    )
                }
            }
        }

        // Active Bottom Sheet popup
        if (showBottomSheet && selectedTransaction != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selectedTransaction = null
                },
                containerColor = LightCharcoalSurface,
                contentColor = PureWhiteText,
                tonalElevation = 16.dp,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ParsedTransactionDetailSheet(
                    transaction = selectedTransaction!!,
                    onDismiss = {
                        showBottomSheet = false
                        selectedTransaction = null
                    },
                    onEditCategory = {
                        categorySheetOpenedFromDetail = true
                        showCategorySheetForTransaction = selectedTransaction
                        showBottomSheet = false
                    }
                )
            }
        }

        // Categories selection sheet
        if (showCategorySheetForTransaction != null) {
            val tx = showCategorySheetForTransaction!!
            val existingCustom = remember(transactions) {
                transactions.map { it.category }.distinct().filter { 
                    it != "Bills" && it != "EMI" && it != "Entertainment" && it != "Food & Drinks" &&
                    it != "Fuel" && it != "Groceries" && it != "Health" && it != "Investment" &&
                    it != "Other" && it != "Shopping" && it != "Transfer" && it != "Travel" && it != "Rent"
                }
            }
            ModalBottomSheet(
                onDismissRequest = {
                    showCategorySheetForTransaction = null
                    if (categorySheetOpenedFromDetail) {
                        showBottomSheet = true
                    }
                },
                containerColor = LightCharcoalSurface,
                contentColor = PureWhiteText,
                tonalElevation = 16.dp
            ) {
                CategoriesSelectionSheet(
                    selectedCategory = tx.category,
                    onCategorySelected = { newCategory ->
                        viewModel.updateTransactionCategory(tx.id, newCategory)
                        selectedTransaction = selectedTransaction?.copy(category = newCategory)
                        showCategorySheetForTransaction = null
                        if (categorySheetOpenedFromDetail) {
                            showBottomSheet = true
                        }
                    },
                    onDismiss = {
                        showCategorySheetForTransaction = null
                        if (categorySheetOpenedFromDetail) {
                            showBottomSheet = true
                        }
                    },
                    existingCustomCategories = existingCustom
                )
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD MAIN SCREEN (image_0.png)
// ==========================================
@Composable
fun DashboardMainScreen(
    transactions: List<TransactionSMS>,
    accountBalances: List<AccountBalance>,
    totalExpenses: Double,
    totalIncome: Double,
    hasSMSPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScanClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onTransactionClick: (TransactionSMS) -> Unit,
    onCategoryClick: ((TransactionSMS) -> Unit)? = null,
    onNavigateToTab: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Line 1: Header (Hi Imthiyaz + Subtitle + Search Icon & controls)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hi ",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PureWhiteText
                        )
                        Text(
                            text = "Imthiyaz",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MintLimePrimary
                        )
                    }
                    Text(
                        text = "Your June snapshot is complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGreyText
                    )
                }

                // Control and Action bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* Search Action */ },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Logs",
                            tint = PureWhiteText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (hasSMSPermission) {
                                onScanClick()
                            } else {
                                onRequestPermission()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan SMS Inbox",
                            tint = MintLimePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Line 2: Circular Metric Container (Total spends and Income below)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Main circular dashboard metric block
                Column(
                    modifier = Modifier
                        .size(190.dp)
                        .background(LightCharcoalSurface, CircleShape)
                        .border(1.2.dp, MintLimePrimary.copy(alpha = 0.7f), CircleShape)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Mint accented trending arrow indicating spent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MintLimePrimary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallMade, // represents "↗" perfectly
                            contentDescription = "Spends Outward Arrow",
                            tint = MintLimePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "TOTAL SPENDS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MutedGreyText,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.0f", totalExpenses),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = PureWhiteText
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Soft credit summary below
                    Text(
                        text = String.format(Locale.getDefault(), "Income: +₹%,.0f", totalIncome),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF81C784)
                    )
                }
            }
        }

        // Line 3: Trends & Categories Action Toggles
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onNavigateToTab(1) }, // Navigate to Detailed Analytics
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightCharcoalSurface,
                        contentColor = MintLimePrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderOutline)
                ) {
                    Icon(Icons.Default.Category, contentDescription = "Categories", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = { onNavigateToTab(2) }, // Navigate to Trends
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightCharcoalSurface,
                        contentColor = MintLimePrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderOutline)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trends", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trends", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Line 4: Dues & Reminders/Accounts Previews (HDFC Card XX5056)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Accounts & Reminders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PureWhiteText
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    // Pre-made card representing HDFC Acc *5056
                    item {
                        val hdfcBalance = accountBalances.find { it.accountIdentifier.contains("HDFC", ignoreCase = true) }
                        val balAmount = hdfcBalance?.remainingBalance ?: 12095.67
                        val lastSync = if (transactions.isNotEmpty()) "Just now" else "Standard offline"
                        
                        Card(
                            modifier = Modifier
                                .width(190.dp)
                                .height(115.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
                            border = BorderStroke(1.dp, BorderOutline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "HDFC ACC",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                        color = MutedGreyText,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MintLimePrimary, CircleShape)
                                    )
                                }

                                Column {
                                    Text(
                                        text = String.format(Locale.getDefault(), "₹%,.2f", balAmount),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = MintLimePrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Updated: $lastSync",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MutedGreyText
                                    )
                                }
                            }
                        }
                    }

                    // Pre-made Card representing Upcoming Reminders
                    item {
                        Card(
                            modifier = Modifier
                                .width(190.dp)
                                .height(115.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
                            border = BorderStroke(1.dp, BorderOutline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DUE REMINDERS",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                        color = Color(0xFFFF8A80),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "🔔",
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Airtel Bill • ₹399",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PureWhiteText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "A/c Repay • ₹3500",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MutedGreyText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Due in 2 days",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Line 5: Recent Transactions Section (Last 5 transactions)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PureWhiteText
                )
                Text(
                    text = "SEE ALL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MintLimePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToTab(1) } // link immediately to screen 2
                )
            }
        }

        val topFive = transactions.take(5)
        if (topFive.isNotEmpty()) {
            items(topFive) { tx ->
                TransactionListItemRow(
                    tx = tx,
                    onClick = { onTransactionClick(tx) },
                    onIconClick = { onCategoryClick?.invoke(tx) }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(LightCharcoalSurface, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = MutedGreyText,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent transactions found.",
                            color = PureWhiteText,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the Play icon (▶) in the header to seed gorgeous template data instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGreyText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: ALL TRANSACTIONS & CATEGORIES SCREEN (image_1.png / image_2.png)
// ==========================================
@Composable
fun AnalysisDetailedScreen(
    transactions: List<TransactionSMS>,
    totalExpenses: Double,
    onTransactionClick: (TransactionSMS) -> Unit,
    onCategoryClick: ((TransactionSMS) -> Unit)? = null
) {
    var subTabState by remember { mutableStateOf(0) } // 0 = Transactions, 1 = Categories, 2 = Merchants

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // App top bar summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Analytics Detail",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhiteText
            )
            Text(
                text = "A complete breakdown of structured SMS ledgers",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGreyText
            )
        }

        // Shared screen spending metric: Big dark circle view repeating spent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(150.dp)
                    .background(LightCharcoalSurface, CircleShape)
                    .border(1.2.dp, BorderOutline, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(MintLimePrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallMade,
                        contentDescription = "outfacing arrow",
                        tint = MintLimePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "MONTHLY SPENDS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MutedGreyText,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.getDefault(), "₹%,.0f", totalExpenses),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = PureWhiteText
                )
            }
        }

        // Tab Selector Row (Transactions / Categories / Merchants)
        TabRow(
            selectedTabIndex = subTabState,
            containerColor = Color.Transparent,
            contentColor = MintLimePrimary,
            indicator = { tabPositions ->
                if (subTabState < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[subTabState]),
                        color = MintLimePrimary
                    )
                }
            },
            divider = { HorizontalDivider(color = BorderOutline) }
        ) {
            Tab(
                selected = subTabState == 0,
                onClick = { subTabState = 0 },
                text = { Text("Transactions", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = subTabState == 1,
                onClick = { subTabState = 1 },
                text = { Text("Categories", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = subTabState == 2,
                onClick = { subTabState = 2 },
                text = { Text("Merchants", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic view content depending on selected tab
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (subTabState) {
                0 -> { // Transactions list
                    if (transactions.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(transactions) { tx ->
                                TransactionListItemRow(
                                    tx = tx,
                                    onClick = { onTransactionClick(tx) },
                                    onIconClick = { onCategoryClick?.invoke(tx) }
                                )
                            }
                        }
                    } else {
                        EmptyStatePlaceholder()
                    }
                }
                1 -> { // Categories aggregation & Donut Chart
                    val categoryList = remember(transactions) { getCategorySpendList(transactions) }
                    if (categoryList.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // Donut Chart Graphic Container
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    DonutChartView(categories = categoryList)
                                }
                            }

                            // Dynamic list of category cards underneath
                            items(categoryList) { cat ->
                                CategoryCardView(cat = cat)
                            }
                        }
                    } else {
                        EmptyStatePlaceholder()
                    }
                }
                2 -> { // Merchant aggregation list
                    val merchantList = remember(transactions) { getMerchantSpendList(transactions) }
                    if (merchantList.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(merchantList) { merch ->
                                MerchantCardView(merchant = merch)
                            }
                        }
                    } else {
                        EmptyStatePlaceholder()
                    }
                }
            }
        }
    }
}

// Donut Chart Custom Canvas implementation
@Composable
fun DonutChartView(
    categories: List<CategoryAgg>,
    modifier: Modifier = Modifier
) {
    val totalSpendsVal = remember(categories) { categories.sumOf { it.amount } }

    Canvas(
        modifier = modifier
            .size(150.dp)
    ) {
        var startAngle = -90f
        val strokeWidthPx = 18.dp.toPx()

        if (totalSpendsVal > 0.0) {
            categories.forEach { cat ->
                val sweepAngle = ((cat.amount / totalSpendsVal) * 360f).toFloat()
                drawArc(
                    color = cat.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
                )
                startAngle += sweepAngle
            }
        } else {
            // Draw dummy grey background arc
            drawArc(
                color = Color.LightGray.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2)
            )
        }
    }

    // Inside description text
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = categories.size.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = PureWhiteText
        )
        Text(
            text = "Categories",
            style = MaterialTheme.typography.labelSmall,
            color = MutedGreyText
        )
    }
}

// Category Row Visual Card
@Composable
fun CategoryCardView(cat: CategoryAgg) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large circular colored category emoji card
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(cat.color.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, cat.color.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = cat.icon, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = cat.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhiteText
                    )
                    Text(
                        text = "${cat.count} spent events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGreyText
                    )
                }
            }

            Text(
                text = String.format(Locale.getDefault(), "₹%,.2f", cat.amount),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = cat.color
            )
        }
    }
}

// Merchant agg Row Visual card
@Composable
fun MerchantCardView(merchant: MerchantAgg) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
        border = BorderStroke(1.dp, BorderOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MintLimePrimary.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Merchant icon",
                        tint = MintLimePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = merchant.merchant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhiteText
                    )
                    Text(
                        text = "${merchant.count} interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGreyText
                    )
                }
            }

            Text(
                text = String.format(Locale.getDefault(), "₹%,.2f", merchant.amount),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhiteText
            )
        }
    }
}

// ==========================================
// SCREEN 3: ADVANCED MONTHLY TRENDS SCREEN (image_4.png)
// ==========================================
@Composable
fun AdvancedTrendsScreen(
    transactions: List<TransactionSMS>,
    totalExpenses: Double,
    totalIncome: Double
) {
    val monthlyTrendList = remember(transactions) { getMonthlyTrendData(transactions) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Advanced Trends",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhiteText
            )
            Text(
                text = "Dual-axis tracking of 6 months performance",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGreyText
            )
        }

        // Custom hand-drawn line + bar dual chart representation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
            border = BorderStroke(1.dp, BorderOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historical Snapshot",
                        style = MaterialTheme.typography.titleSmall,
                        color = PureWhiteText,
                        fontWeight = FontWeight.Bold
                    )

                    // Color indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MintLimePrimary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Income (Line)", style = MaterialTheme.typography.labelSmall, color = MutedGreyText)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFB39DDB), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Spends (Bar)", style = MaterialTheme.typography.labelSmall, color = MutedGreyText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canvas viewport drawing both series
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (monthlyTrendList.isNotEmpty()) {
                        DualAxisChartView(trends = monthlyTrendList)
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No data to plot. Use 'Seed Sample' parameters.", color = MutedGreyText)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Screen Split: Capsule date indicator
        val calendarMonthText = remember {
            SimpleDateFormat("MMM'yy", Locale.US).format(Date())
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LightCharcoalSurface)
                .border(1.dp, BorderOutline, RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Highlighted Focus Cycle",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PureWhiteText
            )
            Box(
                modifier = Modifier
                    .background(BorderOutline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = calendarMonthText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = MintLimePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Grid below chart: 2 large dark cards side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Spends metric element
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(95.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
                border = BorderStroke(1.dp, BorderOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT SPENDS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MutedGreyText,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.CallMade,
                            contentDescription = "Spends",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.0f", totalExpenses),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = PureWhiteText
                    )
                }
            }

            // Income metric element
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(95.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightCharcoalSurface),
                border = BorderStroke(1.dp, BorderOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT INCOME",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MintLimePrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Income",
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.0f", totalIncome),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MintLimePrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Prominent mint-accented Base Button at screen bottom
        val context = LocalContext.current
        val reviewBtnText = remember {
            val formatFull = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
            "Review $formatFull"
        }
        Button(
            onClick = {
                Toast.makeText(context, "$reviewBtnText: Month is within acceptable targets!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintLimePrimary,
                contentColor = DarkGreenOnPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = reviewBtnText.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            )
        }
    }
}

// Dual Axis custom chart viewport implementation
@Composable
fun DualAxisChartView(
    trends: List<MonthlyTrendData>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No trend data available", color = MutedGreyText)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val totalWidth = size.width
            val totalHeight = size.height

            val maxVal = maxOf(
                trends.maxOfOrNull { it.income } ?: 1.0,
                trends.maxOfOrNull { it.expenses } ?: 1.0,
                1.0
            )
            // Add some safety height headroom (1.15 multiplier)
            val limitVal = maxVal * 1.15
            val safeLimitVal = if (limitVal <= 0.0) 1.0 else limitVal

            val pointCount = trends.size
            val divisor = (pointCount - 1).coerceAtLeast(1)
            val xInterval = totalWidth / divisor

            // Draw basic y-grid background guidelines
            val gridLinesCount = 3
            for (i in 0..gridLinesCount) {
                val y = totalHeight * i / gridLinesCount
                drawLine(
                    color = BorderOutline.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(totalWidth, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw 1: Bar Chart Series represents monthly expenses (Purple)
            trends.forEachIndexed { idx, data ->
                val barHeightFraction = (data.expenses / safeLimitVal).toFloat()
                val barHeight = totalHeight * barHeightFraction
                val xCenter = idx * xInterval
                val barWidth = 20.dp.toPx()

                // Centering bounds
                val left = xCenter - (barWidth / 2)
                val top = totalHeight - barHeight

                drawRoundRect(
                    color = Color(0xFFB39DDB), // Light Purple
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            }

            // Draw 2: Line Chart Series represents monthly income (Mint Green)
            val linePoints = trends.mapIndexed { idx, data ->
                val yFraction = (data.income / safeLimitVal).toFloat()
                val y = totalHeight - (totalHeight * yFraction)
                val x = idx * xInterval
                Offset(x, y)
            }

            // Connect nodes to form path lines if possible
            if (linePoints.size > 1) {
                for (i in 0 until linePoints.size - 1) {
                    drawLine(
                        color = MintLimePrimary,
                        start = linePoints[i],
                        end = linePoints[i + 1],
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw circles at data nodes
            linePoints.forEach { pt ->
                // outer outline circle
                drawCircle(
                    color = NearBlackBackground,
                    radius = 7.dp.toPx(),
                    center = pt
                )
                // inline colorful circle
                drawCircle(
                    color = MintLimePrimary,
                    radius = 5.dp.toPx(),
                    center = pt
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Text labels below columns
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            trends.forEach { data ->
                Text(
                    text = data.monthLabel.split("'").firstOrNull() ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MutedGreyText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

// ==========================================
// REGEN COMPOSABLE UI PIECES:
// ==========================================

// Custom Row list item row
@Composable
fun TransactionListItemRow(
    tx: TransactionSMS,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onIconClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Circle supporting category-specific icon tinted dynamically
            val (icon, color) = remember(tx.category) {
                getCategoryAsset(tx.category)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, color.copy(alpha = 0.25f), CircleShape)
                    .then(
                        if (onIconClick != null) {
                            Modifier.clickable { onIconClick() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.beneficiary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Large styled date below description
                val dateFormatted = remember(tx.timestamp) {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(tx.timestamp))
                }
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGreyText
                )
            }
        }

        // Amount colored appropriately: credit is green, others are red
        val isCredit = tx.type == "Credit"
        val labelPrefix = if (isCredit) "+" else "-"
        val textColorVal = if (isCredit) Color(0xFF66BB6A) else Color(0xFFFF5252)

        Text(
            text = String.format(Locale.getDefault(), "%s₹%,.0f", labelPrefix, tx.amount),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
            color = textColorVal
        )
    }
}

// Interactive Bottom Navigation Item Composable
@Composable
fun UnifiedBottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag("nav_item_${label.lowercase()}")
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (active) MintLimePrimary.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MintLimePrimary else MutedGreyText.copy(alpha = 0.60f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = if (active) MintLimePrimary else MutedGreyText,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// Modal structured sheet presentation content
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParsedTransactionDetailSheet(
    transaction: TransactionSMS,
    onDismiss: () -> Unit,
    onEditCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
    ) {
        // Handle Pill indicator and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Parsed SMS Data",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = PureWhiteText
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Sheet",
                    tint = PureWhiteText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Key variables Table Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                .border(1.dp, BorderOutline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SheetDetailRow(
                label = "Date & Time",
                value = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(transaction.timestamp))
            )
            SheetDetailRow(
                label = "Parsed Amount",
                value = String.format(Locale.getDefault(), "₹%,.2f", transaction.amount),
                valueColor = MintLimePrimary
            )
            SheetDetailRow(
                label = "Beneficiary",
                value = transaction.beneficiary
            )
            SheetDetailRow(
                label = "Account Info",
                value = transaction.accountIdentifier
            )
            SheetDetailRow(
                label = "Type",
                value = transaction.type,
                valueColor = if (transaction.type == "Credit") Color(0xFF66BB6A) else Color(0xFFFF5252)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditCategory() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category Mapping",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGreyText
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val catAsset = getCategoryAsset(transaction.category)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(catAsset.second.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(catAsset.first, fontSize = 12.sp)
                    }
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MintLimePrimary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Category",
                        tint = MutedGreyText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SheetDetailRow(
                label = "Closing Balance",
                value = transaction.remainingBalance?.let { String.format(Locale.getDefault(), "₹%,.2f", it) } ?: "N/A"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Original raw sms code body
        Text(
            text = "ORIGINAL RECEIVED SMS BODY",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
            color = MutedGreyText,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = transaction.rawSms,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                color = PureWhiteText.copy(alpha = 0.9f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MintLimePrimary,
                contentColor = DarkGreenOnPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("VERIFIED & CONFIRMED", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SheetDetailRow(
    label: String,
    value: String,
    valueColor: Color = PureWhiteText
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGreyText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Dynamic Assets helper mapping categories to corresponding icons and color tones
fun getCategoryAsset(category: String): Pair<String, Color> {
    return when (category) {
        "Bills" -> "🧾" to Color(0xFF4CAF50)          // Green
        "EMI" -> "📊" to Color(0xFF9E9E9E)            // Grey
        "Entertainment" -> "⭐" to Color(0xFF3F51B5)    // Indigo Blue
        "Food & Drinks" -> "🍽️" to Color(0xFFE91E63)   // Pink/Magenta
        "Fuel" -> "⛽" to Color(0xFFFF9800)           // Amber Orange
        "Groceries" -> "🧺" to Color(0xFF8BC34A)      // Light Green
        "Health" -> "💝" to Color(0xFFFFB300)         // Yellow/Gold
        "Investment" -> "🐖" to Color(0xFF607D8B)     // Blue Grey/Slate
        "Other" -> "💬" to Color(0xFFFF9800)          // Orange
        "Shopping" -> "🛒" to Color(0xFF00ACC1)       // Teal
        "Transfer" -> "↔️" to Color(0xFF9C27B0)       // Purple
        "Travel" -> "🧳" to Color(0xFF673AB7)         // Violet
        "Rent" -> "R" to Color(0xFF4CAF50)            // Green (white R inside green bubble)
        else -> "❓" to Color(0xFFAB47BC)
    }
}

@Composable
fun CategoryGridItem(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val asset = getCategoryAsset(category)
    val color = asset.second
    val iconStr = asset.first

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFF1E2F1E) else Color.Transparent) // Dark translucent green highlight
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) Color(0xFF3E8E41) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, CircleShape)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) Color.White else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconStr,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (iconStr == "R") 22.sp else 24.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Capitalized ellipsized text under category
        val displayName = if (category.length > 10) {
            category.substring(0, 9) + "..."
        } else {
            category
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = PureWhiteText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .background(Color(0xFF53D769), RoundedCornerShape(1.5.dp)) // Selected green line underneath
            )
        } else {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .background(Color.Transparent)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesSelectionSheet(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    existingCustomCategories: List<String> = emptyList()
) {
    val baseCategories = listOf(
        "Bills", "EMI", "Entertainment", "Food & Drinks", "Fuel", 
        "Groceries", "Health", "Investment", "Other", "Shopping", 
        "Transfer", "Travel", "Rent"
    )
    
    val allCategories = remember(existingCustomCategories) {
        val merged = baseCategories.toMutableList()
        existingCustomCategories.forEach {
            if (!merged.contains(it)) {
                merged.add(it)
            }
        }
        merged
    }

    var newCategoryText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(LightCharcoalSurface)
            .padding(top = 12.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
    ) {
        // Top Row: Close "X" Button and Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = PureWhiteText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = PureWhiteText
            )
        }

        // Grid layout of categories
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 20.dp)
        ) {
            items(allCategories) { category ->
                CategoryGridItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = {
                        onCategorySelected(category)
                    }
                )
            }
        }

        // Bottom "New category" Text Field Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C2C2E), RoundedCornerShape(14.dp))
                .border(1.dp, BorderOutline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF53D769), CircleShape) // Green dot icon from screenshot
            )
            Spacer(modifier = Modifier.width(14.dp))
            BasicTextField(
                value = newCategoryText,
                onValueChange = { newCategoryText = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PureWhiteText),
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(MintLimePrimary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (newCategoryText.isEmpty()) {
                            Text(
                                "New category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedGreyText
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (newCategoryText.isNotBlank()) {
                IconButton(
                    onClick = {
                        val cleanName = newCategoryText.trim()
                        if (cleanName.isNotEmpty() && !allCategories.contains(cleanName)) {
                            onCategorySelected(cleanName)
                            newCategoryText = ""
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save category",
                        tint = MintLimePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Helpers for list aggregations inside Detailed Analysis
fun getCategorySpendList(transactions: List<TransactionSMS>): List<CategoryAgg> {
    val currentMonthKey = SimpleDateFormat("MM-yyyy", Locale.US).format(Date())

    val currentSpends = transactions.filter { tx ->
        val format = SimpleDateFormat("MM-yyyy", Locale.US).format(Date(tx.timestamp))
        format == currentMonthKey && tx.type != "Credit"
    }

    val grouped = currentSpends.groupBy { it.category }

    return grouped.map { (cat, list) ->
        val details = getCategoryAsset(cat)
        CategoryAgg(
            category = cat,
            amount = list.sumOf { it.amount },
            count = list.size,
            color = details.second,
            icon = details.first
        )
    }.sortedByDescending { it.amount }
}

fun getMerchantSpendList(transactions: List<TransactionSMS>): List<MerchantAgg> {
    val currentMonthKey = SimpleDateFormat("MM-yyyy", Locale.US).format(Date())

    val currentSpends = transactions.filter { tx ->
        val format = SimpleDateFormat("MM-yyyy", Locale.US).format(Date(tx.timestamp))
        format == currentMonthKey && tx.type != "Credit"
    }

    val grouped = currentSpends.groupBy { it.beneficiary }

    return grouped.map { (merch, list) ->
        MerchantAgg(
            merchant = merch,
            amount = list.sumOf { it.amount },
            count = list.size
        )
    }.sortedByDescending { it.amount }
}

// Empty state loader placeholder
@Composable
fun EmptyStatePlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = "Inbox empty",
            tint = MutedGreyText.copy(alpha = 0.2f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Aggregated details is empty",
            color = PureWhiteText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sync required or seed sample messages back on Home view.",
            color = MutedGreyText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

// Consolidated 6 months history tracker helper
fun getMonthlyTrendData(transactions: List<TransactionSMS>): List<MonthlyTrendData> {
    val format = SimpleDateFormat("MMM", Locale.US)
    val yearFormat = SimpleDateFormat("yy", Locale.US)

    val monthKeys = mutableListOf<String>()
    val monthIncomes = mutableMapOf<String, Double>()
    val monthExpenses = mutableMapOf<String, Double>()

    for (i in 5 downTo 0) {
        val targetCal = Calendar.getInstance()
        targetCal.add(Calendar.MONTH, -i)
        val monthStr = format.format(targetCal.time)
        val yearStr = yearFormat.format(targetCal.time)
        val key = "$monthStr'$yearStr"
        monthKeys.add(key)
        monthIncomes[key] = 0.0
        monthExpenses[key] = 0.0
    }

    for (tx in transactions) {
        val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
        val m = format.format(txCal.time)
        val y = yearFormat.format(txCal.time)
        val key = "$m'$y"

        if (monthIncomes.containsKey(key)) {
            if (tx.type == "Credit") {
                monthIncomes[key] = (monthIncomes[key] ?: 0.0) + tx.amount
            } else {
                monthExpenses[key] = (monthExpenses[key] ?: 0.0) + tx.amount
            }
        }
    }

    val result = monthKeys.map { key ->
        MonthlyTrendData(
            monthLabel = key,
            income = monthIncomes[key] ?: 0.0,
            expenses = monthExpenses[key] ?: 0.0
        )
    }

    // Defensive check: If results list contains fewer than 6 entries for any reason,
    // dynamically pad the list with empty placeholders to avoid IndexOutOfBoundsException
    if (result.size < 6) {
        val paddedResult = result.toMutableList()
        while (paddedResult.size < 6) {
            val missingCount = 6 - paddedResult.size
            val targetCal = Calendar.getInstance()
            targetCal.add(Calendar.MONTH, -missingCount)
            val monthStr = format.format(targetCal.time)
            val yearStr = yearFormat.format(targetCal.time)
            val key = "$monthStr'$yearStr"
            paddedResult.add(0, MonthlyTrendData(monthLabel = key, income = 0.0, expenses = 0.0))
        }
        return paddedResult
    }

    return result
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
