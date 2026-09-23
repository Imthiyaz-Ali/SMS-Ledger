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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
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
            val themeMode by viewModel.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
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

sealed class CategoryIcon {
    data class Vector(val imageVector: ImageVector) : CategoryIcon()
    data class Character(val char: Char) : CategoryIcon()
    object OthersSpecial : CategoryIcon()
}

@Composable
fun OthersIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.75f)
                .background(Color(0xFFE53935), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.5f).sp
            )
        }
    }
}

data class CategoryAgg(
    val category: String,
    val amount: Double,
    val count: Int,
    val color: Color,
    val icon: CategoryIcon
)

data class MerchantAgg(
    val merchant: String,
    val amount: Double,
    val count: Int
)

data class BulkUpdateDialogInfo(
    val transaction: TransactionSMS,
    val newCategory: String,
    val priorTransactions: List<TransactionSMS>
)

data class DailySpendData(
    val dateKey: String,
    val label: String,
    val amount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMSLedgerApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val accountBalances by viewModel.accountBalances.collectAsState()
    val approvedAccounts by viewModel.approvedAccounts.collectAsState()
    val rejectedAccounts by viewModel.rejectedAccounts.collectAsState()
    val monthlyBudgetLimit by viewModel.monthlyBudgetLimit.collectAsState()
    var showSetBudgetDialog by remember { mutableStateOf(false) }

    // Screen State selector (0 = Home/Dashboard, 1 = Analysis/Detailed, 2 = Trends)
    var currentScreenTabIndex by remember { mutableStateOf(0) }
    var selectedFilterMonthLabel by remember { mutableStateOf<String?>(null) }
    var selectedFilterDateKey by remember { mutableStateOf<String?>(null) }
    var selectedAnalysisTypeFilter by remember { mutableStateOf<String?>(null) }

    // Dialog state for viewing full text and verification BottomSheet
    var selectedTransaction by remember { mutableStateOf<TransactionSMS?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showCategorySheetForTransaction by remember { mutableStateOf<TransactionSMS?>(null) }
    var categorySheetOpenedFromDetail by remember { mutableStateOf(false) }
    var showTypeSheetForTransaction by remember { mutableStateOf<TransactionSMS?>(null) }
    var typeSheetOpenedFromDetail by remember { mutableStateOf(false) }
    var bulkUpdateDialogInfo by remember { mutableStateOf<BulkUpdateDialogInfo?>(null) }

    // Local permission status holder
    var hasSMSPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permMap ->
        val readGranted = permMap[Manifest.permission.READ_SMS] ?: false
        val receiveGranted = permMap[Manifest.permission.RECEIVE_SMS] ?: false
        val notifyGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permMap[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        hasSMSPermission = readGranted && receiveGranted
        hasNotificationPermission = notifyGranted
        if (hasSMSPermission) {
            viewModel.scanDeviceInbox(context)
            Toast.makeText(context, "Permissions granted! Syncing SMS...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions denied. Real parsing/notifications may be disabled.", Toast.LENGTH_LONG).show()
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
            format == currentMonthKey && tx.type != "Credit" && tx.type != "Reminder" && tx.type != "Remainder" && tx.type != "Not a Transaction" && tx.type != "Credit Card Payment" && !tx.category.equals("Transfer", ignoreCase = true)
        }.sumOf { it.amount }
    }

    val currentMonthIncome = remember(transactions) {
        transactions.filter { tx ->
            val format = SimpleDateFormat("MM-yyyy", Locale.US).format(Date(tx.timestamp))
            format == currentMonthKey && tx.type == "Credit" && !tx.category.equals("Transfer", ignoreCase = true)
        }.sumOf { it.amount }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    0 -> {
                        val uniqueCreditedBalances = remember(accountBalances, transactions, rejectedAccounts) {
                            accountBalances.filter { acc ->
                                if (rejectedAccounts.contains(acc.accountIdentifier)) {
                                    return@filter false
                                }
                                val lowerAcc = acc.accountIdentifier.lowercase(Locale.getDefault())
                                // Real bank filter
                                val isRealBank = lowerAcc.contains("hdfc") ||
                                                 lowerAcc.contains("yes") ||
                                                 lowerAcc.contains("icici") ||
                                                 lowerAcc.contains("sbi") ||
                                                 lowerAcc.contains("axis") ||
                                                 lowerAcc.contains("kotak") ||
                                                 lowerAcc.contains("hsbc") ||
                                                 lowerAcc.contains("paytm")

                                // Has at least one 'Credit' transaction in the full list
                                val hasBeenCredited = transactions.any { tx ->
                                    tx.accountIdentifier.equals(acc.accountIdentifier, ignoreCase = true) &&
                                    tx.type.equals("Credit", ignoreCase = true)
                                }
                                isRealBank && hasBeenCredited
                            }.distinctBy { acc ->
                                // Group/distinct by account last digits to prevent duplicates like "YES X3349" and "YES Bank X3349"
                                val matcher = java.util.regex.Pattern.compile("(\\d{3,6})").matcher(acc.accountIdentifier)
                                if (matcher.find()) {
                                    matcher.group(1)
                                } else {
                                    acc.accountIdentifier.lowercase(Locale.getDefault())
                                }
                            }
                        }

                        DashboardMainScreen(
                            themeMode = themeMode,
                            onThemeChange = { viewModel.setThemeMode(it) },
                            transactions = transactions,
                            accountBalances = uniqueCreditedBalances,
                            approvedAccounts = approvedAccounts,
                            rejectedAccounts = rejectedAccounts,
                            onApproveAccount = { viewModel.approveAccount(it) },
                            onRejectAccount = { viewModel.rejectAccount(it) },
                            onResetAllAccounts = { viewModel.resetAllAccountStatuses() },
                            totalExpenses = currentMonthExpenses,
                            totalIncome = currentMonthIncome,
                            monthlyBudgetLimit = monthlyBudgetLimit,
                            onSetBudgetClick = { showSetBudgetDialog = true },
                            hasSMSPermission = hasSMSPermission,
                        onRequestPermission = {
                            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                )
                            }
                            permissionLauncher.launch(permissions)
                        },
                        onScanClick = {
                            viewModel.scanDeviceInbox(context, forceFullScan = true) { count ->
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
                        onNavigateToTab = { index -> currentScreenTabIndex = index },
                        onDayClick = { dateKey ->
                            selectedFilterDateKey = dateKey
                            selectedFilterMonthLabel = null
                            currentScreenTabIndex = 1
                        },
                        onUpdateReminderCompleted = { id, isCompleted -> viewModel.updateTransactionCompleted(id, isCompleted) },
                        onSimulateNotifications = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(context, "Grant Notification Permission to see alerts!", Toast.LENGTH_SHORT).show()
                                val permissions = arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                                permissionLauncher.launch(permissions)
                            } else {
                                val sampleTx = TransactionSMS(
                                    id = 999991L,
                                    smsUniqueId = "mock_tx_1",
                                    accountIdentifier = "YES Bank Acc X3349",
                                    amount = 40.0,
                                    beneficiary = "ALEEM ULLA KHAN",
                                    timestamp = System.currentTimeMillis() - 22 * 60 * 1000, // 22 minutes ago
                                    type = "Debit",
                                    category = "Shopping",
                                    rawSms = "Alert: Your YES Bank Acc X3349 has been debited by INR 40.00 for ALEEM ULLA KHAN. Avl Lmt INR 38,000.00.",
                                    remainingBalance = 38000.0
                                )
                                val sampleDue = TransactionSMS(
                                    id = 999992L,
                                    smsUniqueId = "mock_tx_2",
                                    accountIdentifier = "ICICI credit (6008)",
                                    amount = 7413.0,
                                    beneficiary = "ICICI Bank",
                                    timestamp = System.currentTimeMillis() - 3 * 3600 * 1000, // 3 hours ago
                                    type = "Reminder",
                                    category = "Bill Payment",
                                    rawSms = "Your bill of Rs 7413.00 on ICICI card is due in 2 days. Min due Rs.100.00. Kindly pay.",
                                    remainingBalance = null
                                )
                                com.example.utils.NotificationHelper.showTransactionNotification(context, sampleTx)
                                com.example.utils.NotificationHelper.showDueReminderNotification(context, sampleDue, 2)
                                Toast.makeText(context, "🔔 Simulating 2 Screen-Accurate Notifications! Check notification drawer.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    }
                    1 -> {
                        val filteredTxs = remember(transactions, selectedFilterMonthLabel, selectedFilterDateKey, selectedAnalysisTypeFilter) {
                            val baseTxs = if (selectedFilterDateKey != null) {
                                transactions.filter { tx ->
                                    val txDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.timestamp))
                                    txDateKey == selectedFilterDateKey
                                }
                            } else if (selectedFilterMonthLabel != null) {
                                transactions.filter { tx ->
                                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                                    val m = SimpleDateFormat("MMM", Locale.US).format(txCal.time)
                                    val y = SimpleDateFormat("yy", Locale.US).format(txCal.time)
                                    "$m'$y" == selectedFilterMonthLabel
                                }
                            } else {
                                transactions
                            }
                            
                            when (selectedAnalysisTypeFilter) {
                                "Expenses" -> {
                                    baseTxs.filter { it.type != "Credit" && it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" && it.type != "Credit Card Payment" && !it.category.equals("Transfer", ignoreCase = true) }
                                }
                                "Income" -> {
                                    baseTxs.filter { it.type == "Credit" && !it.category.equals("Transfer", ignoreCase = true) }
                                }
                                else -> baseTxs
                            }
                        }
                        val filteredAmount = remember(filteredTxs, selectedFilterMonthLabel, selectedFilterDateKey, selectedAnalysisTypeFilter) {
                            when (selectedAnalysisTypeFilter) {
                                "Expenses", "Income" -> {
                                    filteredTxs.sumOf { it.amount }
                                }
                                else -> {
                                    if (selectedFilterDateKey != null || selectedFilterMonthLabel != null) {
                                        filteredTxs.filter { it.type != "Credit" && it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" && it.type != "Credit Card Payment" && !it.category.equals("Transfer", ignoreCase = true) }.sumOf { it.amount }
                                    } else {
                                        currentMonthExpenses
                                    }
                                }
                            }
                        }
                        AnalysisDetailedScreen(
                            transactions = filteredTxs,
                            totalExpenses = filteredAmount,
                            monthlyBudgetLimit = monthlyBudgetLimit,
                            onSetBudgetClick = { showSetBudgetDialog = true },
                            selectedFilterMonthLabel = selectedFilterMonthLabel,
                            selectedFilterDateKey = selectedFilterDateKey,
                            onClearFilter = { 
                                selectedFilterMonthLabel = null 
                                selectedFilterDateKey = null
                                selectedAnalysisTypeFilter = null
                            },
                            onTransactionClick = { tx ->
                                selectedTransaction = tx
                                showBottomSheet = true
                            },
                            onCategoryClick = { tx ->
                                categorySheetOpenedFromDetail = false
                                showCategorySheetForTransaction = tx
                            },
                            analysisTypeFilter = selectedAnalysisTypeFilter
                        )
                    }
                    2 -> AdvancedTrendsScreen(
                        transactions = transactions,
                        totalExpenses = currentMonthExpenses,
                        totalIncome = currentMonthIncome,
                        monthlyBudgetLimit = monthlyBudgetLimit,
                        onSetBudgetClick = { showSetBudgetDialog = true },
                        onReviewMonth = { monthLabel, typeFilter ->
                            selectedFilterMonthLabel = monthLabel
                            selectedFilterDateKey = null
                            selectedAnalysisTypeFilter = typeFilter
                            currentScreenTabIndex = 1
                        }
                    )
                }
            }

            // Unified Navigation Bar (respect of Notch & gesture bar layout guidelines)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
        val currentTxDetail = selectedTransaction
        if (showBottomSheet && currentTxDetail != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selectedTransaction = null
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 16.dp,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                ParsedTransactionDetailSheet(
                    transaction = currentTxDetail,
                    onDismiss = {
                        showBottomSheet = false
                        selectedTransaction = null
                    },
                    onEditCategory = {
                        categorySheetOpenedFromDetail = true
                        showCategorySheetForTransaction = selectedTransaction
                        showBottomSheet = false
                    },
                    onEditType = {
                        typeSheetOpenedFromDetail = true
                        showTypeSheetForTransaction = selectedTransaction
                        showBottomSheet = false
                    }
                )
            }
        }

        // Type selection sheet
        val currentTypeSheetTx = showTypeSheetForTransaction
        if (currentTypeSheetTx != null) {
            val tx = currentTypeSheetTx
            ModalBottomSheet(
                onDismissRequest = {
                    showTypeSheetForTransaction = null
                    if (typeSheetOpenedFromDetail) {
                        showBottomSheet = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 16.dp
            ) {
                TypeSelectionSheet(
                    selectedType = tx.type,
                    onTypeSelected = { newType ->
                        viewModel.updateTransactionType(tx.id, newType)
                        selectedTransaction = selectedTransaction?.copy(type = newType)
                        showTypeSheetForTransaction = null
                        if (typeSheetOpenedFromDetail) {
                            showBottomSheet = true
                        }
                    },
                    onDismiss = {
                        showTypeSheetForTransaction = null
                        if (typeSheetOpenedFromDetail) {
                            showBottomSheet = true
                        }
                    }
                )
            }
        }

        // Categories selection sheet
        val currentCategorySheetTx = showCategorySheetForTransaction
        if (currentCategorySheetTx != null) {
            val tx = currentCategorySheetTx
            val savedCustomCategories by viewModel.customCategories.collectAsState()
            val existingCustom = remember(transactions, savedCustomCategories) {
                val fromTx = transactions.map { it.category }
                (savedCustomCategories + fromTx).distinct().filter { 
                    it.isNotBlank() &&
                    it != "Bills" && it != "EMI" && it != "Entertainment" && it != "Food & Drinks" &&
                    it != "Fuel" && it != "Groceries" && it != "Health" && it != "Investment" &&
                    it != "Other" && it != "Shopping" && it != "Transfer" && it != "Travel" && 
                    it != "Rent" && it != "Cash Withdrawl" && it != "Cash Withdrawal"
                }
            }
            ModalBottomSheet(
                onDismissRequest = {
                    showCategorySheetForTransaction = null
                    if (categorySheetOpenedFromDetail) {
                        showBottomSheet = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 16.dp
            ) {
                CategoriesSelectionSheet(
                    selectedCategory = tx.category,
                    onCategorySelected = { newCategory ->
                        viewModel.addCustomCategory(newCategory)
                        if (newCategory != tx.category && tx.beneficiary.isNotBlank()) {
                            val prior = transactions.filter {
                                it.beneficiary.equals(tx.beneficiary, ignoreCase = true) &&
                                it.timestamp < tx.timestamp &&
                                it.category != newCategory
                            }
                            if (prior.isNotEmpty()) {
                                bulkUpdateDialogInfo = BulkUpdateDialogInfo(
                                    transaction = tx,
                                    newCategory = newCategory,
                                    priorTransactions = prior
                                )
                                showCategorySheetForTransaction = null
                            } else {
                                viewModel.updateTransactionCategory(tx.id, newCategory, tx.beneficiary)
                                selectedTransaction = selectedTransaction?.copy(category = newCategory)
                                showCategorySheetForTransaction = null
                                if (categorySheetOpenedFromDetail) {
                                    showBottomSheet = true
                                }
                            }
                        } else {
                            viewModel.updateTransactionCategory(tx.id, newCategory, tx.beneficiary)
                            selectedTransaction = selectedTransaction?.copy(category = newCategory)
                            showCategorySheetForTransaction = null
                            if (categorySheetOpenedFromDetail) {
                                showBottomSheet = true
                            }
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

        val currentBulkInfo = bulkUpdateDialogInfo
        if (currentBulkInfo != null) {
            val info = currentBulkInfo
            val cleanName = formatYesBankBeneficiary(info.transaction.beneficiary)
            AlertDialog(
                onDismissRequest = {
                    bulkUpdateDialogInfo = null
                    if (categorySheetOpenedFromDetail) {
                        showBottomSheet = true
                    }
                },
                title = {
                    Text(
                        text = "Update Past Transactions?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = "You changed the category of '$cleanName' to '${info.newCategory}'.\n\nWould you like to also update all ${info.priorTransactions.size} past transaction(s) with the same beneficiary to this category?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            viewModel.updateTransactionCategory(info.transaction.id, info.newCategory, info.transaction.beneficiary)
                            viewModel.updatePastTransactionsCategory(
                                beneficiary = info.transaction.beneficiary,
                                timestamp = info.transaction.timestamp,
                                category = info.newCategory
                            )
                            selectedTransaction = selectedTransaction?.copy(category = info.newCategory)
                            bulkUpdateDialogInfo = null
                            if (categorySheetOpenedFromDetail) {
                                showBottomSheet = true
                            }
                        }
                    ) {
                        Text("Update All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                bulkUpdateDialogInfo = null
                                showCategorySheetForTransaction = info.transaction
                            }
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.updateTransactionCategory(info.transaction.id, info.newCategory, info.transaction.beneficiary)
                                selectedTransaction = selectedTransaction?.copy(category = info.newCategory)
                                bulkUpdateDialogInfo = null
                                if (categorySheetOpenedFromDetail) {
                                    showBottomSheet = true
                                }
                            }
                        ) {
                            Text("Only This", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        // Monthly Budget Dialog
        if (showSetBudgetDialog) {
            SetBudgetDialog(
                currentBudget = monthlyBudgetLimit,
                onSaveBudget = { newLimit ->
                    viewModel.setMonthlyBudgetLimit(newLimit)
                },
                onDismiss = { showSetBudgetDialog = false }
            )
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD MAIN SCREEN (image_0.png)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardMainScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {},
    transactions: List<TransactionSMS>,
    accountBalances: List<AccountBalance>,
    approvedAccounts: Set<String>,
    rejectedAccounts: Set<String>,
    onApproveAccount: (String) -> Unit,
    onRejectAccount: (String) -> Unit,
    onResetAllAccounts: () -> Unit,
    totalExpenses: Double,
    totalIncome: Double,
    monthlyBudgetLimit: Double = 50000.0,
    onSetBudgetClick: () -> Unit = {},
    hasSMSPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScanClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    onTransactionClick: (TransactionSMS) -> Unit,
    onCategoryClick: ((TransactionSMS) -> Unit)? = null,
    onNavigateToTab: (Int) -> Unit,
    onSimulateNotifications: () -> Unit,
    onUpdateReminderCompleted: (Long, Boolean) -> Unit,
    onDayClick: ((String) -> Unit)? = null
) {
    var showAllAccountsSheet by remember { mutableStateOf(false) }
    var showAllRemindersSheet by remember { mutableStateOf(false) }
    var showCompletedReminders by remember { mutableStateOf(false) }
    var showPastRemindersSheet by remember { mutableStateOf(false) }

    val (activeRemindersCurrent, completedRemindersCurrent, pastReminders) = remember(transactions) {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentYear = todayCal.get(Calendar.YEAR)
        val currentMonth = todayCal.get(Calendar.MONTH)

        val upcomingCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
        }
        val upcomingYear = upcomingCal.get(Calendar.YEAR)
        val upcomingMonth = upcomingCal.get(Calendar.MONTH)

        val allReminders = transactions.filter { it.type == "Reminder" || it.type == "Remainder" }
        val grouped = allReminders.groupBy { formatYesBankBeneficiary(it.beneficiary) }
        
        val activeCurrent = mutableListOf<TransactionSMS>()
        val completedCurrent = mutableListOf<TransactionSMS>()
        val past = mutableListOf<TransactionSMS>()
        
        for ((_, txList) in grouped) {
            val sorted = txList.sortedByDescending { it.timestamp }
            val latest = sorted.first()
            
            val dueCal = parseDueDate(latest.rawSms) ?: Calendar.getInstance().apply { timeInMillis = latest.timestamp }
            val isCurrentOrUpcoming = (dueCal.get(Calendar.YEAR) == currentYear && dueCal.get(Calendar.MONTH) == currentMonth) || 
                                      (dueCal.get(Calendar.YEAR) == upcomingYear && dueCal.get(Calendar.MONTH) == upcomingMonth)
            val isGreaterEqualToday = !dueCal.before(todayCal)
            
            if (latest.isCompleted) {
                if (isGreaterEqualToday) {
                    completedCurrent.add(latest)
                } else {
                    past.add(latest)
                }
            } else {
                if (isCurrentOrUpcoming) {
                    activeCurrent.add(latest)
                } else {
                    past.add(latest)
                }
            }
        }
        Triple(activeCurrent, completedCurrent, past)
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val searchResults = remember(transactions, searchQuery, isSearchActive) {
        if (!isSearchActive || searchQuery.isEmpty()) {
            emptyList<TransactionSMS>()
        } else {
            val q = searchQuery.trim().lowercase(Locale.US)
            transactions.filter { tx ->
                tx.category.lowercase(Locale.US).contains(q) ||
                tx.beneficiary.lowercase(Locale.US).contains(q) ||
                tx.rawSms.lowercase(Locale.US).contains(q) ||
                tx.type.lowercase(Locale.US).contains(q) ||
                tx.accountIdentifier.lowercase(Locale.US).contains(q) ||
                tx.amount.toString().contains(q)
            }
        }
    }

    val topFive = remember(transactions) {
        transactions.filter { it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" }.take(5)
    }

    val pastRemindersGrouped = remember(pastReminders) {
        pastReminders.groupBy { due ->
            val dueCal = parseDueDate(due.rawSms) ?: Calendar.getInstance().apply { timeInMillis = due.timestamp }
            val sdf = SimpleDateFormat("MMMM yy", Locale.US)
            sdf.format(dueCal.time)
        }.toList().sortedByDescending { (monthYearStr, _) ->
            val sdf = SimpleDateFormat("MMMM yy", Locale.US)
            try {
                sdf.parse(monthYearStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    val dailySpends = remember(transactions) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("EEE\nd/M", Locale.US)
        
        // Generate the last 7 days (6 days ago through today)
        val daysList = (0..6).map { daysAgo ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            cal
        }.reversed()
        
        daysList.map { cal ->
            val dateKey = sdf.format(cal.time)
            val label = displayFormat.format(cal.time)
            
            val totalOnDay = transactions.filter { tx ->
                val txDateKey = sdf.format(Date(tx.timestamp))
                txDateKey == dateKey && tx.type != "Credit" && tx.type != "Reminder" && tx.type != "Remainder" && tx.type != "Not a Transaction" && tx.type != "Credit Card Payment" && !tx.category.equals("Transfer", ignoreCase = true)
            }.sumOf { it.amount }
            
            DailySpendData(
                dateKey = dateKey,
                label = label,
                amount = totalOnDay
            )
        }
    }

    val maxSpend = remember(dailySpends) {
        val maxVal = dailySpends.maxOfOrNull { it.amount } ?: 0.0
        if (maxVal == 0.0) 1.0 else maxVal
    }

    val totalWeeklyExpenses = remember(dailySpends) {
        dailySpends.sumOf { it.amount }
    }

    val averageDailyExpenses = remember(totalWeeklyExpenses) {
        totalWeeklyExpenses / 7.0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Line 1: Header (Hi Imthiyaz + Subtitle + Search Icon & controls)
        item {
            if (isSearchActive) {
                // Active search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search categories, merchants, text...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Imthiyaz",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Your June snapshot is complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Control and Action bar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var showThemeDialog by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = { showThemeDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("theme_toggle_button")
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                        ) {
                            val themeIcon = when (themeMode) {
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            }
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = "Theme Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showThemeDialog) {
                            ThemeSelectionDialog(
                                currentThemeMode = themeMode,
                                onThemeSelected = { onThemeChange(it) },
                                onDismiss = { showThemeDialog = false }
                            )
                        }

                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Logs",
                                tint = MaterialTheme.colorScheme.onSurface,
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
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan SMS Inbox",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isSearchActive) {
            // Search Active Sections
            item {
                Text(
                    text = if (searchQuery.isEmpty()) "Search Transactions" else "Search Results (${searchResults.size} found)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            if (searchQuery.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Type above to search transactions",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Search by category, amount, merchant, or SMS text description",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (searchResults.isNotEmpty()) {
                items(searchResults) { tx ->
                    TransactionListItemRow(
                        tx = tx,
                        onClick = { onTransactionClick(tx) },
                        onIconClick = { onCategoryClick?.invoke(tx) }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No transactions match your query '$searchQuery'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Line 2: 1-Week Daily Spends Custom Bar Chart (replacing Total Monthly Spends)
            item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Card Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Weekly Tracker Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "WEEKLY TRACKER",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Daily Spends (Last 7 Days)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(Locale.getDefault(), "₹%,.0f", totalWeeklyExpenses),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Weekly Total",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Custom Bar Chart Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dailySpends.forEach { item ->
                            val barHeightFraction = if (maxSpend > 0) (item.amount / maxSpend).toFloat() else 0f
                            val isToday = item.dateKey == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onDayClick?.invoke(item.dateKey)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                // Value above bar (compact formatting)
                                if (item.amount > 0) {
                                    Text(
                                        text = if (item.amount >= 1000) {
                                            String.format(Locale.getDefault(), "₹%.1fk", item.amount / 1000.0)
                                        } else {
                                            String.format(Locale.getDefault(), "₹%.0f", item.amount)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        maxLines = 1
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(14.dp)) // Placeholder space
                                }
                                
                                // Bar
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(maxOf(4.dp, (barHeightFraction * 75).dp)) // Scale bar up to 75.dp max
                                        .background(
                                            color = when {
                                                item.amount == 0.0 -> Color.White.copy(alpha = 0.08f)
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> AquaTertiary.copy(alpha = 0.85f)
                                            },
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
                                        )
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // Day & Date label
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 9.sp, 
                                        lineHeight = 11.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Metric details footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Daily Average Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "Daily Average: ₹%,.0f/day", averageDailyExpenses),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Text(
                            text = "Swipe tabs for analytics",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trends", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trends", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // SECTION 1.1: Only Accounts
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (approvedAccounts.isNotEmpty() || rejectedAccounts.isNotEmpty()) {
                        IconButton(
                            onClick = onResetAllAccounts,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Approvals",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "VIEW ALL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showAllAccountsSheet = true }
                )
            }
        }

        if (accountBalances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No accounts configured yet. Seed/sync data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val previewAccounts = accountBalances.take(2)
            items(previewAccounts) { accBalance ->
                val cleanAccName = formatAccountDisplayName(accBalance.accountIdentifier)
                val isApproved = approvedAccounts.contains(accBalance.accountIdentifier)
                val balAmount = accBalance.remainingBalance

                AccountFeaturedCard(
                    headerTitle = if (accBalance.accountIdentifier.contains("Bank", ignoreCase = true)) "Bank Account" else "Account Balance",
                    headerRightText = if (isApproved) "Tracked" else "Track?",
                    title = cleanAccName,
                    amountText = String.format(Locale.getDefault(), "₹%,.2f", balAmount),
                    amountSubtitle = null,
                    isApprovedOrCompleted = isApproved,
                    badgeIcon = Icons.Default.AccountBalance,
                    onActionClick = {
                        if (isApproved) onRejectAccount(accBalance.accountIdentifier)
                        else onApproveAccount(accBalance.accountIdentifier)
                    },
                    onClick = { showAllAccountsSheet = true },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // SECTION 1.2: Only Reminders
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reminders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "VIEW ALL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showAllRemindersSheet = true }
                )
            }
        }

        if (activeRemindersCurrent.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No active reminders found for current/upcoming month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val previewReminders = activeRemindersCurrent.take(2)
            items(previewReminders) { due ->
                val dueLabel = formatYesBankBeneficiary(due.beneficiary)
                val daysRemaining = calculateDaysRemainingText(due.rawSms)
                val dueDateHeader = formatDueDateHeader(due.rawSms)

                AccountFeaturedCard(
                    headerTitle = dueDateHeader,
                    headerRightText = daysRemaining,
                    title = dueLabel,
                    amountText = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                    amountSubtitle = null,
                    isApprovedOrCompleted = due.isCompleted,
                    badgeIcon = Icons.Default.CreditCard,
                    onActionClick = { onUpdateReminderCompleted(due.id, !due.isCompleted) },
                    onClick = { onTransactionClick(due) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (completedRemindersCurrent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCompletedReminders = !showCompletedReminders }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Completed Reminders (${completedRemindersCurrent.size})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showCompletedReminders) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Completed Reminders",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (showCompletedReminders) {
                items(completedRemindersCurrent) { due ->
                    val dueLabel = formatYesBankBeneficiary(due.beneficiary)
                    val customLabel = if (due.type == "Reminder") {
                        val mStr = java.util.regex.Pattern.compile("(?i)due on\\s+([^.\\s]+)").matcher(due.rawSms)
                        if (mStr.find()) {
                            "Paid (Due was ${formatDueDateString(mStr.group(1)?.removeSuffix(".") ?: "")})"
                        } else {
                            "Paid"
                        }
                    } else {
                        "Completed"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(0.6f)
                            .padding(vertical = 4.dp)
                            .clickable { onTransactionClick(due) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { onUpdateReminderCompleted(due.id, false) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "Mark as active",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = dueLabel,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = TextDecoration.LineThrough
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = customLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        textDecoration = TextDecoration.LineThrough
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pastReminders.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPastRemindersSheet = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Past Reminders",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Past Reminders (${pastReminders.size})",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "VIEW MORE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "SEE ALL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToTab(1) } // link immediately to screen 2
                )
            }
        }

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
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent transactions found.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the Play icon (▶) in the header to seed gorgeous template data instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        } // Close our 'else' block
    }

    if (showAllAccountsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllAccountsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Bank Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showAllAccountsSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (accountBalances.isEmpty()) {
                        item {
                            Text(
                                text = "No configured accounts yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(accountBalances) { accBalance ->
                            val cleanAccName = formatAccountDisplayName(accBalance.accountIdentifier)
                            val isApproved = approvedAccounts.contains(accBalance.accountIdentifier)
                            val balAmount = accBalance.remainingBalance

                            AccountFeaturedCard(
                                headerTitle = if (isApproved) "Active Account" else "New Account",
                                headerRightText = if (isApproved) "Tracked" else "Track?",
                                title = cleanAccName,
                                amountText = String.format(Locale.getDefault(), "₹%,.2f", balAmount),
                                amountSubtitle = null,
                                isApprovedOrCompleted = isApproved,
                                badgeIcon = Icons.Default.AccountBalance,
                                onActionClick = {
                                    if (isApproved) onRejectAccount(accBalance.accountIdentifier)
                                    else onApproveAccount(accBalance.accountIdentifier)
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAllRemindersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllRemindersSheet = false },
            containerColor = Color(0xFF101418),
            contentColor = Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Reminders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = { showAllRemindersSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        Text(
                            text = "Active Reminders (${activeRemindersCurrent.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color(0xFF4DD0E1),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (activeRemindersCurrent.isEmpty()) {
                        item {
                            Text(
                                text = "No active reminders found for current/upcoming month.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7A8A99),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(activeRemindersCurrent) { due ->
                            val dueLabel = formatYesBankBeneficiary(due.beneficiary)
                            val customLabel = if (due.type == "Reminder" || due.type == "Remainder") {
                                "Reminder Alert"
                            } else {
                                "Estimated cycle"
                            }

                            ReminderItemCard(
                                title = dueLabel,
                                subtitle = customLabel,
                                amountText = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                                isCompleted = false,
                                onToggleCompleted = { onUpdateReminderCompleted(due.id, true) },
                                onClick = {
                                    showAllRemindersSheet = false
                                    onTransactionClick(due)
                                }
                            )
                        }
                    }

                    if (completedRemindersCurrent.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Completed Reminders (${completedRemindersCurrent.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = Color(0xFF4DD0E1),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(completedRemindersCurrent) { due ->
                            val dueLabel = formatYesBankBeneficiary(due.beneficiary)

                            ReminderItemCard(
                                title = dueLabel,
                                subtitle = "Paid",
                                amountText = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                                isCompleted = true,
                                onToggleCompleted = { onUpdateReminderCompleted(due.id, false) },
                                onClick = {
                                    showAllRemindersSheet = false
                                    onTransactionClick(due)
                                }
                            )
                        }
                    }

                    if (pastReminders.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Past Reminders (${pastReminders.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = Color(0xFF4DD0E1),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        pastRemindersGrouped.forEach { (monthYearStr, dues) ->
                            item {
                                Text(
                                    text = monthYearStr,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color(0xFF4DD0E1),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            items(dues) { due ->
                                val dueLabel = formatYesBankBeneficiary(due.beneficiary)

                                ReminderItemCard(
                                    title = dueLabel,
                                    subtitle = "Paid",
                                    amountText = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                                    isCompleted = true,
                                    onToggleCompleted = { onUpdateReminderCompleted(due.id, !due.isCompleted) },
                                    onClick = {
                                        showAllRemindersSheet = false
                                        onTransactionClick(due)
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showPastRemindersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPastRemindersSheet = false },
            containerColor = Color(0xFF101418),
            contentColor = Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Past Reminders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = { showPastRemindersSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (pastRemindersGrouped.isEmpty()) {
                        item {
                            Text(
                                text = "No past reminders found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7A8A99),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        pastRemindersGrouped.forEach { (monthYearStr, dues) ->
                            item {
                                Text(
                                    text = monthYearStr,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color(0xFF4DD0E1),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            items(dues) { due ->
                                val dueLabel = formatYesBankBeneficiary(due.beneficiary)

                                ReminderItemCard(
                                    title = dueLabel,
                                    subtitle = "Paid",
                                    amountText = String.format(Locale.getDefault(), "₹%,.0f", due.amount),
                                    isCompleted = due.isCompleted,
                                    onToggleCompleted = { onUpdateReminderCompleted(due.id, !due.isCompleted) },
                                    onClick = {
                                        showPastRemindersSheet = false
                                        onTransactionClick(due)
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}

// ==========================================
// SCREEN 2: ALL TRANSACTIONS & CATEGORIES SCREEN (image_1.png / image_2.png)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisDetailedScreen(
    transactions: List<TransactionSMS>,
    totalExpenses: Double,
    monthlyBudgetLimit: Double = 50000.0,
    onSetBudgetClick: (() -> Unit)? = null,
    onTransactionClick: (TransactionSMS) -> Unit,
    onCategoryClick: ((TransactionSMS) -> Unit)? = null,
    selectedFilterMonthLabel: String? = null,
    selectedFilterDateKey: String? = null,
    onClearFilter: (() -> Unit)? = null,
    analysisTypeFilter: String? = null
) {
    var subTabState by remember { mutableStateOf(0) } // 0 = Transactions, 1 = Categories, 2 = Merchants
    var selectedCategoryForDetail by remember { mutableStateOf<CategoryAgg?>(null) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Analytics Detail",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "A complete breakdown of structured SMS ledgers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                val filterText = when {
                    selectedFilterDateKey != null -> {
                        try {
                            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val formatter = SimpleDateFormat("d MMM", Locale.US)
                            val date = parser.parse(selectedFilterDateKey)
                            val base = if (date != null) formatter.format(date) else selectedFilterDateKey
                            if (analysisTypeFilter != null) "$base $analysisTypeFilter" else base
                        } catch (e: Exception) {
                            selectedFilterDateKey
                        }
                    }
                    selectedFilterMonthLabel != null -> {
                        if (analysisTypeFilter != null) "$selectedFilterMonthLabel $analysisTypeFilter" else selectedFilterMonthLabel
                    }
                    else -> analysisTypeFilter
                }
                
                if (filterText != null) {
                    FilledTonalButton(
                        onClick = { onClearFilter?.invoke() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Show All ($filterText ✕)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
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
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = "outfacing arrow",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val labelText = when {
                    analysisTypeFilter == "Income" -> if (selectedFilterDateKey != null) "DAILY INCOME" else "MONTHLY INCOME"
                    analysisTypeFilter == "Expenses" -> if (selectedFilterDateKey != null) "DAILY SPENDS" else "MONTHLY SPENDS"
                    else -> if (selectedFilterDateKey != null) "DAILY SPENDS" else "MONTHLY SPENDS"
                }
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.getDefault(), "₹%,.0f", totalExpenses),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Monthly Budget Tracker Bar
        val progress = if (monthlyBudgetLimit > 0) (totalExpenses / monthlyBudgetLimit).coerceIn(0.0, 1.0).toFloat() else 0f
        val isOver = totalExpenses > monthlyBudgetLimit
        val remaining = monthlyBudgetLimit - totalExpenses

        Surface(
            onClick = { onSetBudgetClick?.invoke() },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Budget Icon",
                            tint = Color(0xFF9EA7FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Spent: ₹" + String.format(Locale.US, "%,.0f", totalExpenses) + " / Budget: ₹" + String.format(Locale.US, "%,.0f", monthlyBudgetLimit),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isOver) Color(0xFFFF5252) else if (progress >= 0.85f) Color(0xFFFFB74D) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isOver) "Over budget" else "₹" + String.format(Locale.US, "%,.0f", remaining) + " left",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isOver) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Edit limit ✎",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tab Selector Row (Transactions / Categories / Merchants)
        TabRow(
            selectedTabIndex = subTabState,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (subTabState < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[subTabState]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }
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
                    val nonReminderTransactions = remember(transactions) {
                        transactions.filter { it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" }
                    }
                    if (nonReminderTransactions.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(nonReminderTransactions) { tx ->
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
                                CategoryCardView(
                                    cat = cat,
                                    onClick = { selectedCategoryForDetail = cat }
                                )
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

    // Category transactions popup sheet
    val currentCatDetail = selectedCategoryForDetail
    if (currentCatDetail != null) {
        val cat = currentCatDetail
        val catTransactions = remember(transactions, cat.category) {
            transactions.filter { tx ->
                tx.category.equals(cat.category, ignoreCase = true) && tx.type != "Reminder" && tx.type != "Remainder" && tx.type != "Not a Transaction"
            }
        }
        ModalBottomSheet(
            onDismissRequest = {
                selectedCategoryForDetail = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 16.dp,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header inside BottomSheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(cat.color.copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, cat.color.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            when (val icon = cat.icon) {
                                is CategoryIcon.Vector -> {
                                    Icon(
                                        imageVector = icon.imageVector,
                                        contentDescription = cat.category,
                                        tint = cat.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                is CategoryIcon.Character -> {
                                    Text(
                                        text = icon.char.toString(),
                                        color = cat.color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                is CategoryIcon.OthersSpecial -> {
                                    OthersIcon(size = 18.dp)
                                }
                            }
                        }
                        Column {
                            Text(
                                text = cat.category,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${catTransactions.size} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = String.format(Locale.getDefault(), "₹%,.2f", cat.amount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = cat.color
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (catTransactions.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxHeight(0.6f)
                    ) {
                        items(catTransactions) { tx ->
                            TransactionListItemRow(
                                tx = tx,
                                onClick = { 
                                    onTransactionClick(tx)
                                    selectedCategoryForDetail = null
                                },
                                onIconClick = { onCategoryClick?.invoke(tx) }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions found in this category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Categories",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Category Row Visual Card
@Composable
fun CategoryCardView(cat: CategoryAgg, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                // Large circular colored category icon card
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(cat.color.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, cat.color.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (val icon = cat.icon) {
                        is CategoryIcon.Vector -> {
                            Icon(
                                imageVector = icon.imageVector,
                                contentDescription = cat.category,
                                tint = cat.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        is CategoryIcon.Character -> {
                            Text(
                                text = icon.char.toString(),
                                color = cat.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        is CategoryIcon.OthersSpecial -> {
                            OthersIcon(size = 20.dp)
                        }
                    }
                }

                Column {
                    Text(
                        text = cat.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${cat.count} spent events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Merchant icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = merchant.merchant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${merchant.count} interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = String.format(Locale.getDefault(), "₹%,.2f", merchant.amount),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ==========================================
// SCREEN 3: ADVANCED MONTHLY TRENDS SCREEN (image_4.png replica)
// ==========================================
@Composable
fun AdvancedTrendsScreen(
    transactions: List<TransactionSMS>,
    totalExpenses: Double,
    totalIncome: Double,
    monthlyBudgetLimit: Double = 50000.0,
    onSetBudgetClick: () -> Unit = {},
    onReviewMonth: (String, String) -> Unit
) {
    val monthlyTrendList = remember(transactions) { getMonthlyTrendData(transactions) }
    
    // Default selected month to the latest one (usually the far-right index 35)
    var selectedMonthLabel by remember(monthlyTrendList) {
        mutableStateOf(monthlyTrendList.lastOrNull()?.monthLabel ?: "")
    }

    // Dynamic filtering based on selection
    val selectedMonthTransactions = remember(transactions, selectedMonthLabel) {
        transactions.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val m = SimpleDateFormat("MMM", Locale.US).format(txCal.time)
            val y = SimpleDateFormat("yy", Locale.US).format(txCal.time)
            "$m'$y" == selectedMonthLabel
        }
    }

    // Dynamic metrics calculated for the selected month
    val selectedSpends = remember(selectedMonthTransactions) {
        selectedMonthTransactions.filter { it.type != "Credit" && it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" && it.type != "Credit Card Payment" && !it.category.equals("Transfer", ignoreCase = true) }.sumOf { it.amount }
    }
    val selectedIncome = remember(selectedMonthTransactions) {
        selectedMonthTransactions.filter { it.type == "Credit" && !it.category.equals("Transfer", ignoreCase = true) }.sumOf { it.amount }
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 1. Top Header Row (Back button, Title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    Toast.makeText(context, "Navigating back...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 2. The Custom Interactive Scrollable Dual-Axis Chart Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(262.dp)
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = constraints.maxWidth.toFloat() - with(density) { 48.dp.toPx() }
            val scrollState = rememberScrollState()

            val columnWidth = 65.dp
            val chartHeight = 180.dp

            val columnWidthPx = with(density) { columnWidth.toPx() }

            // Dynamic safeLimitVal based on shown months in current scroll screen
            val safeLimitVal by remember(monthlyTrendList, scrollState.value, viewportWidthPx, columnWidthPx) {
                derivedStateOf {
                    val startScroll = scrollState.value.toFloat()
                    val endScroll = startScroll + viewportWidthPx

                    val visible = monthlyTrendList.filterIndexed { index: Int, _: com.example.MonthlyTrendData ->
                        val colStart = index * columnWidthPx
                        val colEnd = (index + 1) * columnWidthPx
                        colEnd >= startScroll && colStart <= endScroll
                    }

                    val maxAmount = if (visible.isEmpty()) 1.0 else {
                        maxOf(
                            visible.maxOfOrNull { it.expenses } ?: 1.0,
                            visible.maxOfOrNull { it.income } ?: 1.0,
                            1.0
                        )
                    }
                    val limitVal = maxAmount * 1.15
                    if (limitVal <= 0.0) 1.0 else limitVal
                }
            }

            // Helper format for Indian currency numbering with safe US Locale: e.g. 1.72L or 86.2K
            fun formatYAxisValue(value: Double): String {
                return when {
                    value >= 100000.0 -> String.format(Locale.US, "%.1fL", value / 100000.0)
                    value >= 1000.0 -> String.format(Locale.US, "%.1fK", value / 1000.0)
                    else -> String.format(Locale.US, "%.0f", value)
                }
            }

            // Helper format for chart value labels above data lines/bars: e.g. 2.15L or 1.2K
            fun formatChartValueLabel(value: Double): String {
                return when {
                    value >= 100000.0 -> String.format(Locale.US, "%.2fL", value / 100000.0)
                    value >= 1000.0 -> String.format(Locale.US, "%.1fK", value / 1000.0)
                    else -> String.format(Locale.US, "%.0f", value)
                }
            }

            // Fixed Y-Axis gridlines & labels drawn in the background
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(1.0, 0.75, 0.5, 0.25, 0.0).forEach { ratio ->
                    val yLabel = formatYAxisValue(safeLimitVal * ratio)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Axis Label
                        Text(
                            text = yLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(46.dp),
                            maxLines = 1
                        )
                        // Grid Line
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        )
                    }
                }
            }

            // Proactively Scroll to the latest month (current month at far-right index 35)
            LaunchedEffect(monthlyTrendList) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp) // shift precisely right of Y-axis label column
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                ) {
                    val totalWidth = columnWidth * monthlyTrendList.size

                    // Bottom elements inside the scroll container
                    Column(modifier = Modifier.width(totalWidth)) {
                        val linePrimaryColor = MaterialTheme.colorScheme.primary
                        val lineOnSurfaceColor = MaterialTheme.colorScheme.onSurface
                        // Custom drawing canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(chartHeight)
                        ) {
                            val columnWidthPx = columnWidth.toPx()
                            val canvasHeight = size.height

                            // Draw Expenses Rounded Bars (subtle Blue)
                            monthlyTrendList.forEachIndexed { i, d ->
                                val cx = (i * columnWidthPx) + (columnWidthPx / 2)
                                val barHeight = canvasHeight * (d.expenses / safeLimitVal).toFloat()
                                val barTop = canvasHeight - barHeight
                                val barWidthPx = 16.dp.toPx()

                                drawRoundRect(
                                    color = Color(0xFF536DFE), // Beautiful vivid blue
                                    topLeft = Offset(cx - (barWidthPx / 2), barTop),
                                    size = Size(barWidthPx, barHeight),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                            }

                            // Draw Income connected line series (Vibrant green path)
                            val linePoints = monthlyTrendList.mapIndexed { idx, d ->
                                val cx = (idx * columnWidthPx) + (columnWidthPx / 2)
                                val cy = canvasHeight - (canvasHeight * (d.income / safeLimitVal)).toFloat()
                                Offset(cx, cy)
                            }

                            if (linePoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(linePoints[0].x, linePoints[0].y)
                                    for (p in 1 until linePoints.size) {
                                        lineTo(linePoints[p].x, linePoints[p].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = linePrimaryColor,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // Draw selection highlights and line dots
                            monthlyTrendList.forEachIndexed { i, d ->
                                val cx = (i * columnWidthPx) + (columnWidthPx / 2)
                                val cy = canvasHeight - (canvasHeight * (d.income / safeLimitVal)).toFloat()

                                // If this label matches the selected one, draw a vertical highlight
                                if (d.monthLabel == selectedMonthLabel) {
                                    drawRoundRect(
                                        color = lineOnSurfaceColor.copy(alpha = 0.08f),
                                        topLeft = Offset(i * columnWidthPx, 0f),
                                        size = Size(columnWidthPx, canvasHeight + 20.dp.toPx()),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                }

                                // Neon Circle Mark for Income nodes
                                drawCircle(
                                    color = linePrimaryColor,
                                    radius = 6.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                // Inner white dot outline
                                drawCircle(
                                    color = lineOnSurfaceColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }

                        // Bottom labels row
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        ) {
                            monthlyTrendList.forEachIndexed { idx, d ->
                                val cleanLabel = if (idx == 0) "6" else d.monthLabel
                                Box(
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val isSelected = d.monthLabel == selectedMonthLabel
                                    Text(
                                        text = cleanLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Value labels overlay drawn above bars and dots
                    Row(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(chartHeight)
                    ) {
                        monthlyTrendList.forEach { d ->
                            Box(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .fillMaxHeight()
                            ) {
                                // Spends Label (shifted slightly to the left to prevent overlapping)
                                if (d.expenses > 0) {
                                    val barHeightFraction = (d.expenses / safeLimitVal).toFloat()
                                    val barHeightDp = chartHeight * barHeightFraction
                                    val spendsOffset = (chartHeight - barHeightDp - 14.dp).coerceAtLeast(2.dp)
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(x = (-15).dp, y = spendsOffset)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = formatChartValueLabel(d.expenses),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color(0xFFC5CAE9),
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Income Label (shifted slightly to the right to prevent overlapping)
                                if (d.income > 0) {
                                    val incomeHeightFraction = (d.income / safeLimitVal).toFloat()
                                    val incomeHeightDp = chartHeight * incomeHeightFraction
                                    val incomeOffset = (chartHeight - incomeHeightDp - 18.dp).coerceAtLeast(2.dp)
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(x = 15.dp, y = incomeOffset)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 3.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = formatChartValueLabel(d.income),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Click detection Overlay on columns
                    Row(modifier = Modifier.width(totalWidth)) {
                        monthlyTrendList.forEach { d ->
                            Box(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedMonthLabel = d.monthLabel
                                    }
                            )
                        }
                    }
                }

                // 3. Scrollbar Scroll slider indicator below the scroll area
                Spacer(modifier = Modifier.height(10.dp))
                val scrollMax = scrollState.maxValue.coerceAtLeast(1)
                val ratio = scrollState.value.toFloat() / scrollMax
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally)
                ) {
                    val handleWidth = 24.dp
                    Box(
                        modifier = Modifier
                            .offset(x = (130.dp - handleWidth) * ratio)
                            .width(handleWidth)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(1.5.dp))
                    )
                }
            }
        }

        // 3. Month Header Display and Dropdown Select
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedMonthLabel,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .clickable {
                        Toast.makeText(context, "Account filters click. Toggle display standard sets.", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "All accounts",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 4. Custom Metrics Section: Big concentric spends on left + stacked on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Concentric circle Spends card on left
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant) // gorgeous deep blue-grey container
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .clickable {
                        onReviewMonth(selectedMonthLabel, "Expenses")
                    }
            ) {
                // Background Concentric Rings (matching screenshot)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.7f
                    val radii = listOf(35.dp.toPx(), 65.dp.toPx(), 95.dp.toPx(), 125.dp.toPx(), 155.dp.toPx())
                    radii.forEach { r ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.045f),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                // Main card values
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFF536DFE), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Spends",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowOutward,
                            contentDescription = "Trend up-right arrow",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "₹ %,.2f", selectedSpends),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Right Stack of two smaller cards (Income top, Budget bottom)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Green Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer) // Rich forest emerald
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .clickable {
                            onReviewMonth(selectedMonthLabel, "Income")
                        }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "₹ %,.0f", selectedIncome),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Hidden balance toggle icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).clickable {
                                Toast.makeText(context, "Balance hidden/shown", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Interactive Monthly Budget Tracking Card
                val spent = selectedSpends
                val limit = monthlyBudgetLimit
                val progress = if (limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f
                val actualPercentage = if (limit > 0) ((spent / limit) * 100).toInt() else 0
                val isOverBudget = spent > limit
                val remaining = limit - spent

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isOverBudget) Color(0xFF3E1B1B)
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (isOverBudget) Color(0xFFFF5252).copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { onSetBudgetClick() }
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isOverBudget) Color(0xFFFF5252).copy(alpha = 0.2f)
                                            else Color(0xFF9EA7FC).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = "Budget Icon",
                                        tint = if (isOverBudget) Color(0xFFFF5252) else Color(0xFF9EA7FC),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = "Monthly Budget",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit budget limit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isOverBudget) "Over by ₹" + String.format(Locale.US, "%,.0f", spent - limit)
                                           else "₹" + String.format(Locale.US, "%,.0f", remaining) + " left",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isOverBudget) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${actualPercentage}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = when {
                                    isOverBudget -> Color(0xFFFF5252)
                                    progress >= 0.85f -> Color(0xFFFFB74D)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. Dynamic Prominent Action Button at the extremely bottom
        Button(
            onClick = {
                onReviewMonth(selectedMonthLabel, "Expenses")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "Review $selectedMonthLabel",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            )
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
                        .then(
                            if (onIconClick != null) {
                                Modifier.clickable { onIconClick() }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (icon) {
                        is CategoryIcon.Vector -> {
                            Icon(
                                imageVector = icon.imageVector,
                                contentDescription = tx.category,
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        is CategoryIcon.Character -> {
                            Text(
                                text = icon.char.toString(),
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        is CategoryIcon.OthersSpecial -> {
                            OthersIcon(size = 20.dp)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatYesBankBeneficiary(tx.beneficiary),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val dateFormatted = remember(tx.timestamp) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(tx.timestamp))
                    }
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isTransfer = tx.category.equals("Transfer", ignoreCase = true)
            val isCredit = tx.type == "Credit"
            val labelPrefix = if (isTransfer) "⇄ " else if (isCredit) "+" else "-"
            val textColorVal = if (isTransfer) MaterialTheme.colorScheme.secondary else if (isCredit) MaterialTheme.colorScheme.primary else Color(0xFFEF5350)

            Text(
                text = String.format(Locale.getDefault(), "%s₹%,.0f", labelPrefix, tx.amount),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = textColorVal
            )
        }
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
                .then(
                    if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    else Modifier
                )
                .padding(horizontal = 22.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.3.sp),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onEditCategory: () -> Unit,
    onEditType: () -> Unit
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
                color = MaterialTheme.colorScheme.onSurface
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Key variables Table Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
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
                valueColor = MaterialTheme.colorScheme.primary
            )
            SheetDetailRow(
                label = "Beneficiary",
                value = formatYesBankBeneficiary(transaction.beneficiary)
            )
            SheetDetailRow(
                label = "Account Info",
                value = transaction.accountIdentifier
            )
            val isSheetTxTransfer = transaction.category.equals("Transfer", ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditType() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val typeValue = if (isSheetTxTransfer) "Transfer" else transaction.type
                    val typeColor = if (isSheetTxTransfer) MaterialTheme.colorScheme.secondary else if (transaction.type == "Credit") MaterialTheme.colorScheme.primary else if (transaction.type == "Reminder") Color(0xFFFF8A80) else if (transaction.type == "Credit Card Payment") Color(0xFFFFB74D) else Color(0xFFEF5350)
                    Text(
                        text = typeValue,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = typeColor
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        when (val icon = catAsset.first) {
                            is CategoryIcon.Vector -> {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = transaction.category,
                                    tint = catAsset.second,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            is CategoryIcon.Character -> {
                                Text(
                                    text = icon.char.toString(),
                                    color = catAsset.second,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            is CategoryIcon.OthersSpecial -> {
                                OthersIcon(size = 14.dp)
                            }
                        }
                    }
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Category",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Sender: ${transaction.sender}\n\n${transaction.rawSms}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }

    }
}

@Composable
fun SheetDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Dynamic Assets helper mapping categories to corresponding icons and color tones
fun getCategoryAsset(category: String): Pair<CategoryIcon, Color> {
    return when (category) {
        "Bills" -> CategoryIcon.Vector(Icons.AutoMirrored.Filled.ReceiptLong) to Color(0xFF4CAF50)          // Green
        "EMI" -> CategoryIcon.Vector(Icons.Default.AccountBalance) to Color(0xFF9E9E9E)        // Grey
        "Entertainment" -> CategoryIcon.Vector(Icons.Default.Celebration) to Color(0xFF3F51B5)  // Indigo Blue
        "Food & Drinks" -> CategoryIcon.Vector(Icons.Default.Restaurant) to Color(0xFFE91E63)   // Pink/Magenta
        "Fuel" -> CategoryIcon.Vector(Icons.Default.LocalGasStation) to Color(0xFFFF9800)       // Amber Orange
        "Groceries" -> CategoryIcon.Vector(Icons.Default.LocalGroceryStore) to Color(0xFF8BC34A)  // Light Green
        "Health" -> CategoryIcon.Vector(Icons.Default.MedicalServices) to Color(0xFFFFB300)     // Yellow/Gold
        "Investment" -> CategoryIcon.Vector(Icons.AutoMirrored.Filled.TrendingUp) to Color(0xFF607D8B) // Blue Grey/Slate (Modern financial trend up icon)
        "Other", "Others" -> CategoryIcon.OthersSpecial to Color(0xFFE53935)                  // Red exclamation with black background
        "Shopping" -> CategoryIcon.Vector(Icons.Default.ShoppingBag) to Color(0xFF00ACC1)       // Teal
        "Transfer" -> CategoryIcon.Vector(Icons.Default.SwapHoriz) to Color(0xFF9C27B0)         // Purple
        "Travel" -> CategoryIcon.Vector(Icons.Default.Flight) to Color(0xFF673AB7)              // Violet
        "Rent" -> CategoryIcon.Vector(Icons.Default.HomeWork) to Color(0xFF4CAF50)              // Green
        "Cash Withdrawl", "Cash Withdrawal" -> CategoryIcon.Vector(Icons.Default.Atm) to Color(0xFFFF9800) // Money/Cash Icon (Amber Orange)
        else -> {
            val trimmed = category.trim()
            val firstChar = if (trimmed.isNotEmpty()) {
                trimmed.first().uppercaseChar()
            } else {
                '?'
            }
            CategoryIcon.Character(firstChar) to Color(0xFFAB47BC)
        }
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
    val icon = asset.first

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (icon is CategoryIcon.OthersSpecial) Color.Transparent else color,
                    CircleShape
                )
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) Color.White else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is CategoryIcon.Vector -> {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = category,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                is CategoryIcon.Character -> {
                    Text(
                        text = icon.char.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                is CategoryIcon.OthersSpecial -> {
                    OthersIcon(size = 56.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

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
fun TypeSelectionSheet(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val types = listOf("Credit", "Debit", "Not a Transaction", "EMI", "SIP")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(MaterialTheme.colorScheme.surface)
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Change Transaction Type",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(types) { type ->
                val isSelected = type == selectedType || (type == "Not a Transaction" && (selectedType == "Remainder" || selectedType == "Reminder" || selectedType == "Not a Transaction"))
                
                Surface(
                    onClick = { onTypeSelected(type) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayType = when (type) {
                            "Debit" -> "Debit (Expense)"
                            "Not a Transaction" -> "Not a Transaction"
                            else -> type
                        }
                        Text(
                            text = displayType,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
        "Transfer", "Travel", "Rent", "Cash Withdrawl"
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
            .background(MaterialTheme.colorScheme.surface)
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Grid layout of categories
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = 12.dp)
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
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (newCategoryText.isEmpty()) {
                            Text(
                                "New category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Helpers for list aggregations inside Detailed Analysis
fun getCategorySpendList(transactions: List<TransactionSMS>): List<CategoryAgg> {
    val grouped = transactions.filter { it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" }.groupBy { it.category }

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
    val grouped = transactions.filter { it.type != "Reminder" && it.type != "Remainder" && it.type != "Not a Transaction" }.groupBy { it.beneficiary }

    return grouped.map { (merch, list) ->
        MerchantAgg(
            merchant = merch,
            amount = list.sumOf { it.amount },
            count = list.size
        )
    }.sortedByDescending { it.amount }
}

@Composable
fun SetBudgetDialog(
    currentBudget: Double,
    onSaveBudget: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetText by remember {
        mutableStateOf(if (currentBudget > 0) String.format(Locale.US, "%.0f", currentBudget) else "50000")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Set Monthly Budget",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Set your target monthly expense limit. Your debited transactions will be tracked against this budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.filter { char -> char.isDigit() } },
                    label = { Text("Monthly Limit (₹)") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(25000, 50000, 75000, 100000).forEach { preset ->
                        val isSelected = budgetText == preset.toString()
                        Surface(
                            onClick = { budgetText = preset.toString() },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            Text(
                                text = "₹${preset / 1000}k",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = budgetText.toDoubleOrNull() ?: currentBudget
                    if (amount > 0) {
                        onSaveBudget(amount)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeSelectionDialog(
    currentThemeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Theme",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeOptionItem(
                    title = "Dark Mode",
                    subtitle = "Always use dark theme",
                    icon = Icons.Default.DarkMode,
                    isSelected = currentThemeMode == ThemeMode.DARK,
                    onClick = {
                        onThemeSelected(ThemeMode.DARK)
                        onDismiss()
                    }
                )
                ThemeOptionItem(
                    title = "Light Mode",
                    subtitle = "Always use light theme",
                    icon = Icons.Default.LightMode,
                    isSelected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = {
                        onThemeSelected(ThemeMode.LIGHT)
                        onDismiss()
                    }
                )
                ThemeOptionItem(
                    title = "System Default",
                    subtitle = "Follow system appearance",
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = currentThemeMode == ThemeMode.SYSTEM,
                    onClick = {
                        onThemeSelected(ThemeMode.SYSTEM)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_option_${title.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Aggregated details is empty",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sync required or seed sample messages back on Home view.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

// Consolidated 36 months history tracker helper
fun getMonthlyTrendData(transactions: List<TransactionSMS>): List<MonthlyTrendData> {
    val format = SimpleDateFormat("MMM", Locale.US)
    val yearFormat = SimpleDateFormat("yy", Locale.US)

    val monthKeys = mutableListOf<String>()
    val monthIncomes = mutableMapOf<String, Double>()
    val monthExpenses = mutableMapOf<String, Double>()

    for (i in 35 downTo 0) {
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
        if (tx.type == "Reminder" || tx.type == "Remainder" || tx.type == "Not a Transaction" || tx.type == "Credit Card Payment") continue
        if (tx.category.equals("Transfer", ignoreCase = true)) continue
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

    // Defensive check: If results list contains fewer than 36 entries for any reason,
    // dynamically pad the list with empty placeholders to avoid IndexOutOfBoundsException
    if (result.size < 36) {
        val paddedResult = result.toMutableList()
        while (paddedResult.size < 36) {
            val missingCount = 36 - paddedResult.size
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

fun formatYesBankBeneficiary(beneficiary: String): String {
    val lower = beneficiary.lowercase(Locale.getDefault()).trim()
    if (lower == "yesbank 3349 card" || lower == "yesbank 3349") {
        return "Yes Bank 3349 Card"
    }
    val yesCardRegex = Regex("yesbank\\s+(\\d{3,6})\\s+card")
    val match = yesCardRegex.matchEntire(lower)
    if (match != null) {
        val digits = match.groupValues[1]
        return "Yes Bank $digits Card"
    }

    if (beneficiary.contains("YES BANK", ignoreCase = true) && beneficiary.contains("@")) {
        val indexAt = beneficiary.indexOf('@')
        if (indexAt != -1) {
            var afterAt = beneficiary.substring(indexAt + 1).trim()
            val dateRegex = Regex("(?i)\\b\\d{2}[-/]\\d{2}[-/]\\d{4}.*")
            afterAt = afterAt.replace(dateRegex, "").trim()
            
            val timeRegex = Regex("(?i)\\b\\d{2}:\\d{2}(?::\\d{2})?.*")
            afterAt = afterAt.replace(timeRegex, "").trim()

            var clean = afterAt
            while (clean.endsWith(".") || clean.endsWith(",") || clean.endsWith("-") || clean.endsWith("_")) {
                clean = clean.dropLast(1).trim()
            }
            if (clean.isNotBlank()) {
                return clean
            }
        }
    }
    return beneficiary
}

fun formatAccountDisplayName(identifier: String): String {
    return com.example.utils.TransactionParser.standardizeAccountIdentifier(identifier)
}

fun formatDueDateString(rawDate: String): String {
    val cleaned = rawDate.replace("\\", "/").replace("-", "/").trim()
    val parts = cleaned.split("/")
    if (parts.size >= 3) {
        val day = parts[0].toIntOrNull()
        val month = parts[1].toIntOrNull()
        var year = parts[2].trim().toIntOrNull()
        if (day != null && month != null && year != null) {
            val months = listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            if (month in 1..12) {
                val monthName = months[month - 1]
                if (year < 100) {
                    year += 2000
                }
                return String.format(Locale.getDefault(), "%02d %s %d", day, monthName, year)
            }
        }
    }
    return rawDate
}

fun parseDueDate(rawSms: String): Calendar? {
    val mStr = java.util.regex.Pattern.compile("(?i)due on\\s+([^.\\s]+)").matcher(rawSms)
    if (!mStr.find()) return null
    val rawDate = (mStr.group(1)?.removeSuffix(".") ?: "").trim().replace("\\", "/").replace("-", "/")
    val parts = rawDate.split("/")
    if (parts.size >= 3) {
        val day = parts[0].toIntOrNull() ?: return null
        val monthStr = parts[1]
        var year = parts[2].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        
        val month = monthStr.toIntOrNull()
        if (month != null) {
            if (month in 1..12) {
                val cal = Calendar.getInstance()
                cal.clear()
                cal.set(year, month - 1, day)
                return cal
            }
        } else {
            val months = listOf(
                "jan", "feb", "mar", "apr", "may", "jun",
                "jul", "aug", "sep", "oct", "nov", "dec"
            )
            val index = months.indexOf(monthStr.lowercase(Locale.US).take(3))
            if (index != -1) {
                val cal = Calendar.getInstance()
                cal.clear()
                cal.set(year, index, day)
                return cal
            }
        }
    }
    return null
}

fun formatDueDateHeader(rawSms: String): String {
    val cal = parseDueDate(rawSms)
    if (cal != null) {
        val sdf = java.text.SimpleDateFormat("dd MMM yy", Locale.US)
        return sdf.format(cal.time)
    }
    return "Due Soon"
}

fun calculateDaysRemainingText(rawSms: String): String {
    val cal = parseDueDate(rawSms) ?: return "Reminder"
    val today = Calendar.getInstance()
    val diffMillis = cal.timeInMillis - today.timeInMillis
    val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    return when {
        diffDays < 0 -> "Overdue"
        diffDays == 0 -> "Due today"
        diffDays == 1 -> "1 day to go"
        else -> "$diffDays days to go"
    }
}

@Composable
fun ReminderItemCard(
    title: String,
    subtitle: String = "Reminder Alert",
    amountText: String,
    isCompleted: Boolean = false,
    onToggleCompleted: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF161A1E)
    val cardBorder = Color(0xFF2C353F)
    val alertColor = Color(0xFFFF6B6B)
    val mutedText = Color(0xFF7A8A99)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (onToggleCompleted != null) {
                    IconButton(
                        onClick = onToggleCompleted,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.AutoMirrored.Filled.Undo else Icons.Outlined.CheckCircle,
                            contentDescription = if (isCompleted) "Mark active" else "Mark completed",
                            tint = if (isCompleted) mutedText else Color(0xFF4F5E6D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isCompleted) mutedText else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isCompleted) mutedText else alertColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (isCompleted) mutedText else alertColor
                )
                if (!isCompleted) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alert",
                        tint = alertColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AccountFeaturedCard(
    headerTitle: String,
    headerRightText: String? = null,
    title: String,
    amountText: String,
    amountSubtitle: String? = null,
    isApprovedOrCompleted: Boolean = true,
    badgeIcon: ImageVector = Icons.Default.CreditCard,
    onActionClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardBg = Color(0xFF222427)
    val headerBg = Color(0xFF2B2E33)
    val badgeBg = Color(0xFFBAC8D3)
    val badgeIconTint = Color(0xFF1C2024)
    val subTextColor = Color(0xFF9EA8B5)
    val limeGreen = Color(0xFF8CE63A)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        headerBg,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!headerRightText.isNullOrEmpty()) {
                    Text(
                        text = headerRightText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = subTextColor
                    )
                }
            }

            // Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color.White
                        )
                        if (!amountSubtitle.isNullOrEmpty()) {
                            Text(
                                text = " $amountSubtitle",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = subTextColor
                            )
                        }
                    }

                    if (onActionClick != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(
                                    BorderStroke(2.dp, if (isApprovedOrCompleted) limeGreen else Color(0xFFEF5350)),
                                    CircleShape
                                )
                                .background(
                                    if (isApprovedOrCompleted) limeGreen.copy(alpha = 0.12f) else Color(0xFFEF5350).copy(alpha = 0.12f)
                                )
                                .clickable { onActionClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isApprovedOrCompleted) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = "Action",
                                tint = if (isApprovedOrCompleted) limeGreen else Color(0xFFEF5350),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
