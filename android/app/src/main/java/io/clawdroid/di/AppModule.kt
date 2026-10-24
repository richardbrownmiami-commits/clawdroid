package io.clawdroid.di

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.clawdroid.core.data.local.AppDatabase
import io.clawdroid.core.data.local.dao.MessageDao
import io.clawdroid.core.data.local.entity.MessageEntity
import io.clawdroid.core.data.mapper.MessageMapper
import io.clawdroid.core.domain.model.MessageStatus
import io.clawdroid.llama.LlamaModelManager
import io.clawdroid.assistant.AccessibilityScreenshotSource
import io.clawdroid.assistant.DeviceController
import io.clawdroid.core.data.local.ImageFileStorage
import io.clawdroid.feature.chat.voice.ScreenshotSource
import io.clawdroid.core.data.remote.WebSocketClient
import io.clawdroid.assistant.ToolRequestHandler
import io.clawdroid.core.data.repository.ChatRepositoryImpl
import io.clawdroid.core.data.repository.TtsCatalogRepositoryImpl
import io.clawdroid.core.data.repository.TtsSettingsRepositoryImpl
import io.clawdroid.core.domain.repository.ChatRepository
import io.clawdroid.core.domain.repository.TtsCatalogRepository
import io.clawdroid.core.domain.repository.TtsSettingsRepository
import io.clawdroid.core.domain.usecase.ConnectChatUseCase
import io.clawdroid.core.domain.usecase.DisconnectChatUseCase
import io.clawdroid.core.domain.usecase.LoadMoreMessagesUseCase
import io.clawdroid.core.domain.usecase.ObserveConnectionUseCase
import io.clawdroid.core.domain.usecase.ObserveMessagesUseCase
import io.clawdroid.core.domain.usecase.ObserveStatusUseCase
import io.clawdroid.core.domain.usecase.SendMessageUseCase
import io.clawdroid.backend.api.GatewaySettingsStore
import io.clawdroid.feature.chat.ChatViewModel
import io.clawdroid.feature.chat.SettingsViewModel
import io.clawdroid.feature.chat.voice.SpeechRecognizerWrapper
import io.clawdroid.feature.chat.voice.TextToSpeechWrapper
import io.clawdroid.feature.chat.voice.CameraCaptureManager
import io.clawdroid.feature.chat.voice.VoiceModeManager
import io.clawdroid.settings.AppSettingsViewModel
import io.clawdroid.settings.GatewaySettingsStoreImpl
import io.clawdroid.setup.SetupApiClient
import io.clawdroid.setup.SetupViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import androidx.core.content.edit
import java.util.UUID
import java.util.concurrent.TimeUnit

val appModule = module {
    // CoroutineScope
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // GatewaySettingsStore
    single<GatewaySettingsStore> { GatewaySettingsStoreImpl(androidContext(), get()) }

    // Room
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "clawdroid.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
    single { get<AppDatabase>().messageDao() }

    // Ktor HttpClient
    single {
        HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                preconfigured = OkHttpClient.Builder()
                    .pingInterval(30, TimeUnit.SECONDS)
                    .build()
            }
        }
    }

    // WebSocketClient
    single {
        val prefs = androidContext().getSharedPreferences("clawdroid", android.content.Context.MODE_PRIVATE)
        val clientId = prefs.getString("client_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("client_id", it) }
        }
        WebSocketClient(get(), get(), clientId, context = androidContext())
    }

    // ImageFileStorage
    single { ImageFileStorage(androidContext()) }

    // Llama Model Manager
    single { LlamaModelManager(androidContext()) }

    // Repository
    single<ChatRepository> {
        val repo = ChatRepositoryImpl(get(), get(), get(), get())
        val llamaManager: LlamaModelManager = get()
        val messageDao: MessageDao = get()
        val handler = ToolRequestHandler(
            context = androidContext(),
            deviceController = get(),
            screenshotSource = get(),
            setOverlayVisibility = {},
            onAccessibilityNeeded = {}
        )
        repo.onToolRequest = { request ->
            val response = handler.handle(request)
            if (response.success) response.result ?: "" else response.error ?: "unknown error"
        }
        repo.localMessageHandler = { text, _, onStatus ->
            if (llamaManager.isLocalMode.value) {
                val userEntity = MessageMapper.toEntity(text, emptyList(), MessageStatus.SENT)
                messageDao.insert(userEntity)
                val reply = llamaManager.processLocalMessage(
                    userText = text,
                    onStatus = onStatus,
                    onToolRequest = { req ->
                        val resp = handler.handle(req)
                        if (resp.success) resp.result ?: "" else resp.error ?: "unknown error"
                    }
                )
                onStatus(null)
                val replyEntity = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    content = reply,
                    sender = "AGENT",
                    imagePathList = null,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.RECEIVED.name
                )
                messageDao.insert(replyEntity)
                true
            } else {
                false
            }
        }
        repo
    }
    single<TtsSettingsRepository> { TtsSettingsRepositoryImpl(androidContext()) }
    single<TtsCatalogRepository> {
        TtsCatalogRepositoryImpl(androidContext(), get<TtsSettingsRepository>().ttsConfig, get())
    }

    // UseCases
    factory { SendMessageUseCase(get()) }
    factory { ObserveMessagesUseCase(get()) }
    factory { ObserveConnectionUseCase(get()) }
    factory { ObserveStatusUseCase(get()) }
    factory { LoadMoreMessagesUseCase(get()) }
    factory { ConnectChatUseCase(get()) }
    factory { DisconnectChatUseCase(get()) }

    // Screenshot
    single { AccessibilityScreenshotSource() }
    single<ScreenshotSource> { get<AccessibilityScreenshotSource>() }

    // Device Controller (for Android tool)
    single { DeviceController() }

    // Voice
    factory { SpeechRecognizerWrapper(androidContext()) }
    single { TextToSpeechWrapper(androidContext(), get<TtsSettingsRepository>().ttsConfig) }
    single { CameraCaptureManager(androidContext()) }
    single { VoiceModeManager(get(), get(), get(), get(), get(), get()) }

    // Setup
    single { SetupApiClient(get()) }

    // ViewModel
    viewModel { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { AppSettingsViewModel(get(), get(), get()) }
    viewModel { SetupViewModel(get(), get()) }
}
