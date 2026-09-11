package com.example.aichat.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aichat.background.BackgroundScreenshotManager

private object Routes {
    const val CONTACTS = "contacts"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

internal const val STREAM_SCROLL_DEBOUNCE_MS = 120L

internal fun hasPostNotificationsPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

internal fun canRequestPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private fun shareConversation(context: android.content.Context, title: String, content: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "分享聊天"))
    }.onFailure {
        Toast.makeText(context, "系统没有可用的分享应用", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun AiChatApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (state.config.backgroundCaptureEnabled && result.resultCode == Activity.RESULT_OK && data != null) {
            BackgroundScreenshotManager.start(context, result.resultCode, data)
        }
    }

    // Re-assert the user-selected service state whenever the activity is recreated or returns
    // from a system permission screen. The switch itself remains the source of truth; there is
    // deliberately no matching automatic stop here.
    LaunchedEffect(state.config.backgroundCaptureEnabled) {
        if (state.config.backgroundCaptureEnabled) {
            runCatching { BackgroundScreenshotManager.start(context) }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONTACTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    conversations = state.conversations,
                    previews = state.conversationPreviews,
                    selectedConversationId = state.selectedConversationId,
                    isAnyWorking = state.isAnyWorking,
                    collapsedGroups = state.collapsedGroups,
                    onOpenChat = { id ->
                        if (viewModel.selectConversation(id)) navController.navigate(Routes.CHAT)
                    },
                    onFastCreateConversation = {
                        viewModel.createConversation("新聊天") {
                            navController.navigate(Routes.CHAT)
                        }
                    },
                    onCreateConversation = { title, group, onCreated ->
                        viewModel.createConversation(title, group) {
                            onCreated()
                            navController.navigate(Routes.CHAT)
                        }
                    },
                    onRenameConversation = viewModel::renameConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    onDeleteConversations = viewModel::deleteConversations,
                    onExportConversation = { id ->
                        viewModel.exportConversation(id) { title, content ->
                            shareConversation(context, title, content)
                        }
                    },
                    onExportConversations = { ids ->
                        viewModel.exportConversations(ids) { title, content ->
                            shareConversation(context, title, content)
                        }
                    },
                    onSetConversationGroup = viewModel::setConversationGroup,
                    onSetConversationsGroup = viewModel::setConversationsGroup,
                    onRenameGroup = viewModel::renameGroup,
                    onToggleGroupCollapsed = viewModel::toggleGroupCollapsed,
                    onTogglePinConversation = viewModel::togglePinConversation,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onImportImage = viewModel::importImage,
                    onRemoveImage = viewModel::removeSelectedImage,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                    onRetry = viewModel::retry,
                    onRegenerate = viewModel::regenerate,
                    onDeleteMessage = viewModel::deleteMessage,
                    onClear = viewModel::clearConversation,
                    onDraftRestored = viewModel::clearDraftRestore,
                    onSelectModelPreset = viewModel::switchModelPreset,
                    onExport = {
                        state.selectedConversationId?.let { id ->
                            viewModel.exportConversation(id) { title, content ->
                                shareConversation(context, title, content)
                            }
                        }
                    },
                    onExportMarkdown = { viewModel.exportMarkdown(context) },
                    onExportImage = { includeThinking -> viewModel.exportImage(context, includeThinking) },
                    onToggleWebSearch = viewModel::toggleWebSearch,
                    onSwitchBranch = viewModel::switchMessageBranch,
                    onRenameConversation = viewModel::renameConversation,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = {
                            baseUrl,
                            model,
                            apiKey,
                            visionEnabled,
                            updateUrl,
                            backgroundEnabled,
                            screenshotPrompt,
                            overlayBackgroundColor,
                            overlayGlassEnabled,
                            shortAnswerModeEnabled,
                            autoFallbackEnabled,
                            screenshotTrigger,
                            autoCollapseThinking,
                        ->
                        viewModel.saveConfig(
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey,
                            visionEnabled = visionEnabled,
                            updateManifestUrl = updateUrl,
                            backgroundCaptureEnabled = backgroundEnabled,
                            screenshotPrompt = screenshotPrompt,
                            overlayBackgroundColor = overlayBackgroundColor,
                            overlayGlassEnabled = overlayGlassEnabled,
                            shortAnswerModeEnabled = shortAnswerModeEnabled,
                            autoFallbackEnabled = autoFallbackEnabled,
                            screenshotTrigger = screenshotTrigger,
                            autoCollapseThinking = autoCollapseThinking,
                        ).also { result ->
                            if (result.isSuccess) {
                                if (backgroundEnabled) {
                                    runCatching { BackgroundScreenshotManager.start(context) }
                                } else {
                                    BackgroundScreenshotManager.stop(context)
                                }
                            }
                        }
                    },
                    onBackgroundCaptureChanged = { enabled ->
                        viewModel.setBackgroundCaptureEnabled(enabled).also { result ->
                            if (result.isSuccess) {
                                if (enabled) {
                                    // Start the worker immediately after the preference is
                                    // persisted. The projection grant can then be supplied from
                                    // the permission button without a startup race.
                                    runCatching { BackgroundScreenshotManager.start(context) }
                                } else {
                                    BackgroundScreenshotManager.stop(context)
                                }
                            }
                        }
                    },
                    onOverlayAppearanceChanged = viewModel::setOverlayAppearance,
                    onShortAnswerModeChanged = viewModel::setShortAnswerModeEnabled,
                    onAutoFallbackEnabledChanged = viewModel::setAutoFallbackEnabled,
                    onAutoCollapseThinkingChanged = viewModel::setAutoCollapseThinking,
                    onModelPresetSelected = viewModel::selectModelPreset,
                    onScreenshotTriggerChanged = viewModel::setScreenshotTrigger,
                    onThemeColorChanged = viewModel::setThemeColor,
                    onDefaultWebSearchEnabledChanged = viewModel::setDefaultWebSearchEnabled,
                    onDeleteKey = viewModel::deleteApiKey,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onPrepareUpdate = viewModel::prepareUpdateInstall,
                    onDismissUpdate = viewModel::dismissUpdate,
                    onRequestProjection = {
                        projectionLauncher.launch(BackgroundScreenshotManager.projectionPermissionIntent(context))
                    },
                    onCaptureNow = {
                        val accepted = runCatching { BackgroundScreenshotManager.captureNow(context) }
                            .getOrDefault(false)
                        if (!accepted) {
                            Toast.makeText(
                                context,
                                "请确认音量监听已开启，且当前没有正在处理的截图",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onOpenOverlaySettings = { BackgroundScreenshotManager.openOverlaySettings(context) },
                    onOpenAccessibilitySettings = { BackgroundScreenshotManager.openAccessibilitySettings(context) },
                    onTestConnection = viewModel::testModelConnection,
                    onResetProbeState = viewModel::resetProbeState,
                    onRefreshStorageStats = viewModel::loadStorageStats,
                    onCleanupOrphanImages = viewModel::cleanupOrphanImages,
                    onExportBackup = { uri -> viewModel.exportBackup(context, uri) },
                    onImportBackup = { uri -> viewModel.importBackup(context, uri) },
                    onResetBackupRestoreState = viewModel::resetBackupRestoreState,
                )
            }
        }
    }
}
