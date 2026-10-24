package io.clawdroid.llama.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R as LucideR
import io.clawdroid.core.ui.theme.DeepBlack
import io.clawdroid.core.ui.theme.GlassBorder
import io.clawdroid.core.ui.theme.GlassWhite
import io.clawdroid.core.ui.theme.GradientCyan
import io.clawdroid.core.ui.theme.GradientPurple
import io.clawdroid.core.ui.theme.NeonCyan
import io.clawdroid.core.ui.theme.TextPrimary
import io.clawdroid.core.ui.theme.TextSecondary
import io.clawdroid.llama.LlamaFileResolver
import io.clawdroid.llama.LlamaModelManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlamaSettingsScreen(
    onNavigateBack: () -> Unit,
    llamaManager: LlamaModelManager = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isLocalMode by llamaManager.isLocalMode.collectAsState()
    val modelPath by llamaManager.modelPath.collectAsState()
    val modelStatus by llamaManager.modelStatus.collectAsState()
    val contextSize by llamaManager.contextSize.collectAsState()
    val threads by llamaManager.threads.collectAsState()
    val temperature by llamaManager.temperature.collectAsState()

    var manualPathInput by remember(modelPath) { mutableStateOf(modelPath) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importStatus = "Importing file…"
                val resolved = LlamaFileResolver.resolveOrCopyModel(context, uri) {
                    importStatus = it
                }
                if (resolved != null) {
                    manualPathInput = resolved
                    llamaManager.setModelPath(resolved)
                    importStatus = "File selected. Tap Load Model to initialize."
                } else {
                    importStatus = "Failed to access or copy file"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GradientCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.1f),
                        radius = size.width * 0.8f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GradientPurple.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.9f),
                        radius = size.width * 0.7f
                    )
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Local Model (llama.cpp)", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_arrow_left),
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mode Toggle Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = GlassWhite),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Inbuilt llama.cpp",
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isLocalMode) {
                                    "Offline direct inference active (no server or API port)"
                                } else {
                                    "Cloud AI Gateway active (tap switch to use local GGUF)"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isLocalMode,
                            onCheckedChange = { llamaManager.setLocalMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }
                }

                if (isLocalMode) {
                    // Status Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Engine & Model Status", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = modelStatus,
                                color = if (llamaManager.engine.isModelLoaded()) NeonCyan else TextPrimary,
                                fontSize = 14.sp
                            )
                            if (importStatus != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = importStatus!!, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Model Selection Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Model File (.gguf)", color = TextPrimary, fontSize = 15.sp)

                            OutlinedTextField(
                                value = manualPathInput,
                                onValueChange = {
                                    manualPathInput = it
                                    llamaManager.setModelPath(it)
                                },
                                label = { Text("Model file path", color = TextSecondary) },
                                placeholder = { Text("/sdcard/Download/model.gguf", color = TextSecondary.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = GlassBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassBorder)
                                ) {
                                    Text("Pick .gguf", color = TextPrimary)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            llamaManager.loadModel(manualPathInput, contextSize, threads)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("Load Model", color = Color.Black)
                                }
                            }

                            if (llamaManager.engine.isModelLoaded()) {
                                OutlinedButton(
                                    onClick = { llamaManager.unloadModel() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                ) {
                                    Text("Unload Model")
                                }
                            }
                        }
                    }

                    // Performance & Parameters Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Inference Parameters", color = TextPrimary, fontSize = 15.sp)

                            // Context size
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Context Size (RAM limit)", color = TextSecondary, fontSize = 13.sp)
                                    Text("$contextSize tokens", color = NeonCyan, fontSize = 13.sp)
                                }
                                Slider(
                                    value = contextSize.toFloat(),
                                    onValueChange = { llamaManager.setContextSize(it.toInt()) },
                                    valueRange = 256f..2048f,
                                    steps = 6,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan
                                    )
                                )
                            }

                            // CPU Threads
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CPU Threads", color = TextSecondary, fontSize = 13.sp)
                                    Text("$threads threads", color = NeonCyan, fontSize = 13.sp)
                                }
                                Slider(
                                    value = threads.toFloat(),
                                    onValueChange = { llamaManager.setThreads(it.toInt()) },
                                    valueRange = 1f..8f,
                                    steps = 6,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan
                                    )
                                )
                            }

                            // Temperature
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Temperature", color = TextSecondary, fontSize = 13.sp)
                                    Text(String.format("%.2f", temperature), color = NeonCyan, fontSize = 13.sp)
                                }
                                Slider(
                                    value = temperature,
                                    onValueChange = { llamaManager.setTemperature(it) },
                                    valueRange = 0.1f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Cloud AI Mode Info Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = GlassWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Cloud AI Mode Active", color = NeonCyan, fontSize = 15.sp)
                            Text(
                                text = "ClawDroid is currently communicating with your Cloud AI Gateway and configured external provider (Claude, OpenAI, Ollama, etc.).",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Toggle 'Use Inbuilt llama.cpp' above anytime to switch to fully offline in-process model execution.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
