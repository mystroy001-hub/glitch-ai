package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppiumCodeGeneratorDialog
import com.example.ui.components.AppiumInspectorSheet
import com.example.ui.components.ChatGPTWebView
import com.example.ui.viewmodel.ChatAppiumViewModel
import com.example.ui.viewmodel.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: ChatAppiumViewModel,
    onNavigateToLogs: () -> Unit,
    onNavigateToEditor: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val promptText by viewModel.currentPrompt.collectAsState()
    val isWebViewMode by viewModel.isWebViewMode.collectAsState()
    val webUrl by viewModel.webUrl.collectAsState()
    val isAutomating by viewModel.isAutomating.collectAsState()
    val automationStatus by viewModel.lastAutomationStatus.collectAsState()
    val extractedDomJson by viewModel.extractedDomJson.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isLiveConnected by viewModel.isLiveConnected.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showLegalDialog by remember { mutableStateOf<String?>(null) } // "privacy" or "terms"
    
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF00FF66),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "⚡ GLITCH_OS // AI TERMINAL",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF66)
                             )
                            Text(
                                text = "STATUS: SYSTEM_ONLINE // SECURE_PORT",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF00FF66).copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Options",
                                tint = Color(0xFF00FF66)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF0A0A0A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("🎬 Glitch Video Editor", color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToEditor()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📜 Privacy Policy", color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    showMenu = false
                                    showLegalDialog = "privacy"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚖️ Terms of Service", color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    showMenu = false
                                    showLegalDialog = "terms"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📂 View Appium Logs", color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToLogs()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("🧼 Clear Chat History", color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace) },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearChat()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF000000)
                )
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Automation Progress Indicator (Sleek Green)
            if (isAutomating) {
                LinearProgressIndicator(
                    color = Color(0xFF00FF66),
                    trackColor = Color(0xFF05220C),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("automation_progress")
                )
            }

            // Automation Console Status Log Message
            if (isAutomating) {
                Surface(
                    color = Color(0xFF070707),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.2f))
                ) {
                    Text(
                        text = ">>> SYS_LOG: $automationStatus",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00FF66),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("automation_status_text")
                    )
                }
            }

            // Main Content Box (WebView kept active invisible + scrollable terminal lists)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Invisible, background ChatGPTWebView always alive to run NoTrack AI background automation seamlessly
                Column(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                ) {
                    ChatGPTWebView(url = webUrl, viewModel = viewModel, modifier = Modifier.weight(1f))
                }

                // Scrollable green terminal chatbot message window
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(message = msg, onNavigateToEditor = onNavigateToEditor)
                    }
                }
            }

            // Terminal Command Input Bar
            Surface(
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ">",
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { viewModel.onPromptChange(it) },
                        placeholder = {
                            Text(
                                "Ask NoTrack AI via Glitch terminal...",
                                color = Color(0xFF00FF66).copy(alpha = 0.4f),
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                            .semantics { contentDescription = "chat_input_field" },
                        shape = RoundedCornerShape(4.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF66)
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF050505),
                            unfocusedContainerColor = Color(0xFF010101),
                            focusedBorderColor = Color(0xFF00FF66),
                            unfocusedBorderColor = Color(0xFF00FF66).copy(alpha = 0.2f),
                            cursorColor = Color(0xFF00FF66)
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            viewModel.onPromptChange("[GENERATE_STORY_JSON]")
                            viewModel.sendMessage()
                        },
                        modifier = Modifier
                            .testTag("video_prompt_button")
                            .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .background(Color(0xFF050505))
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Generate Story JSON",
                            tint = Color(0xFF00FF66)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.sendMessage() },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF66),
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .testTag("send_button")
                            .semantics { contentDescription = "send_button" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send prompt",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RUN",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }

    // Custom Monospace Terminal Legal Dialog (Privacy/Terms)
    if (showLegalDialog != null) {
        AlertDialog(
            onDismissRequest = { showLegalDialog = null },
            title = {
                Text(
                    text = if (showLegalDialog == "privacy") "📜 GLITCH_OS // PRIVACY POLICY" else "⚖️ GLITCH_OS // TERMS OF SERVICE",
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .background(Color.Black)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (showLegalDialog == "privacy") {
                            """
                            === PRIVACY POLICY ===
                            Last Updated: September 2026
                            
                            1. DATA PRIVACY & COMPLIANCE
                            We respect your absolute privacy. GLITCH_OS does not collect, harvest, or store any personal information, keystrokes, or chat logs.
                            
                            2. LOCAL STORAGE
                            All conversations and logs remain securely within your local Android sandbox database.
                            
                            3. NO-TRACK COMPLIANCE
                            This terminal uses NoTrack AI background bridges which completely shield you from typical commercial user telemetry trackers.
                            
                            ======================
                            """.trimIndent()
                        } else {
                            """
                            === TERMS OF SERVICE ===
                            Last Updated: September 2026
                            
                            1. USER COMPLIANCE
                            By launching GLITCH_OS console terminal, you agree to comply with system operations rules and local guidelines.
                            
                            2. DRIVER COMPLIIntegrity
                            Operation of automation actions are conducted entirely under sandbox isolation protocols.
                            
                            3. LIABILITIES
                            This tool is provided "as-is" without any warranties.
                            
                            ========================
                            """.trimIndent()
                        },
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLegalDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66))
                ) {
                    Text("OK_ACKNOWLEDGE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0A0A0A)
        )
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage, onNavigateToEditor: () -> Unit) {
    val isUser = message.sender == "User"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}")
    ) {
        // Monospace Prompt Header
        Text(
            text = if (isUser) "[user@glitch ~]$" else "[glitch@terminal ~]$",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUser) Color(0xFF00FF66).copy(alpha = 0.8f) else Color(0xFF33FF99)
        )
        Spacer(modifier = Modifier.height(3.dp))

        // Message Text Styled Like Console Output
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = if (isUser) Color(0xFFE0E0E0) else Color(0xFF00FF66)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF050505))
                .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(10.dp)
        )

        if (!isUser && message.text.contains("\"scenes\"") && message.text.contains("{")) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onNavigateToEditor()
                },
                modifier = Modifier
                    .testTag("compose_faceless_btn_${message.id}")
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF66),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ COMPOSE FACELESS VIDEO",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
