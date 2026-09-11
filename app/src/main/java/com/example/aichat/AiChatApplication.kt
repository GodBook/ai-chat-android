package com.example.aichat

import android.app.Application
import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ConfigStore
import com.example.aichat.data.local.ImageFileStore
import com.example.aichat.data.network.OpenAiCompatibleClient
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.repository.DefaultChatRepository
import com.example.aichat.data.update.AppUpdateManager
import com.example.aichat.data.update.UpdateConfigStore

class AiChatApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 更新安装完成后重启到这里：后台删掉已经装入系统的安装包缓存。
        Thread { runCatching { container.updateManager.deleteInstalledUpdateApks() } }
            .apply { isDaemon = true }
            .start()
    }
}

class AppContainer(application: Application) {
    private val database = ChatDatabase.getInstance(application)
    val configStore = ConfigStore(application)
    val apiKeyStore = ApiKeyStore(application)
    val imageFileStore = ImageFileStore(application)
    val client = OpenAiCompatibleClient(imageFileStore)
    val webSearchSettingsStore = com.example.aichat.data.local.WebSearchSettingsStore(application)
    val webSearchClient = com.example.aichat.data.network.WebSearchClient(resolveConfig = webSearchSettingsStore::resolve)
    val updateConfigStore = UpdateConfigStore(application)
    val updateManager = AppUpdateManager(
        context = application,
        configStore = updateConfigStore,
    )
    val chatRepository: ChatRepository = DefaultChatRepository(
        database = database,
        configStore = configStore,
        apiKeyStore = apiKeyStore,
        imageFileStore = imageFileStore,
        client = client,
        webSearchClient = webSearchClient,
    )
}
