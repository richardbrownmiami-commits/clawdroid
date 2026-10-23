package io.clawdroid.llama

import android.util.Log

class LlamaNativeEngine {
    external fun nativeEngineInfo(): String
    external fun nativeLoadModel(path: String, contextSize: Int, threads: Int): Boolean
    external fun nativeUnloadModel()
    external fun nativeModelInfo(): String
    external fun nativeLastError(): String
    external fun nativeMemoryBytes(): Long
    external fun nativeCancelGeneration()
    external fun nativeGenerateChatStreaming(
        systemPrompt: String,
        historyEncoded: String,
        userText: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float
    )
    external fun nativeBenchmark(systemPrompt: String, maxTokens: Int): String

    @Volatile
    private var tokenListener: ((String) -> Unit)? = null

    fun onNativeToken(token: String) {
        tokenListener?.invoke(token)
    }

    fun generateChatStreaming(
        systemPrompt: String,
        historyEncoded: String,
        userText: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
        onToken: (String) -> Unit
    ) {
        tokenListener = onToken
        try {
            nativeGenerateChatStreaming(
                systemPrompt,
                historyEncoded,
                userText,
                maxTokens,
                temperature,
                topP,
                repeatPenalty
            )
        } finally {
            tokenListener = null
        }
    }

    fun isModelLoaded(): Boolean {
        return try {
            !nativeModelInfo().startsWith("No GGUF model loaded")
        } catch (_: Throwable) {
            false
        }
    }

    fun encodeHistory(turns: List<Turn>): String = turns.joinToString("\u001e") { turn ->
        val role = if (turn.isUser) "user" else "assistant"
        val content = turn.text.replace("\u001e", " ").replace("\u001f", " ")
        "\u001f"
    }

    data class Turn(val isUser: Boolean, val text: String)

    companion object {
        private const val TAG = "LlamaNativeEngine"
        var isLibraryLoaded = false
            private set

        init {
            try {
                System.loadLibrary("clawllama")
                isLibraryLoaded = true
                Log.i(TAG, "Loaded native clawllama library successfully")
            } catch (t: Throwable) {
                Log.w(TAG, "clawllama native library could not be loaded: ${t.message}")
            }
        }

        const val DEFAULT_CONTEXT_SIZE = 512
        const val DEFAULT_THREADS = 4
        const val DEFAULT_MAX_TOKENS = 256
        const val DEFAULT_TEMP = 0.7f
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_PENALTY = 1.1f
    }
}
