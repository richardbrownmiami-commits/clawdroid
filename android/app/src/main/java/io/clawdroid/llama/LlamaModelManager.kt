package io.clawdroid.llama

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import io.clawdroid.core.data.remote.dto.ToolRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

class LlamaModelManager(
    private val context: Context,
    val engine: LlamaNativeEngine = LlamaNativeEngine()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _isLocalMode = MutableStateFlow(prefs.getBoolean(KEY_LOCAL_MODE, false))
    val isLocalMode: StateFlow<Boolean> = _isLocalMode.asStateFlow()

    private val _modelPath = MutableStateFlow(prefs.getString(KEY_MODEL_PATH, "") ?: "")
    val modelPath: StateFlow<String> = _modelPath.asStateFlow()

    private val _modelStatus = MutableStateFlow("Not loaded")
    val modelStatus: StateFlow<String> = _modelStatus.asStateFlow()

    private val _contextSize = MutableStateFlow(prefs.getInt(KEY_CONTEXT_SIZE, 512))
    val contextSize: StateFlow<Int> = _contextSize.asStateFlow()

    private val _threads = MutableStateFlow(prefs.getInt(KEY_THREADS, 4))
    val threads: StateFlow<Int> = _threads.asStateFlow()

    private val _temperature = MutableStateFlow(prefs.getFloat(KEY_TEMP, 0.7f))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    init {
        updateStatus()
        if (_isLocalMode.value && _modelPath.value.isNotEmpty() && File(_modelPath.value).exists()) {
            loadModel(_modelPath.value, _contextSize.value, _threads.value)
        }
    }

    fun setLocalMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOCAL_MODE, enabled) }
        _isLocalMode.value = enabled
        if (enabled && !engine.isModelLoaded() && _modelPath.value.isNotEmpty()) {
            loadModel(_modelPath.value, _contextSize.value, _threads.value)
        }
    }

    fun setModelPath(path: String) {
        prefs.edit { putString(KEY_MODEL_PATH, path) }
        _modelPath.value = path
    }

    fun setContextSize(size: Int) {
        prefs.edit { putInt(KEY_CONTEXT_SIZE, size) }
        _contextSize.value = size
    }

    fun setThreads(threads: Int) {
        prefs.edit { putInt(KEY_THREADS, threads) }
        _threads.value = threads
    }

    fun setTemperature(temp: Float) {
        prefs.edit { putFloat(KEY_TEMP, temp) }
        _temperature.value = temp
    }

    fun loadModel(
        path: String = _modelPath.value,
        ctx: Int = _contextSize.value,
        th: Int = _threads.value
    ): Boolean {
        if (path.isEmpty()) {
            _modelStatus.value = "No model file path selected"
            return false
        }
        val file = File(path)
        if (!file.exists()) {
            _modelStatus.value = "File not found: ${file.name}"
            return false
        }

        setModelPath(path)
        _modelStatus.value = "Loading ${file.name}…"
        val success = try {
            engine.nativeLoadModel(path, ctx, th)
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading model", e)
            false
        }

        if (success) {
            val info = engine.nativeModelInfo()
            _modelStatus.value = "Loaded: ${file.name} ($ctx ctx, $th threads)"
            Log.i(TAG, "Model loaded: $info")
        } else {
            val err = engine.nativeLastError()
            _modelStatus.value = if (err.isNotEmpty()) "Load error: $err" else "Failed to load model"
            Log.e(TAG, "Failed to load model: $err")
        }
        return success
    }

    fun unloadModel() {
        try {
            engine.nativeUnloadModel()
            _modelStatus.value = "Unloaded"
        } catch (e: Throwable) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    private fun updateStatus() {
        _modelStatus.value = if (engine.isModelLoaded()) {
            val path = _modelPath.value
            val name = if (path.isNotEmpty()) File(path).name else "GGUF Model"
            "Loaded: $name"
        } else {
            "No model loaded"
        }
    }

    suspend fun processLocalMessage(
        userText: String,
        historyTurns: List<LlamaNativeEngine.Turn> = emptyList(),
        onStatus: (String?) -> Unit,
        onToolRequest: suspend (ToolRequest) -> String
    ): String = withContext(Dispatchers.IO) {
        if (!engine.isModelLoaded()) {
            if (_modelPath.value.isNotEmpty() && File(_modelPath.value).exists()) {
                onStatus("Loading GGUF model…")
                loadModel(_modelPath.value, _contextSize.value, _threads.value)
            }
            if (!engine.isModelLoaded()) {
                return@withContext "⚠️ No GGUF model is loaded. Go to Settings → AI Engine & Local Model to pick and load your .gguf model."
            }
        }

        onStatus("Thinking…")
        val encodedHistory = engine.encodeHistory(historyTurns)

        val systemPrompt = """You are ClawDroid, an offline Android phone assistant.
You can answer questions and perform device actions on this phone.
When you need to run an action, output a JSON block in this format:
```json
{"action": "ACTION_NAME", "params": {...}}
```
Common actions:
- launch_app(package_name: string)
- search_apps(query: string)
- open_url(url: string)
- web_search(query: string)
- set_alarm(hour: int, minutes: int, message: string)
- set_timer(length_seconds: int, message: string)
- dial(phone_number: string)
- compose_sms(phone_number: string, message: string)
- flashlight(enabled: boolean)
- set_volume(volume_percent: int)
- set_brightness(brightness_percent: int)
- clipboard_copy(text: string)
- clipboard_read()
- tap(x: int, y: int)
- swipe(start_x: int, start_y: int, end_x: int, end_y: int)
- text(text: string)
- keyevent(keycode: int)

If no action is needed, reply to the user directly.""".trimIndent()

        val responseBuilder = StringBuilder()
        try {
            engine.generateChatStreaming(
                systemPrompt = systemPrompt,
                historyEncoded = encodedHistory,
                userText = userText,
                maxTokens = 256,
                temperature = _temperature.value,
                topP = 0.9f,
                repeatPenalty = 1.1f,
                onToken = { token ->
                    if (!token.startsWith("\u0001")) {
                        responseBuilder.append(token)
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Generation failed", e)
            return@withContext "Error during model inference: ${e.message}"
        }

        val rawResponse = responseBuilder.toString().trim()
        val parsedAction = extractAction(rawResponse)

        if (parsedAction != null) {
            onStatus("Running action: ${parsedAction.action}…")
            val toolRequest = ToolRequest(
                requestId = UUID.randomUUID().toString(),
                action = parsedAction.action,
                params = parsedAction.params
            )
            val result = try {
                onToolRequest(toolRequest)
            } catch (e: Throwable) {
                "Error: ${e.message}"
            }

            val cleanText = rawResponse.replace(parsedAction.rawBlock, "").trim()
            buildString {
                if (cleanText.isNotEmpty()) {
                    append(cleanText)
                    append("\n\n")
                }
                append("⚙️ **Action:** `${parsedAction.action}`\n")
                append("**Result:** $result")
            }
        } else {
            rawResponse.ifEmpty { "(No response generated)" }
        }
    }

    private data class ParsedAction(
        val action: String,
        val params: JsonObject? = null,
        val rawBlock: String = ""
    )

    private fun extractAction(text: String): ParsedAction? {
        val blockRegex = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        val match = blockRegex.find(text)
        if (match != null) {
            val jsonStr = match.groupValues[1]
            try {
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val action = obj["action"]?.jsonPrimitive?.content
                if (!action.isNullOrEmpty()) {
                    val params = obj["params"]?.let {
                        try { it.jsonObject } catch (_: Throwable) { null }
                    }
                    return ParsedAction(action = action, params = params, rawBlock = match.value)
                }
            } catch (_: Throwable) {}
        }

        // Inline json fallback: {"action": "...", ...}
        val inlineRegex = Regex("(\\{[^{}]*\"action\"\\s*:\\s*\"[^\"]+\"[^{}]*\\})")
        val inlineMatch = inlineRegex.find(text)
        if (inlineMatch != null) {
            val jsonStr = inlineMatch.groupValues[1]
            try {
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                val action = obj["action"]?.jsonPrimitive?.content
                if (!action.isNullOrEmpty()) {
                    val params = obj["params"]?.let {
                        try { it.jsonObject } catch (_: Throwable) { null }
                    }
                    return ParsedAction(action = action, params = params, rawBlock = inlineMatch.value)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    companion object {
        private const val TAG = "LlamaModelManager"
        private const val PREFS_NAME = "clawdroid_local_llama"
        private const val KEY_LOCAL_MODE = "local_mode_enabled"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_CONTEXT_SIZE = "context_size"
        private const val KEY_THREADS = "threads"
        private const val KEY_TEMP = "temperature"
    }
}
