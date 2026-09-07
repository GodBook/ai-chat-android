package com.example.aichat.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.aichat.data.model.MAX_SCREENSHOT_PROMPT_LENGTH
import com.example.aichat.data.model.OVERLAY_COLOR_PRESETS
import com.example.aichat.data.update.InstallPreparation
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSave: suspend (String, String, String, Boolean, String, Boolean, String, String, Boolean, Boolean) -> Result<Unit>,
    onBackgroundCaptureChanged: suspend (Boolean) -> Result<Unit>,
    onOverlayAppearanceChanged: suspend (String, Boolean) -> Result<Unit>,
    onShortAnswerModeChanged: suspend (Boolean) -> Result<Unit>,
    onDeleteKey: () -> Unit,
    onCheckUpdate: (String?) -> Unit,
    onDownloadUpdate: (com.example.aichat.data.update.AppUpdateInfo) -> Unit,
    onPrepareUpdate: () -> InstallPreparation?,
    onDismissUpdate: () -> Unit,
    onRequestProjection: () -> Unit,
    onCaptureNow: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var baseUrl by rememberSaveable(state.config.baseUrl) { mutableStateOf(state.config.baseUrl) }
    var model by rememberSaveable(state.config.model) { mutableStateOf(state.config.model) }
    var apiKey by rememberSaveable { mutableStateOf("") }
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
    var updateManifestUrl by rememberSaveable(state.updateManifestUrl) { mutableStateOf(state.updateManifestUrl) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var updatingBackgroundCapture by remember { mutableStateOf(false) }
    var updatingOverlayAppearance by remember { mutableStateOf(false) }
    var updatingShortAnswerMode by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("连接设置", fontWeight = FontWeight.SemiBold)
                        Text("AI BOTOY · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "模型连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") },
                supportingText = { Text("例如：https://api.openai.com/v1") },
                singleLine = true,
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                singleLine = true,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Text(
                "聊天能力",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支持图片", fontWeight = FontWeight.Medium)
                    Text("当前模型需要支持视觉输入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = visionEnabled, onCheckedChange = { visionEnabled = it; saved = false })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Text(
                "后台助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("音量下键后台截图问答", fontWeight = FontWeight.Medium)
                    Text(
                        "开启后，应用在后台运行时按下音量下键会截取屏幕并发送给 AI；需要同时开启支持图片",
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
            if (backgroundCaptureEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        color = if (backgroundPermissionsReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("API Key 仅加密保存在本机，并会直接发送给你填写的模型服务。请不要在不可信设备上分享应用或调试日志。")
                }
            }
            Text("在线更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = updateManifestUrl,
                onValueChange = { updateManifestUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("更新清单地址") },
                supportingText = { Text("HTTPS JSON，例如 https://你的域名/latest.json") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onCheckUpdate(updateManifestUrl) },
                    enabled = state.updateState !is UpdateUiState.Checking && state.updateState !is UpdateUiState.Downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("检查更新")
                }
                when (val update = state.updateState) {
                    is UpdateUiState.Checking -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    is UpdateUiState.Downloading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else -> Unit
                }
            }
            when (val update = state.updateState) {
                is UpdateUiState.UpToDate -> Text("当前已是最新版本（${update.latestVersionName}）", color = MaterialTheme.colorScheme.primary)
                is UpdateUiState.Error -> Text(update.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) {
                AssistChip(onClick = {}, label = { Text("已保存") }, leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
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
                                )
                                    .onSuccess { error = null; saved = true; apiKey = "" }
                                    .onFailure { error = it.message ?: "保存失败"; saved = false }
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && !updatingOverlayAppearance,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("保存设置")
                }
                if (state.hasApiKey) {
                    OutlinedButton(onClick = { showDeleteKeyConfirmation = true }, enabled = !saving) { Text("删除密钥") }
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
