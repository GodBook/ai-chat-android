package com.example.aichat.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.aichat.BuildConfig
import com.example.aichat.background.BackgroundScreenshotManager
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.FALLBACK_MODEL
import com.example.aichat.data.model.MODEL_PRESETS
import com.example.aichat.data.model.ModelPreset
import com.example.aichat.data.model.canReplaceEndpointForPreset
import com.example.aichat.data.model.MAX_SCREENSHOT_PROMPT_LENGTH
import com.example.aichat.data.model.OVERLAY_COLOR_PRESETS
import com.example.aichat.data.model.ScreenshotTrigger
import com.example.aichat.data.update.InstallPreparation
import kotlinx.coroutines.launch

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One-tap model choices. The text field above still accepts any model id. */
@Composable
private fun ModelPresetChips(
    currentModel: String,
    onPicked: (ModelPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "快捷选择",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MODEL_PRESETS.forEach { preset ->
                FilterChip(
                    selected = currentModel.trim() == preset.model,
                    onClick = { onPicked(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSettings(
    selectedThemeKey: String,
    onThemeSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppThemeColor.entries.forEach { preset ->
            val selected = selectedThemeKey == preset.key
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onThemeSelected(preset.key) },
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(preset.lightPrimary)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun OverlayAppearanceSettings(
    backgroundColor: String,
    glassEnabled: Boolean,
    enabled: Boolean,
    onBackgroundColorChanged: (String) -> Unit,
    onGlassChanged: (Boolean) -> Unit,
) {
    val previewColor = Color(android.graphics.Color.parseColor(backgroundColor))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "悬浮回答外观",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OVERLAY_COLOR_PRESETS.forEach { preset ->
                val selected = backgroundColor == preset.colorHex
                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onBackgroundColorChanged(preset.colorHex) },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(preset.colorHex)))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF17336F),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(preset.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = previewColor.copy(alpha = if (glassEnabled) 0.72f else 0.97f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF315BCE),
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 截屏回答", fontWeight = FontWeight.SemiBold, color = Color(0xFF1C2B52))
                    Text("回答外观预览", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5874))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("半透明毛玻璃", fontWeight = FontWeight.Medium)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "半透明背景与系统模糊"
                    } else {
                        "当前系统使用半透明效果"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = glassEnabled,
                enabled = enabled,
                onCheckedChange = onGlassChanged,
            )
        }
    }
}

@Composable
private fun ScreenshotTriggerSettings(
    trigger: ScreenshotTrigger,
    enabled: Boolean,
    onTriggerChanged: (ScreenshotTrigger) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "截图快捷键",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        ScreenshotTrigger.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = trigger == option,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onTriggerChanged(option) },
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = trigger == option, onClick = null, enabled = enabled)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.label, fontWeight = FontWeight.Medium)
                    Text(
                        option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSave: suspend (
        String,
        String,
        String,
        Boolean,
        String,
        Boolean,
        String,
        String,
        Boolean,
        Boolean,
        Boolean,
        ScreenshotTrigger,
        Boolean,
    ) -> Result<Unit>,
    onBackgroundCaptureChanged: suspend (Boolean) -> Result<Unit>,
    onOverlayAppearanceChanged: suspend (String, Boolean) -> Result<Unit>,
    onShortAnswerModeChanged: suspend (Boolean) -> Result<Unit>,
    onAutoFallbackEnabledChanged: suspend (Boolean) -> Result<Unit>,
    onAutoCollapseThinkingChanged: suspend (Boolean) -> Result<Unit>,
    onModelPresetSelected: suspend (ModelPreset) -> Result<Unit>,
    onScreenshotTriggerChanged: suspend (ScreenshotTrigger) -> Result<Unit>,
    onThemeColorChanged: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    onDefaultWebSearchEnabledChanged: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    onDeleteKey: () -> Unit,
    onCheckUpdate: (String?) -> Unit,
    onDownloadUpdate: (com.example.aichat.data.update.AppUpdateInfo) -> Unit,
    onPrepareUpdate: () -> InstallPreparation?,
    onDismissUpdate: () -> Unit,
    onRequestProjection: () -> Unit,
    onCaptureNow: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onTestConnection: (baseUrl: String, model: String, apiKey: String?) -> Unit = { _, _, _ -> },
    onResetProbeState: () -> Unit = {},
    onRefreshStorageStats: () -> Unit = {},
    onCleanupOrphanImages: () -> Unit = {},
    onExportBackup: (android.net.Uri) -> Unit = {},
    onImportBackup: (android.net.Uri) -> Unit = {},
    onResetBackupRestoreState: () -> Unit = {},
) {
    var baseUrl by rememberSaveable(state.config.baseUrl) { mutableStateOf(state.config.baseUrl) }
    var model by rememberSaveable(state.config.model) { mutableStateOf(state.config.model) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var themeColor by rememberSaveable(state.config.themeColor) { mutableStateOf(state.config.themeColor) }
    var visionEnabled by rememberSaveable(state.config.visionEnabled) { mutableStateOf(state.config.visionEnabled) }
    var backgroundCaptureEnabled by rememberSaveable(state.config.backgroundCaptureEnabled) {
        mutableStateOf(state.config.backgroundCaptureEnabled)
    }
    var screenshotPrompt by rememberSaveable(state.config.screenshotPrompt) {
        mutableStateOf(state.config.screenshotPrompt)
    }
    var overlayBackgroundColor by rememberSaveable(state.config.overlayBackgroundColor) {
        mutableStateOf(state.config.overlayBackgroundColor)
    }
    var overlayGlassEnabled by rememberSaveable(state.config.overlayGlassEnabled) {
        mutableStateOf(state.config.overlayGlassEnabled)
    }
    var shortAnswerModeEnabled by rememberSaveable(state.config.shortAnswerModeEnabled) {
        mutableStateOf(state.config.shortAnswerModeEnabled)
    }
    var autoFallbackEnabled by rememberSaveable(state.config.autoFallbackEnabled) {
        mutableStateOf(state.config.autoFallbackEnabled)
    }
    var autoCollapseThinking by rememberSaveable(state.config.autoCollapseThinking) {
        mutableStateOf(state.config.autoCollapseThinking)
    }
    var screenshotTrigger by rememberSaveable(state.config.screenshotTrigger) {
        mutableStateOf(state.config.screenshotTrigger)
    }
    var defaultWebSearchEnabled by rememberSaveable(state.config.defaultWebSearchEnabled) {
        mutableStateOf(state.config.defaultWebSearchEnabled)
    }
    var updateManifestUrl by rememberSaveable(state.updateManifestUrl) { mutableStateOf(state.updateManifestUrl) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var updatingBackgroundCapture by remember { mutableStateOf(false) }
    var updatingOverlayAppearance by remember { mutableStateOf(false) }
    var updatingShortAnswerMode by remember { mutableStateOf(false) }
    var updatingAutoFallback by remember { mutableStateOf(false) }
    var updatingScreenshotTrigger by remember { mutableStateOf(false) }
    var updatingDefaultWebSearch by remember { mutableStateOf(false) }
    var showDeleteKeyConfirmation by rememberSaveable { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var pendingBackgroundEnable by remember { mutableStateOf<Boolean?>(null) }
    var pendingBackgroundPrevious by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlayPermissionGranted = remember(permissionRefresh) {
        BackgroundScreenshotManager.canDrawOverlays(context)
    }
    val accessibilityPermissionGranted = remember(permissionRefresh) {
        BackgroundScreenshotManager.isAccessibilityServiceEnabled(context)
    }
    val usesAccessibilityScreenshot = BackgroundScreenshotManager.usesAccessibilityScreenshot
    val screenshotPermissionGranted = if (usesAccessibilityScreenshot) {
        accessibilityPermissionGranted
    } else {
        BackgroundScreenshotManager.hasActiveProjection
    }
    // Android 11+ captures through AccessibilityService and does not run the
    // foreground worker. Notification permission is only a prerequisite for
    // the Android 10 fallback, which uses a foreground service.
    val notificationPermissionRequired = !usesAccessibilityScreenshot
    val notificationPermissionGranted = !notificationPermissionRequired ||
        hasPostNotificationsPermission(context)
    val backgroundPermissionsReady = screenshotPermissionGranted &&
        overlayPermissionGranted && accessibilityPermissionGranted && notificationPermissionGranted

    fun persistBackgroundCaptureChange(requested: Boolean, previous: Boolean) {
        backgroundCaptureEnabled = requested
        saved = false
        updatingBackgroundCapture = true
        scope.launch {
            try {
                onBackgroundCaptureChanged(requested)
                    .onSuccess {
                        error = null
                        saved = true
                    }
                    .onFailure {
                        backgroundCaptureEnabled = previous
                        error = it.message ?: "后台截图设置保存失败"
                    }
            } finally {
                updatingBackgroundCapture = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requested = pendingBackgroundEnable
        val previous = pendingBackgroundPrevious
        pendingBackgroundEnable = null
        if (requested == true && !granted) {
            backgroundCaptureEnabled = previous
            error = "需要允许通知，才能显示 Android 10 后台截图状态和回答"
        } else if (requested != null) {
            persistBackgroundCaptureChange(requested, previous)
        }
    }

    fun requestBackgroundCaptureChange(requested: Boolean) {
        val previous = backgroundCaptureEnabled
        if (requested && notificationPermissionRequired && canRequestPostNotificationsPermission() &&
            !notificationPermissionGranted) {
            pendingBackgroundEnable = requested
            pendingBackgroundPrevious = previous
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            persistBackgroundCaptureChange(requested, previous)
        }
    }

    val launchInstallIntent: (android.content.Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
            onDismissUpdate()
        } catch (_: ActivityNotFoundException) {
            installError = "系统没有可用的安装程序"
        } catch (failure: SecurityException) {
            installError = failure.message ?: "无法打开安装程序"
        }
    }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        when (val preparation = onPrepareUpdate()) {
            is InstallPreparation.Ready -> launchInstallIntent(preparation.intent)
            is InstallPreparation.PermissionRequired -> installError = "尚未允许安装未知应用"
            null -> Unit
        }
    }

    fun launchPreparedInstall() {
        val preparation = onPrepareUpdate()
        if (preparation == null) return
        try {
            when (preparation) {
                is InstallPreparation.Ready -> launchInstallIntent(preparation.intent)
                is InstallPreparation.PermissionRequired -> unknownSourcesLauncher.launch(preparation.settingsIntent)
            }
        } catch (_: ActivityNotFoundException) {
            installError = "系统没有可用的安装程序"
        } catch (failure: SecurityException) {
            installError = failure.message ?: "无法打开安装设置"
        }
    }

    val performSave: suspend () -> Result<Unit> = {
        saving = true
        try {
            onSave(
                baseUrl,
                model,
                apiKey,
                visionEnabled,
                updateManifestUrl,
                backgroundCaptureEnabled,
                screenshotPrompt,
                overlayBackgroundColor,
                overlayGlassEnabled,
                shortAnswerModeEnabled,
                autoFallbackEnabled,
                screenshotTrigger,
                autoCollapseThinking,
            )
                .onSuccess {
                    error = null
                    saved = true
                    apiKey = ""
                }
                .onFailure {
                    error = it.message ?: "保存失败"
                    saved = false
                }
        } finally {
            saving = false
        }
    }

    fun handleExit() {
        if (saving) return
        val hasUnsavedChanges = !saved && (
            baseUrl.trim() != state.config.baseUrl.trim() ||
            model.trim() != state.config.model.trim() ||
            apiKey.isNotBlank() ||
            visionEnabled != state.config.visionEnabled ||
            updateManifestUrl.trim() != state.updateManifestUrl.trim() ||
            screenshotPrompt.trim() != state.config.screenshotPrompt.trim()
        )
        if (hasUnsavedChanges && error == null && model.trim().isNotBlank() && baseUrl.trim().isNotBlank()) {
            scope.launch {
                performSave().onSuccess {
                    onBack()
                }
            }
        } else {
            onBack()
        }
    }

    BackHandler(onBack = { handleExit() })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("设置", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { handleExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 1. 模型与接口卡片
            SettingsCard {
                SettingsCardHeader(
                    icon = Icons.Default.Tune,
                    title = "模型与接口",
                    subtitle = "配置 AI 对话的大模型与网络端点",
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址") },
                    supportingText = { Text("例如：https://api.deepseek.com/v1") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    supportingText = { Text("可以点下方芯片快捷选择，也可以直接手写模型名") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("模型失效自动回退", fontWeight = FontWeight.Medium)
                        Text(
                            "默认模型确认下线或过期时，自动改用 $FALLBACK_MODEL 重试一次；关闭后始终使用上面填写的模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoFallbackEnabled,
                        enabled = !saving && !updatingAutoFallback,
                        onCheckedChange = { requested ->
                            val previous = autoFallbackEnabled
                            autoFallbackEnabled = requested
                            saved = false
                            updatingAutoFallback = true
                            scope.launch {
                                try {
                                    onAutoFallbackEnabledChanged(requested)
                                        .onSuccess {
                                            error = null
                                            saved = true
                                        }
                                        .onFailure {
                                            autoFallbackEnabled = previous
                                            error = it.message ?: "自动回退设置保存失败"
                                        }
                                } finally {
                                    updatingAutoFallback = false
                                }
                            }
                        },
                    )
                }
                ModelPresetChips(
                    currentModel = model,
                    onPicked = { preset ->
                        model = preset.model
                        val presetEndpoint = preset.baseUrl
                        if (presetEndpoint != null && canReplaceEndpointForPreset(baseUrl)) {
                            baseUrl = presetEndpoint
                        }
                        saved = false
                        scope.launch {
                            onModelPresetSelected(preset)
                                .onSuccess {
                                    error = null
                                    saved = true
                                }
                                .onFailure {
                                    error = it.message ?: "选择模型失败"
                                }
                        }
                    },
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text(if (state.hasApiKey) "已保存，输入新值可替换" else "粘贴你的 API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showKey) "隐藏密钥" else "显示密钥")
                        }
                    },
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        )
                        Text(
                            "API Key 仅加密保存在本机，并会直接发送给你填写的模型服务。请不要在不可信设备上分享应用或调试日志。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 测试连接操作与状态反馈
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onTestConnection(baseUrl, model, apiKey)
                        },
                        enabled = !saving && state.probeState !is ProbeUiState.Probing && (apiKey.isNotBlank() || state.hasApiKey),
                    ) {
                        if (state.probeState is ProbeUiState.Probing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在测试...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("测试连接")
                        }
                    }

                    if (state.probeState is ProbeUiState.Success) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                "连接正常 · ${state.probeState.latencyMs}ms",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    } else if (state.probeState is ProbeUiState.Failure) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                state.probeState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 2. 主题颜色卡片
            SettingsCard {
                SettingsCardHeader(
                    icon = Icons.Default.Palette,
                    title = "主题颜色",
                    subtitle = "选择你喜爱的应用主题色调",
                )
                ThemeColorSettings(
                    selectedThemeKey = themeColor,
                    onThemeSelected = { requestedKey ->
                        themeColor = requestedKey
                        scope.launch {
                            onThemeColorChanged(requestedKey)
                        }
                    },
                )
            }

            // 3. 对话偏好卡片
            SettingsCard {
                SettingsCardHeader(
                    icon = Icons.Default.AutoAwesome,
                    title = "对话偏好",
                    subtitle = "多模态视觉、思维链与联网搜索设置",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("支持图片", fontWeight = FontWeight.Medium)
                        Text(
                            "当前模型需要支持视觉输入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = visionEnabled, onCheckedChange = { visionEnabled = it; saved = false })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动折叠思考过程", fontWeight = FontWeight.Medium)
                        Text(
                            "生成完成后自动收起深度思考卡片，点击可随时展开查看思考链",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCollapseThinking,
                        onCheckedChange = { enabled ->
                            autoCollapseThinking = enabled
                            saved = false
                            scope.launch {
                                onAutoCollapseThinkingChanged(enabled)
                                    .onSuccess {
                                        error = null
                                        saved = true
                                    }
                                    .onFailure {
                                        error = it.message ?: "保存设置失败"
                                    }
                            }
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("默认启用联网搜索", fontWeight = FontWeight.Medium)
                        Text(
                            "发送新消息时默认检索实时网页信息（也可在聊天输入框左侧随时切换）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = defaultWebSearchEnabled,
                        enabled = !saving && !updatingDefaultWebSearch,
                        onCheckedChange = { enabled ->
                            val previous = defaultWebSearchEnabled
                            defaultWebSearchEnabled = enabled
                            saved = false
                            updatingDefaultWebSearch = true
                            scope.launch {
                                try {
                                    onDefaultWebSearchEnabledChanged(enabled)
                                        .onSuccess {
                                            error = null
                                            saved = true
                                        }
                                        .onFailure {
                                            defaultWebSearchEnabled = previous
                                            error = it.message ?: "联网搜索设置保存失败"
                                        }
                                } finally {
                                    updatingDefaultWebSearch = false
                                }
                            }
                        },
                    )
                }
            }

            // 3. 后台截屏助手卡片
            SettingsCard {
                SettingsCardHeader(
                    icon = Icons.Default.SmartToy,
                    title = "后台截图助手",
                    subtitle = "息屏或在其他应用中快速识屏与答题",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("后台截图问答", fontWeight = FontWeight.Medium)
                        Text(
                            "开启后，应用在后台运行时按设置好的音量键会截取屏幕并发送给 AI；需要同时开启支持图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = backgroundCaptureEnabled,
                        enabled = !saving && !updatingBackgroundCapture,
                        onCheckedChange = ::requestBackgroundCaptureChange,
                    )
                }
                if (backgroundCaptureEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OverlayAppearanceSettings(
                        backgroundColor = overlayBackgroundColor,
                        glassEnabled = overlayGlassEnabled,
                        enabled = !saving && !updatingOverlayAppearance,
                        onBackgroundColorChanged = { requestedColor ->
                            val previousColor = overlayBackgroundColor
                            overlayBackgroundColor = requestedColor
                            saved = false
                            updatingOverlayAppearance = true
                            scope.launch {
                                try {
                                    onOverlayAppearanceChanged(requestedColor, overlayGlassEnabled)
                                        .onSuccess {
                                            error = null
                                            saved = true
                                        }
                                        .onFailure {
                                            overlayBackgroundColor = previousColor
                                            error = it.message ?: "悬浮回答外观保存失败"
                                        }
                                } finally {
                                    updatingOverlayAppearance = false
                                }
                            }
                        },
                        onGlassChanged = { requestedGlass ->
                            val previousGlass = overlayGlassEnabled
                            overlayGlassEnabled = requestedGlass
                            saved = false
                            updatingOverlayAppearance = true
                            scope.launch {
                                try {
                                    onOverlayAppearanceChanged(overlayBackgroundColor, requestedGlass)
                                        .onSuccess {
                                            error = null
                                            saved = true
                                        }
                                        .onFailure {
                                            overlayGlassEnabled = previousGlass
                                            error = it.message ?: "悬浮回答外观保存失败"
                                        }
                                } finally {
                                    updatingOverlayAppearance = false
                                }
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("选择/判断题简版回答模式", fontWeight = FontWeight.Medium)
                            Text(
                                "识别到选择题显示 A-D 方块（多选题会同时点亮多个），判断题左边为正确、右边为错误；只显示约 1 秒，不弹出文字回答",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = shortAnswerModeEnabled,
                            enabled = !saving && !updatingShortAnswerMode,
                            onCheckedChange = { requested ->
                                val previous = shortAnswerModeEnabled
                                shortAnswerModeEnabled = requested
                                saved = false
                                updatingShortAnswerMode = true
                                scope.launch {
                                    try {
                                        onShortAnswerModeChanged(requested)
                                            .onSuccess {
                                                error = null
                                                saved = true
                                            }
                                            .onFailure {
                                                shortAnswerModeEnabled = previous
                                                error = it.message ?: "简版回答设置保存失败"
                                            }
                                    } finally {
                                        updatingShortAnswerMode = false
                                    }
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ScreenshotTriggerSettings(
                        trigger = screenshotTrigger,
                        enabled = !saving && !updatingScreenshotTrigger,
                        onTriggerChanged = { requested ->
                            if (requested != screenshotTrigger) {
                                val previous = screenshotTrigger
                                screenshotTrigger = requested
                                saved = false
                                updatingScreenshotTrigger = true
                                scope.launch {
                                    try {
                                        onScreenshotTriggerChanged(requested)
                                            .onSuccess {
                                                error = null
                                                saved = true
                                            }
                                            .onFailure {
                                                screenshotTrigger = previous
                                                error = it.message ?: "截图快捷键保存失败"
                                            }
                                    } finally {
                                        updatingScreenshotTrigger = false
                                    }
                                }
                            }
                        },
                    )
                    OutlinedTextField(
                        value = screenshotPrompt,
                        onValueChange = {
                            screenshotPrompt = it.take(MAX_SCREENSHOT_PROMPT_LENGTH)
                            saved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("截图后发送给 AI 的提示词") },
                        supportingText = {
                            Text("${screenshotPrompt.length}/$MAX_SCREENSHOT_PROMPT_LENGTH")
                        },
                        isError = screenshotPrompt.isBlank(),
                        minLines = 3,
                        maxLines = 6,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                screenshotPrompt = DEFAULT_SCREENSHOT_PROMPT
                                saved = false
                            },
                            enabled = screenshotPrompt != DEFAULT_SCREENSHOT_PROMPT,
                        ) {
                            Text("恢复默认提示词")
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (usesAccessibilityScreenshot) {
                                    "开关会立即保存，只会由你手动关闭。请开启悬浮窗和音量监听，并在系统无障碍设置中选择“AI BOTOY”。Android 11 及以上由无障碍服务直接截图，不需要单独授权屏幕录制。"
                                } else {
                                    "开关会立即保存，只会由你手动关闭。Android 10 还需授权屏幕捕获、悬浮窗和音量监听。屏幕捕获授权在应用进程被系统结束后需要重新授予。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (usesAccessibilityScreenshot) {
                                    "系统截图与音量监听：${if (accessibilityPermissionGranted) "已开启" else "未开启"}；悬浮窗：${if (overlayPermissionGranted) "已开启" else "未开启"}"
                                } else {
                                    "屏幕捕获：${if (screenshotPermissionGranted) "已授权" else "未授权"}；悬浮窗：${if (overlayPermissionGranted) "已开启" else "未开启"}；音量监听：${if (accessibilityPermissionGranted) "已开启" else "未开启"}；通知：${if (notificationPermissionGranted) "已允许" else "未允许"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (backgroundPermissionsReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onRequestProjection,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("授权屏幕捕获")
                            }
                            OutlinedButton(
                                onClick = onOpenOverlaySettings,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("开启悬浮窗")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenOverlaySettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开启悬浮窗")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开启音量监听")
                    }
                    OutlinedButton(
                        onClick = onCaptureNow,
                        enabled = backgroundPermissionsReady && !updatingBackgroundCapture,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即测试截图")
                    }
                    if (notificationPermissionRequired && canRequestPostNotificationsPermission() &&
                        !notificationPermissionGranted) {
                        OutlinedButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            enabled = !saving && !updatingBackgroundCapture,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("允许后台通知")
                        }
                    }
                }
            }

            // 4. 数据与存储卡片
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri ->
                if (uri != null) onExportBackup(uri)
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) onImportBackup(uri)
            }

            SettingsCard {
                SettingsCardHeader(
                    icon = Icons.Default.FolderZip,
                    title = "数据与存储",
                    subtitle = "全量数据备份、跨机恢复与存储清理",
                )

                // 存储占用面板
                val stats = state.storageStats
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "存储空间概览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val sizeFormatted = remember(stats?.imageSizeBytes) {
                            val bytes = stats?.imageSizeBytes ?: 0L
                            when {
                                bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes.toDouble() / (1024 * 1024))
                                bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes.toDouble() / 1024)
                                else -> "$bytes B"
                            }
                        }
                        Text(
                            "已存会话：${stats?.conversationCount ?: 0} 个 · 消息记录：${stats?.messageCount ?: 0} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "聊天与截图附件：${stats?.imageCount ?: 0} 张图片 · 占用空间约 $sizeFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 备份状态反馈
                if (state.backupRestoreState is BackupRestoreUiState.Processing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在处理备份数据...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (state.backupRestoreState is BackupRestoreUiState.Success) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                state.backupRestoreState.message,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onResetBackupRestoreState, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else if (state.backupRestoreState is BackupRestoreUiState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                state.backupRestoreState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onResetBackupRestoreState, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // 操作按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportLauncher.launch("ai_botoy_backup_$timeStr.zip")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.backupRestoreState !is BackupRestoreUiState.Processing,
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出备份")
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.backupRestoreState !is BackupRestoreUiState.Processing,
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("恢复备份")
                    }
                }

                OutlinedButton(
                    onClick = onCleanupOrphanImages,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清理无效图片缓存")
                }
            }

            // 5. 关于与更新卡片
            var showAdvancedUpdate by rememberSaveable { mutableStateOf(false) }

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "AI BOTOY",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    "Build ${BuildConfig.VERSION_CODE}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onCheckUpdate(updateManifestUrl) },
                        enabled = state.updateState !is UpdateUiState.Checking && state.updateState !is UpdateUiState.Downloading,
                    ) {
                        if (state.updateState is UpdateUiState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("检查中…")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("检查更新")
                        }
                    }
                }

                when (val update = state.updateState) {
                    is UpdateUiState.UpToDate -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    "当前已是最新版本（${update.latestVersionName}）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在下载更新…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UpdateUiState.Error -> {
                        Text(
                            update.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> Unit
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvancedUpdate = !showAdvancedUpdate }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "高级配置（更新清单地址）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (showAdvancedUpdate) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAdvancedUpdate) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (showAdvancedUpdate) {
                    OutlinedTextField(
                        value = updateManifestUrl,
                        onValueChange = { updateManifestUrl = it; saved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("更新清单地址") },
                        supportingText = { Text("HTTPS JSON，例如 https://你的域名/latest.json") },
                        singleLine = true,
                    )
                }
            }

            // 底部操作区与保存
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (saved) {
                AssistChip(
                    onClick = {},
                    label = { Text("设置已保存") },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch { performSave() }
                    },
                    enabled = !saving && !updatingOverlayAppearance,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("保存设置")
                    }
                }
                if (state.hasApiKey) {
                    OutlinedButton(
                        onClick = { showDeleteKeyConfirmation = true },
                        enabled = !saving,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("删除密钥")
                    }
                }
            }
            Text(
                "聊天记录和设置保存在本机。通过同一签名安装新版 APK 时，Android 会保留原有数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    when (val update = state.updateState) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("发现新版本 ${update.info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("版本号：${update.info.versionCode}")
                    if (update.info.releaseNotes.isNotBlank()) Text(update.info.releaseNotes)
                    Text("下载后将调用系统安装程序，应用数据会保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { onDownloadUpdate(update.info) }) { Text("下载更新") }
            },
            dismissButton = { TextButton(onClick = onDismissUpdate) { Text("稍后") } },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fraction = update.progress?.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(fraction * 100).toInt()}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在下载…")
                    }
                }
            },
            confirmButton = {},
        )
        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("更新已下载") },
            text = { Text("准备交给系统安装程序。若系统要求许可，请开启“允许安装未知应用”；返回后会继续安装。") },
            confirmButton = { TextButton(onClick = { launchPreparedInstall() }) { Text("安装") } },
            dismissButton = { TextButton(onClick = onDismissUpdate) { Text("稍后") } },
        )
        else -> Unit
    }
    installError?.let { message ->
        AlertDialog(
            onDismissRequest = { installError = null },
            title = { Text("无法安装更新") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { installError = null }) { Text("知道了") } },
        )
    }
    if (showDeleteKeyConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteKeyConfirmation = false },
            title = { Text("删除 API Key？") },
            text = { Text("删除后将无法发送新消息，聊天记录不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteKeyConfirmation = false
                        onDeleteKey()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteKeyConfirmation = false }) { Text("取消") } },
        )
    }
}
