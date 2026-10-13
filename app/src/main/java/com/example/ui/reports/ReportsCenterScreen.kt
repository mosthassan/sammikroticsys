package com.example.ui.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.CurrencyCode
import com.example.core.model.Money
import com.example.core.time.AppClock
import com.example.core.time.FiscalCalendar
import com.example.domain.report.ColumnAlignment
import com.example.domain.report.DrillDownService
import com.example.domain.report.DrillTarget
import com.example.domain.report.ReportCategory
import com.example.domain.report.ReportCell
import com.example.domain.report.ReportColumn
import com.example.domain.report.ReportFooter
import com.example.domain.report.ReportRequest
import com.example.domain.report.ReportResult
import com.example.domain.report.ReportRow
import com.example.util.ReportShareHelper
import com.example.domain.report.ReportTable
import com.example.domain.report.ReportType
import com.example.domain.report.ReportsEngine
import com.example.domain.report.RowType
import com.example.ui.theme.AssetPurple
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberDarkCanvas
import com.example.ui.theme.CyberDarkCardElevated
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.MikroTikCyan
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AppViewModel
import com.example.data.local.entity.PartyEntity
import com.example.data.local.entity.TreasuryAccountEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ReportPeriodFilter(val title: String) {
    THIS_MONTH("هذا الشهر"),
    PREVIOUS_MONTH("الشهر السابق"),
    THIS_YEAR("هذه السنة"),
    CUSTOM("مخصص")
}

@Composable
fun ReportsCenterScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reportsEngine = remember { ReportsEngine(viewModel.db) }
    val drillDownService = remember { DrillDownService(viewModel.db) }

    val allPartners by viewModel.allPartners.collectAsState()
    val allTreasuries by viewModel.allTreasuries.collectAsState()

    var selectedCategory by remember { mutableStateOf<ReportCategory?>(null) }
    var selectedReportType by remember { mutableStateOf<ReportType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var periodFilter by remember { mutableStateOf(ReportPeriodFilter.THIS_MONTH) }
    var selectedPartnerId by remember { mutableStateOf<String?>(null) }
    var selectedTreasuryId by remember { mutableStateOf<String?>(null) }

    // Date calculations using AppClock & FiscalCalendar
    val todayEpoch = remember { AppClock.todayEpochDay() }
    val todayDate = remember { LocalDate.ofEpochDay(todayEpoch) }

    var fromEpochDay by remember {
        mutableStateOf(LocalDate.of(todayDate.year, todayDate.monthValue, 1).toEpochDay())
    }
    var toEpochDay by remember {
        mutableStateOf(LocalDate.of(todayDate.year, todayDate.monthValue, todayDate.lengthOfMonth()).toEpochDay())
    }

    // Active report state
    var isLoadingReport by remember { mutableStateOf(false) }
    var currentReportResult by remember { mutableStateOf<ReportResult?>(null) }
    var reportErrorMessage by remember { mutableStateOf<String?>(null) }

    // Drill down dialog state
    var activeDrillTarget by remember { mutableStateOf<DrillTarget?>(null) }
    var activeDrillExpectedMinor by remember { mutableStateOf(0L) }
    var activeDrillCurrency by remember { mutableStateOf(CurrencyCode.FUNCTIONAL) }

    // Recalculate date range when period filter changes
    LaunchedEffect(periodFilter) {
        when (periodFilter) {
            ReportPeriodFilter.THIS_MONTH -> {
                fromEpochDay = LocalDate.of(todayDate.year, todayDate.monthValue, 1).toEpochDay()
                toEpochDay = LocalDate.of(todayDate.year, todayDate.monthValue, todayDate.lengthOfMonth()).toEpochDay()
            }
            ReportPeriodFilter.PREVIOUS_MONTH -> {
                val prevMonthDate = todayDate.minusMonths(1)
                fromEpochDay = LocalDate.of(prevMonthDate.year, prevMonthDate.monthValue, 1).toEpochDay()
                toEpochDay = LocalDate.of(prevMonthDate.year, prevMonthDate.monthValue, prevMonthDate.lengthOfMonth()).toEpochDay()
            }
            ReportPeriodFilter.THIS_YEAR -> {
                val fiscalYear = FiscalCalendar.fiscalYearOf(todayEpoch)
                fromEpochDay = FiscalCalendar.yearStart(fiscalYear)
                toEpochDay = FiscalCalendar.yearEnd(fiscalYear)
            }
            ReportPeriodFilter.CUSTOM -> {
                // Keep current from/to
            }
        }
    }

    // Function to run report
    val loadReport: (ReportType) -> Unit = { type ->
        coroutineScope.launch {
            isLoadingReport = true
            reportErrorMessage = null
            try {
                val params = mutableMapOf<String, String>()
                if (type == ReportType.PARTNER_STATEMENT && selectedPartnerId != null) {
                    params["partyId"] = selectedPartnerId!!
                }
                if (type == ReportType.DAILY_CASH_REGISTER && selectedTreasuryId != null) {
                    params["treasuryId"] = selectedTreasuryId!!
                }
                val request = ReportRequest(
                    type = type,
                    fromEpochDay = fromEpochDay,
                    toEpochDay = toEpochDay,
                    params = params
                )
                val result = reportsEngine.generateReport(request)
                currentReportResult = result
            } catch (e: Exception) {
                reportErrorMessage = e.message ?: "حدث خطأ أثناء إعداد التقرير"
            } finally {
                isLoadingReport = false
            }
        }
    }

    // Intercept back press when inside report detail on phones
    BackHandler(enabled = selectedReportType != null) {
        selectedReportType = null
        currentReportResult = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkCanvas)
    ) {
        val isWideScreen = maxWidth >= 760.dp

        if (isWideScreen) {
            // Tablet Two-Pane Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: Catalog & Filters
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .background(CyberDarkSurface)
                        .padding(12.dp)
                ) {
                    ReportsHeaderSection(searchQuery, onSearchChange = { searchQuery = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    PeriodFilterRow(periodFilter, onPeriodSelected = { periodFilter = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoriesFilterChips(selectedCategory, onCategorySelected = { selectedCategory = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CyberBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    ReportsCatalogList(
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        activeType = selectedReportType,
                        onReportSelected = { type ->
                            selectedReportType = type
                            loadReport(type)
                        }
                    )
                }

                HorizontalDivider(
                    color = CyberBorder,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                )

                // Right Pane: Active Report Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    if (selectedReportType == null) {
                        ReportPlaceholderView()
                    } else {
                        ActiveReportView(
                            reportType = selectedReportType!!,
                            result = currentReportResult,
                            isLoading = isLoadingReport,
                            errorMessage = reportErrorMessage,
                            partners = allPartners,
                            treasuries = allTreasuries,
                            selectedPartnerId = selectedPartnerId,
                            onPartnerSelected = { pId ->
                                selectedPartnerId = pId
                                loadReport(selectedReportType!!)
                            },
                            selectedTreasuryId = selectedTreasuryId,
                            onTreasurySelected = { tId ->
                                selectedTreasuryId = tId
                                loadReport(selectedReportType!!)
                            },
                            onRefresh = { loadReport(selectedReportType!!) },
                            onCellClick = { target, amount, curr ->
                                activeDrillTarget = target
                                activeDrillExpectedMinor = amount
                                activeDrillCurrency = curr
                            },
                            onSharePdf = { table, footer ->
                                ReportShareHelper.shareReportPdf(context, table, footer)
                            },
                            onShareCsv = { table ->
                                ReportShareHelper.shareReportCsv(context, table)
                            }
                        )
                    }
                }
            }
        } else {
            // Mobile Single-Pane Master/Detail
            if (selectedReportType == null) {
                // Master Screen: Catalog & Filters
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ReportsHeaderSection(searchQuery, onSearchChange = { searchQuery = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodFilterRow(periodFilter, onPeriodSelected = { periodFilter = it })
                    Spacer(modifier = Modifier.height(10.dp))
                    CategoriesFilterChips(selectedCategory, onCategorySelected = { selectedCategory = it })
                    Spacer(modifier = Modifier.height(12.dp))
                    ReportsCatalogList(
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        activeType = null,
                        onReportSelected = { type ->
                            selectedReportType = type
                            loadReport(type)
                        }
                    )
                }
            } else {
                // Detail Screen: Full Screen Report View
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedReportType = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "رجوع لمركز التقارير", tint = MikroTikCyan)
                        }
                        Text(
                            text = selectedReportType!!.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ActiveReportView(
                        reportType = selectedReportType!!,
                        result = currentReportResult,
                        isLoading = isLoadingReport,
                        errorMessage = reportErrorMessage,
                        partners = allPartners,
                        treasuries = allTreasuries,
                        selectedPartnerId = selectedPartnerId,
                        onPartnerSelected = { pId ->
                            selectedPartnerId = pId
                            loadReport(selectedReportType!!)
                        },
                        selectedTreasuryId = selectedTreasuryId,
                        onTreasurySelected = { tId ->
                            selectedTreasuryId = tId
                            loadReport(selectedReportType!!)
                        },
                        onRefresh = { loadReport(selectedReportType!!) },
                        onCellClick = { target, amount, curr ->
                            activeDrillTarget = target
                            activeDrillExpectedMinor = amount
                            activeDrillCurrency = curr
                        },
                        onSharePdf = { table, footer ->
                            ReportShareHelper.shareReportPdf(context, table, footer)
                        },
                        onShareCsv = { table ->
                            ReportShareHelper.shareReportCsv(context, table)
                        }
                    )
                }
            }
        }
    }

    // Drill down dialog overlay
    if (activeDrillTarget != null) {
        DrillDownDialog(
            target = activeDrillTarget!!,
            expectedAmountMinor = activeDrillExpectedMinor,
            currency = activeDrillCurrency,
            drillDownService = drillDownService,
            onDismiss = { activeDrillTarget = null }
        )
    }
}

@Composable
private fun ReportsHeaderSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MikroTikCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "مركز التقارير المحاسبية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "القوائم المالية والتحليل والرقابة",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        fontSize = 10.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("بحث في التقارير (أرباح، ميزانية، شركاء...)", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MikroTikCyan,
                unfocusedBorderColor = CyberBorder
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("input_search_reports")
        )
    }
}

@Composable
private fun PeriodFilterRow(
    selectedPeriod: ReportPeriodFilter,
    onPeriodSelected: (ReportPeriodFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ReportPeriodFilter.values().forEach { filter ->
            val isSelected = filter == selectedPeriod
            FilterChip(
                selected = isSelected,
                onClick = { onPeriodSelected(filter) },
                label = { Text(filter.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MikroTikCyan,
                    selectedLabelColor = CyberDarkCanvas,
                    containerColor = CyberDarkCardElevated,
                    labelColor = TextPrimaryDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) MikroTikCyan else CyberBorder,
                    selectedBorderColor = MikroTikCyan,
                    enabled = true,
                    selected = isSelected
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoriesFilterChips(
    selectedCategory: ReportCategory?,
    onCategorySelected: (ReportCategory?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("جميع الفئات", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MikroTikCyan.copy(alpha = 0.2f),
                    selectedLabelColor = MikroTikCyan,
                    containerColor = CyberDarkCardElevated,
                    labelColor = TextSecondaryDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (selectedCategory == null) MikroTikCyan else CyberBorder,
                    selectedBorderColor = MikroTikCyan,
                    enabled = true,
                    selected = selectedCategory == null
                )
            )
        }
        items(ReportCategory.values()) { cat ->
            val isSelected = cat == selectedCategory
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(if (isSelected) null else cat) },
                label = { Text(cat.title, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MikroTikCyan.copy(alpha = 0.2f),
                    selectedLabelColor = MikroTikCyan,
                    containerColor = CyberDarkCardElevated,
                    labelColor = TextSecondaryDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) MikroTikCyan else CyberBorder,
                    selectedBorderColor = MikroTikCyan,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

@Composable
private fun ReportsCatalogList(
    selectedCategory: ReportCategory?,
    searchQuery: String,
    activeType: ReportType?,
    onReportSelected: (ReportType) -> Unit
) {
    val filteredReports = remember(selectedCategory, searchQuery) {
        ReportType.values().filter { rep ->
            val matchCategory = selectedCategory == null || rep.category == selectedCategory
            val matchQuery = searchQuery.isBlank() || rep.title.contains(searchQuery.trim(), ignoreCase = true) || rep.subtitle.contains(searchQuery.trim(), ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(filteredReports) { rep ->
            val isSelected = rep == activeType
            Surface(
                color = if (isSelected) MikroTikCyan.copy(alpha = 0.12f) else CyberDarkCardElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSelected) MikroTikCyan else CyberBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReportSelected(rep) }
                    .testTag("report_card_${rep.name}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when (rep.category) {
                            ReportCategory.FINANCIAL_STATEMENTS -> StatusOnline.copy(alpha = 0.2f)
                            ReportCategory.PROFITS -> MikroTikCyan.copy(alpha = 0.2f)
                            ReportCategory.PARTNERS -> AssetPurple.copy(alpha = 0.2f)
                            ReportCategory.CASH -> StatusOnline.copy(alpha = 0.2f)
                            ReportCategory.AUDIT -> StatusWarning.copy(alpha = 0.2f)
                            else -> CyberDarkSurface
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (rep.category) {
                                    ReportCategory.FINANCIAL_STATEMENTS -> Icons.Default.Analytics
                                    ReportCategory.PROFITS -> Icons.Default.Analytics
                                    ReportCategory.PARTNERS -> Icons.Default.Group
                                    ReportCategory.CASH -> Icons.Default.Payments
                                    ReportCategory.AUDIT -> Icons.Default.Warning
                                    else -> Icons.Default.Analytics
                                },
                                contentDescription = null,
                                tint = when (rep.category) {
                                    ReportCategory.FINANCIAL_STATEMENTS -> StatusOnline
                                    ReportCategory.PROFITS -> MikroTikCyan
                                    ReportCategory.PARTNERS -> AssetPurple
                                    ReportCategory.CASH -> StatusOnline
                                    ReportCategory.AUDIT -> StatusWarning
                                    else -> TextSecondaryDark
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rep.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) MikroTikCyan else TextPrimaryDark
                        )
                        Text(
                            text = rep.subtitle,
                            fontSize = 11.sp,
                            color = TextSecondaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveReportView(
    reportType: ReportType,
    result: ReportResult?,
    isLoading: Boolean,
    errorMessage: String?,
    partners: List<PartyEntity> = emptyList(),
    treasuries: List<TreasuryAccountEntity> = emptyList(),
    selectedPartnerId: String? = null,
    onPartnerSelected: (String) -> Unit = {},
    selectedTreasuryId: String? = null,
    onTreasurySelected: (String?) -> Unit = {},
    onRefresh: () -> Unit,
    onCellClick: (DrillTarget, Long, CurrencyCode) -> Unit,
    onSharePdf: (ReportTable, ReportFooter?) -> Unit,
    onShareCsv: (ReportTable) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = MikroTikCyan)
                Text("جاري استخراج بيانات التقرير من الأستاذ العام...", fontSize = 12.sp, color = TextSecondaryDark)
            }
        }
        return
    }

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(40.dp))
                Text(text = "تعذر توليد التقرير", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                Text(text = errorMessage, fontSize = 12.sp, color = TextSecondaryDark, textAlign = TextAlign.Center)
                Button(onClick = onRefresh, shape = RoundedCornerShape(8.dp)) {
                    Text("إعادة المحاولة")
                }
            }
        }
        return
    }

    if (result == null || result.table.rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد بيانات متاحة لهذا التقرير في الفترة المحددة", color = TextSecondaryDark)
        }
        return
    }

    val table = result.table
    val footer = result.footer

    Column(modifier = Modifier.fillMaxSize()) {
        // Partner sub-filter chips for PARTNER_STATEMENT
        if (reportType == ReportType.PARTNER_STATEMENT) {
            val activePartners = partners.filter { it.isPartner }
            if (activePartners.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(activePartners) { p ->
                        val isSel = (selectedPartnerId == p.id) || (selectedPartnerId == null && p == activePartners.first())
                        FilterChip(
                            selected = isSel,
                            onClick = { onPartnerSelected(p.id) },
                            label = { Text(p.name, fontSize = 11.sp, color = if (isSel) MikroTikCyan else TextPrimaryDark) }
                        )
                    }
                }
            }
        }

        // Treasury sub-filter chips for DAILY_CASH_REGISTER
        if (reportType == ReportType.DAILY_CASH_REGISTER) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                item {
                    val isAll = selectedTreasuryId == null
                    FilterChip(
                        selected = isAll,
                        onClick = { onTreasurySelected(null) },
                        label = { Text("كافة الخزائن", fontSize = 11.sp, color = if (isAll) MikroTikCyan else TextPrimaryDark) }
                    )
                }
                items(treasuries) { t ->
                    val isSel = selectedTreasuryId == t.id
                    FilterChip(
                        selected = isSel,
                        onClick = { onTreasurySelected(t.id) },
                        label = { Text("${t.name} (${t.currency})", fontSize = 11.sp, color = if (isSel) MikroTikCyan else TextPrimaryDark) }
                    )
                }
            }
        }

        // Actions & Status Header
        Surface(
            color = CyberDarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = table.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimaryDark
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = table.subtitle,
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = if (footer.reconciled) StatusOnline.copy(alpha = 0.15f) else SemanticExpenseRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (footer.reconciled) "✓ متطابق" else "⚠️ تفاوت",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (footer.reconciled) StatusOnline else SemanticExpenseRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { onSharePdf(table, footer) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير ومشاركة PDF", tint = MikroTikCyan)
                    }
                    IconButton(onClick = { onShareCsv(table) }) {
                        Icon(Icons.Default.Download, contentDescription = "تصدير CSV لـ Excel", tint = TextPrimaryDark)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = TextSecondaryDark)
                    }
                }
            }
        }

        // Summary / Reconciliation alert banner
        if (!footer.reconciliationDetails.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = if (footer.reconciled) MikroTikCyan.copy(alpha = 0.1f) else SemanticExpenseRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (footer.reconciled) MikroTikCyan.copy(alpha = 0.35f) else SemanticExpenseRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (footer.reconciled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (footer.reconciled) MikroTikCyan else SemanticExpenseRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = footer.reconciliationDetails,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimaryDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Table Viewer
        Surface(
            color = CyberDarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val horizontalScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(CyberDarkCardElevated)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.columns.forEach { col ->
                        Text(
                            text = col.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MikroTikCyan,
                            textAlign = when (col.alignment) {
                                ColumnAlignment.START -> TextAlign.Start
                                ColumnAlignment.CENTER -> TextAlign.Center
                                ColumnAlignment.END -> TextAlign.End
                            },
                            modifier = Modifier.width(160.dp)
                        )
                    }
                }

                HorizontalDivider(color = CyberBorder)

                // Table Rows
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(table.rows) { row ->
                        val rowBg = when (row.type) {
                            RowType.SECTION -> CyberDarkCanvas
                            RowType.TOTAL -> MikroTikCyan.copy(alpha = 0.15f)
                            RowType.SUBTOTAL -> CyberDarkCardElevated
                            RowType.DATA -> Color.Transparent
                        }

                        Surface(color = rowBg) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                row.cells.forEachIndexed { cIdx, cell ->
                                    val col = table.columns.getOrNull(cIdx)
                                    val isBold = row.type == RowType.TOTAL || row.type == RowType.SECTION || (cell is ReportCell.TextCell && cell.isBold)

                                    val cellText = when (cell) {
                                        is ReportCell.MoneyCell -> Money(cell.amountMinor, cell.currency).format()
                                        is ReportCell.TextCell -> cell.text
                                        is ReportCell.PercentCell -> cell.formatPercentText()
                                        is ReportCell.CountCell -> cell.count.toString()
                                        is ReportCell.DateCell -> LocalDate.ofEpochDay(cell.epochDay).toString()
                                    }

                                    val isDrillable = cell.drillTarget != null
                                    Text(
                                        text = cellText,
                                        fontSize = if (row.type == RowType.SECTION) 12.sp else 11.sp,
                                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isDrillable -> MikroTikCyan
                                            row.type == RowType.TOTAL -> MikroTikCyan
                                            cell is ReportCell.MoneyCell && cell.amountMinor < 0L -> SemanticExpenseRed
                                            cell is ReportCell.TextCell && cell.text.startsWith("+") -> SemanticIncomeGreen
                                            cell is ReportCell.TextCell && cell.text.startsWith("-") -> SemanticExpenseRed
                                            else -> TextPrimaryDark
                                        },
                                        textDecoration = if (isDrillable) TextDecoration.Underline else TextDecoration.None,
                                        textAlign = when (col?.alignment) {
                                            ColumnAlignment.START -> TextAlign.Start
                                            ColumnAlignment.CENTER -> TextAlign.Center
                                            ColumnAlignment.END -> TextAlign.End
                                            else -> TextAlign.Start
                                        },
                                        modifier = Modifier
                                            .width(160.dp)
                                            .clickable(enabled = isDrillable) {
                                                cell.drillTarget?.let { target ->
                                                    val amount = (cell as? ReportCell.MoneyCell)?.amountMinor ?: 0L
                                                    val curr = (cell as? ReportCell.MoneyCell)?.currency ?: CurrencyCode.FUNCTIONAL
                                                    onCellClick(target, amount, curr)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = CyberBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Footer with Hash Fingerprint
        Surface(
            color = CyberDarkCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "بصمة التقرير: ${footer.contentHashSha256.take(16)}...",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMutedDark
                    )
                }

                Text(
                    text = "وقت الإصدار: ${footer.issuedAtText.take(19)}",
                    fontSize = 10.sp,
                    color = TextMutedDark
                )
            }
        }
    }
}

@Composable
private fun ReportPlaceholderView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MikroTikCyan.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = MikroTikCyan, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                text = "اختر تقريراً من القائمة لعرضه وتحليله",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimaryDark
            )
            Text(
                text = "يمكنك تصدير أي تقرير مباشرة كـ PDF أو CSV، أو النقر على أي رقم للغوص في قيود الأستاذ",
                fontSize = 11.sp,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )
        }
    }
}
