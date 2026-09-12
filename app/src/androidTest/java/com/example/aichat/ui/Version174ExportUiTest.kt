package com.example.aichat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class Version174ExportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var requestCode = 0
    private var pickerIntent: Intent? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            this@Version174ExportUiTest.requestCode = requestCode
            pickerIntent = contract.createIntent(context, input)
        }
    }
    private val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }

    private fun destination(name: String): Pair<File, Uri> {
        val file = File(context.cacheDir, "exports/174-$name").apply { parentFile!!.mkdirs(); writeText("old trailing content that must be replaced") }
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    @Test fun savesWholeCollapsedCodeSnapshotThroughCreateDocumentAndCancelWritesNothing() {
        val code = (1..40).joinToString("\n", postfix = "\n") { "print(\"line $it\")" }
        var markdown by mutableStateOf("```python\n${code}```")
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MarkdownText(markdown) } }
        } }
        compose.onNodeWithText("另存为代码文件").performScrollTo().performClick()
        File(context.getExternalFilesDir(null), "174-code.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, pickerIntent!!.action)
        assertEquals("code.py", pickerIntent!!.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("text/plain", pickerIntent!!.type)
        val (file, uri) = destination("code.py")
        try {
            compose.runOnIdle {
                markdown = "```python\nprint(\"changed while choosing\")\n```"
                registry.dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
            }
            compose.waitUntil(10_000) { file.readText() == code }
            compose.onNodeWithText("另存为代码文件").performScrollTo().performClick()
            compose.runOnIdle { registry.dispatchResult(requestCode, Activity.RESULT_CANCELED, null) }
            assertEquals(code, file.readText())
        } finally { file.delete() }
    }

    @Test fun tableCopiesTabularTextAndExportsQuotedUtf8Csv() {
        val markdown = "| 名称 | 说明 |\n| --- | --- |\n| **中文** | a,b |\n| 数值 | -12.5 |"
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            MaterialTheme { MarkdownText(markdown) }
        } }
        compose.onNodeWithText("复制表格").performClick()
        compose.onNodeWithText("已复制表格").assertExists()
        File(context.getExternalFilesDir(null), "174-table.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        compose.runOnIdle { assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().contains("中文\ta,b")) }
        compose.onNodeWithText("导出 CSV").performClick()
        assertEquals("text/csv", pickerIntent!!.type)
        assertEquals("table.csv", pickerIntent!!.getStringExtra(Intent.EXTRA_TITLE))
        val (file, uri) = destination("table.csv")
        try {
            compose.runOnIdle { registry.dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
            compose.waitUntil(10_000) { file.readText() == "\uFEFF名称,说明\r\n中文,\"a,b\"\r\n数值,-12.5\r\n" }
        } finally { file.delete() }
    }
}
