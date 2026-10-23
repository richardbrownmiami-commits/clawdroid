package io.clawdroid.llama

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LlamaFileResolver {
    private const val TAG = "LlamaFileResolver"

    fun getFileName(context: Context, uri: Uri): String {
        var name = "model.gguf"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex) ?: name
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve file name: ${e.message}")
        }
        return name
    }

    suspend fun resolveOrCopyModel(
        context: Context,
        uri: Uri,
        onProgress: (String) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)

        // Check if URI is already a file:// URI
        if (uri.scheme == "file") {
            uri.path?.let { return@withContext it }
        }

        // Try direct SD card / shared storage path heuristic
        val pathStr = uri.path ?: ""
        if (pathStr.contains("/storage/") || pathStr.contains("/sdcard/")) {
            val idx = pathStr.indexOf("/storage/")
            if (idx != -1) {
                val candidate = File(pathStr.substring(idx))
                if (candidate.exists()) return@withContext candidate.absolutePath
            }
        }

        // Fallback: Copy to app files dir models folder
        val modelsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val targetFile = File(modelsDir, fileName)

        try {
            onProgress("Importing ${fileName}…")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model from URI", e)
            null
        }
    }
}
