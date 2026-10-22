package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.BackupManifest
import com.example.data.sync.CompletenessReport
import com.example.data.sync.SyncState
import com.example.ui.theme.BrandCyanPrimary
import com.example.ui.theme.SemanticExpenseRed
import com.example.ui.theme.SemanticIncomeGreen
import com.example.ui.theme.SemanticWarningAmber
import com.example.ui.viewmodel.AppViewModel
import com.example.util.findActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncSheet(
    viewModel: AppViewModel,
    sheetState: SheetState,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncMeta by viewModel.syncMetadata.collectAsState()
    val backupsList by viewModel.cloudBackupsList.collectAsState()
    val conflictWarning by viewModel.conflictWarning.collectAsState()
    val completenessReport by viewModel.completenessReport.collectAsState()

    var showDirectLoginDialog by remember { mutableStateOf(false) }
    var directEmailInput by remember { mutableStateOf("") }
    var directNameInput by remember { mutableStateOf("") }

    var uploadPassphrase by remember { mutableStateOf("") }
    var showPassphraseInput by remember { mutableStateOf(false) }

    var backupToRestore by remember { mutableStateOf<BackupManifest?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    var backupToDelete by remember { mutableStateOf<BackupManifest?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser?.isSignedIn == true) {
            viewModel.fetchCloudBackupsList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrandCyanPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = BrandCyanPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "النسخ الاحتياطي السحابي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Firebase Firestore • أمان وعزل مشفر لكل مستخدم",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.fetchCloudBackupsList() },
                        modifier = Modifier.testTag("btn_refresh_backups_list")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تحديث قائمة النسخ",
                            tint = BrandCyanPrimary
                        )
                    }
                }
            }

            // 1. Google Account Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_account_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = currentUser?.displayName?.ifBlank { "مستخدم Google" } ?: "غير متصل بحساب",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = currentUser?.email ?: "لم يتم تسجيل الدخول",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BrandCyanPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            val isOnline = currentUser != null && currentUser?.isSignedIn == true
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOnline) SemanticIncomeGreen.copy(alpha = 0.15f) else SemanticExpenseRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isOnline) "متصل ومفعل" else "غير متصل",
                                    color = if (isOnline) SemanticIncomeGreen else SemanticExpenseRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (currentUser == null || currentUser?.isSignedIn == false) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.signInWithGoogleOneTap(context) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("btn_google_one_tap_sign_in"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تسجيل بحساب Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showDirectLoginDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_direct_email_login"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("تسجيل مباشر", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المعرف: ${currentUser?.uid?.take(10)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { viewModel.signOutGoogle() },
                                    modifier = Modifier.testTag("btn_google_sign_out")
                                ) {
                                    Text("تسجيل الخروج", color = SemanticExpenseRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Conflict Warning Alert Banner
            if (conflictWarning != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SemanticWarningAmber.copy(alpha = 0.15f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SemanticWarningAmber)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticWarningAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تنبيه تعارض السحابة",
                                    fontWeight = FontWeight.Bold,
                                    color = SemanticWarningAmber,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = conflictWarning ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.dismissConflictWarning() }) {
                                    Text("إلغاء")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.uploadChunkedBackup(
                                            passphrase = uploadPassphrase.ifBlank { null },
                                            forceOverwrite = true
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SemanticWarningAmber)
                                ) {
                                    Text("متابعة الرفع وحفظ النسخة", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Progress Banner
            item {
                when (val state = syncState) {
                    is SyncState.InProgress -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = BrandCyanPrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyanPrimary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = BrandCyanPrimary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        color = BrandCyanPrimary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    is SyncState.Success -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SemanticIncomeGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SemanticIncomeGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemanticIncomeGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    color = SemanticIncomeGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is SyncState.Error -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = SemanticExpenseRed.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SemanticExpenseRed.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = SemanticExpenseRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.message,
                                    color = SemanticExpenseRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    SyncState.Idle -> { /* Idle */ }
                }
            }

            // Upload New Backup Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "إنشاء نسخة احتياطية سحابية جديدة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "يتم ضغط قاعدة البيانات الكاملة وتقسيمها وتشفيرها لضمان الامتثال لحدود Firestore وسلامة القيود 100%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showPassphraseInput = !showPassphraseInput }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = BrandCyanPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showPassphraseInput) "إلغاء كلمة مرور التشفير" else "تشفير النسخة بكلمة مرور (اختياري)",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showPassphraseInput) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uploadPassphrase,
                                onValueChange = { uploadPassphrase = it },
                                label = { Text("عبارة مرور التشفير (AES-256-GCM)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.uploadChunkedBackup(
                                    passphrase = uploadPassphrase.ifBlank { null },
                                    forceOverwrite = false
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_upload_chunked_backup"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = currentUser != null && currentUser?.isSignedIn == true
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("رفع نسخة احتياطية سحابية الآن", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5 Backups List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "قائمة آخر 5 نسخ سحابية محفوظة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${backupsList.size} من 5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (backupsList.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (currentUser?.isSignedIn == true)
                                        "لا توجد نسخ سحابية محفوظة بعد. اضغط 'رفع نسخة احتياطية سحابية الآن'."
                                    else
                                        "يرجى تسجيل الدخول بحساب Google لعرض وإدارة نسخك السحابية.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(backupsList) { backup ->
                    BackupManifestCard(
                        manifest = backup,
                        onInspectCompleteness = { viewModel.inspectBackupCompleteness(backup) },
                        onRestore = {
                            backupToRestore = backup
                            restorePassphrase = ""
                            showRestoreConfirmDialog = true
                        },
                        onDelete = {
                            backupToDelete = backup
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }
    }

    // Direct Login Dialog
    if (showDirectLoginDialog) {
        AlertDialog(
            onDismissRequest = { showDirectLoginDialog = false },
            title = { Text("تسجيل الدخول المباشر بالبريد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = directEmailInput,
                        onValueChange = { directEmailInput = it },
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = directNameInput,
                        onValueChange = { directNameInput = it },
                        label = { Text("الاسم المعروض (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (directEmailInput.isNotBlank()) {
                            viewModel.signInDirectWithEmail(directEmailInput.trim(), directNameInput.trim())
                            showDirectLoginDialog = false
                        }
                    }
                ) {
                    Text("دخول")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectLoginDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Restore Confirm Dialog
    if (showRestoreConfirmDialog && backupToRestore != null) {
        val b = backupToRestore!!
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandCyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استعادة النسخة السحابية", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تاريخ النسخة: ${sdf.format(Date(b.createdAt))}\nالجهاز: ${b.deviceId}\nعدد السجلات: ${b.documentsCount} وثيقة، ${b.journalLinesCount} سطر قيد",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = SemanticWarningAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ملاحظة أمان: سيتم أخذ نسخة أمان محلية تلقائياً قبل بدء الاستعادة لضمان إمكانية التراجع الفوري في حال حدوث أي خطأ.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (b.isEncrypted) {
                        OutlinedTextField(
                            value = restorePassphrase,
                            onValueChange = { restorePassphrase = it },
                            label = { Text("عبارة مرور فك التشفير") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreChunkedBackup(b.backupId, restorePassphrase.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary)
                ) {
                    Text("تأكيد الاستعادة الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Confirm Dialog
    if (showDeleteConfirmDialog && backupToDelete != null) {
        val b = backupToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("حذف النسخة السحابية", fontWeight = FontWeight.Bold, color = SemanticExpenseRed) },
            text = {
                Text("هل أنت متأكد من حذف هذه النسخة الاحتياطية (${b.backupId}) وجميع أجزائها من السحابة نهائياً؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteChunkedBackup(b.backupId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticExpenseRed)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Completeness Report Dialog
    if (completenessReport != null) {
        val rep = completenessReport!!
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { viewModel.clearCompletenessReport() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = BrandCyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقرير اكتمال ومطابقة النسخة", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "تاريخ النسخة: ${sdf.format(Date(rep.backupCreatedAt))} • الجهاز: ${rep.deviceId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الجدول المحاسبي", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                            Text("السحابة", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                            Text("محلياً", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                            Text("الحالة", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    items(rep.items) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.tableName, fontSize = 11.sp, modifier = Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.backupCount.toString(), fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            Text(item.localCount.toString(), fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                            val diff = item.backupCount - item.localCount
                            Text(
                                text = if (item.isMatch) "مطابق" else (if (diff > 0) "+$diff" else "$diff"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isMatch) SemanticIncomeGreen else SemanticWarningAmber,
                                modifier = Modifier.weight(0.8f),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي السجلات", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("سحابي: ${rep.totalBackupRecords} | محلي: ${rep.totalLocalRecords}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandCyanPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearCompletenessReport() }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
private fun BackupManifestCard(
    manifest: BackupManifest,
    onInspectCompleteness: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandCyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = BrandCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sdf.format(Date(manifest.createdAt)),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "الجهاز: ${manifest.deviceId} • ${manifest.chunkCount} أجزاء",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (manifest.isEncrypted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("مشفر", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ManifestMetricItem("الوثائق", manifest.documentsCount)
                ManifestMetricItem("أسطر القيود", manifest.journalLinesCount)
                ManifestMetricItem("الأطراف", manifest.partiesCount)
                ManifestMetricItem("الباقات", manifest.packagesCount)
                ManifestMetricItem("الخزائن", manifest.treasuriesCount)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onInspectCompleteness) {
                    Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تقرير الاكتمال", fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف النسخة", tint = SemanticExpenseRed, modifier = Modifier.size(20.dp))
                    }
                    Button(
                        onClick = onRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyanPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استعادة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManifestMetricItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            color = BrandCyanPrimary
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
