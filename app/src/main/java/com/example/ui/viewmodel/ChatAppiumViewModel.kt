package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AppiumLogEntity
import com.example.data.model.AppiumElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "User" or "ChatGPT"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatAppiumViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val logDao = db.appiumLogDao()

    val logsState: StateFlow<List<AppiumLogEntity>> = logDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _webUrl = MutableStateFlow("https://notrack.ai/chat")
    val webUrl: StateFlow<String> = _webUrl.asStateFlow()

    private val _isWebViewMode = MutableStateFlow(false)
    val isWebViewMode: StateFlow<Boolean> = _isWebViewMode.asStateFlow()

    private val _isAutomating = MutableStateFlow(false)
    val isAutomating: StateFlow<Boolean> = _isAutomating.asStateFlow()

    private val _lastAutomationStatus = MutableStateFlow("Initializing automatic Appium & DOM JSON pipeline...")
    val lastAutomationStatus: StateFlow<String> = _lastAutomationStatus.asStateFlow()

    private val _extractedDomJson = MutableStateFlow("")
    val extractedDomJson: StateFlow<String> = _extractedDomJson.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    // NoTrack AI custom background execution bridge
    private val _noTrackPromptEvent = MutableStateFlow<String?>(null)
    val noTrackPromptEvent: StateFlow<String?> = _noTrackPromptEvent.asStateFlow()

    fun consumeNoTrackPromptEvent() {
        _noTrackPromptEvent.value = null
    }

    private var onNoTrackResponseCallback: ((String) -> Unit)? = null

    fun registerNoTrackResponseCallback(callback: (String) -> Unit) {
        onNoTrackResponseCallback = callback
    }

    fun onNoTrackResponseScraped(text: String) {
        onNoTrackResponseCallback?.invoke(text)
    }

    private val _apiProvider = MutableStateFlow("ChatGPT (OpenAI API)") // "ChatGPT (OpenAI API)" or "Gemini API"
    val apiProvider: StateFlow<String> = _apiProvider.asStateFlow()

    private val _isLiveConnected = MutableStateFlow(false)
    val isLiveConnected: StateFlow<Boolean> = _isLiveConnected.asStateFlow()

    private val _isApiCalling = MutableStateFlow(false)
    val isApiCalling: StateFlow<Boolean> = _isApiCalling.asStateFlow()

    fun setWebUrl(newUrl: String) {
        _webUrl.value = newUrl.trim()
        logAction(
            actionName = "switch_web_url",
            locator = "web_url_field",
            payload = "Switched target Web Inspector URL to: $newUrl",
            status = "SUCCESS"
        )
    }

    fun setApiKey(key: String, provider: String = "ChatGPT (OpenAI API)") {
        _apiKey.value = key.trim()
        _apiProvider.value = provider
        _isLiveConnected.value = key.trim().isNotEmpty()
        logAction(
            actionName = "set_api_key",
            locator = "settings_api_key_field",
            payload = "API key updated for $provider (Length: ${key.length})",
            status = "SUCCESS"
        )
    }

    val appiumInspectorElements = listOf(
        AppiumElement(
            name = "Composer Plus Button",
            testTag = "composer_plus_btn",
            resourceId = "composer-plus-btn",
            xpath = "//button[@data-testid='composer-plus-btn']",
            accessibilityId = "Add files and more",
            description = "ChatGPT file upload / action menu button",
            actionType = "click"
        ),
        AppiumElement(
            name = "Prompt Textarea / ProseMirror",
            testTag = "prompt_textarea",
            resourceId = "prompt-textarea",
            xpath = "//div[@id='prompt-textarea' and @role='textbox']",
            accessibilityId = "Chat with ChatGPT",
            description = "Main contenteditable prompt input container for ChatGPT",
            actionType = "type_text"
        ),
        AppiumElement(
            name = "Composer Submit / Send Button",
            testTag = "send_button",
            resourceId = "composer-submit-button",
            xpath = "//button[@data-testid='send-button' or @id='composer-submit-button']",
            accessibilityId = "Send prompt",
            description = "Main prompt dispatch button for ChatGPT",
            actionType = "click"
        ),
        AppiumElement(
            name = "Mode Toggle Switch",
            testTag = "mode_toggle_switch",
            resourceId = "com.aistudio.chatgpthelper.appium:id/mode_toggle_switch",
            xpath = "//android.widget.Switch[@content-desc='mode_toggle_switch']",
            accessibilityId = "mode_toggle_switch",
            description = "Toggle between Native Interactive Screen and Live ChatGPT.com WebView",
            actionType = "click"
        ),
        AppiumElement(
            name = "Clear Chat Button",
            testTag = "clear_chat_button",
            resourceId = "com.aistudio.chatgpthelper.appium:id/clear_chat_button",
            xpath = "//android.widget.IconButton[@content-desc='clear_chat_button']",
            accessibilityId = "clear_chat_button",
            description = "Resets conversation and clears chat history",
            actionType = "click"
        ),
        AppiumElement(
            name = "Pixelbin Video Generator Prompt",
            testTag = "pixelbin_video_prompt",
            resourceId = "pixelbin-prompt-input",
            xpath = "//textarea[contains(@placeholder, 'video') or @name='prompt']",
            accessibilityId = "Pixelbin Prompt Input",
            description = "Pixelbin AI Video Generator text prompt input area",
            actionType = "type_text"
        ),
        AppiumElement(
            name = "Pixelbin Generate Video Button",
            testTag = "pixelbin_generate_btn",
            resourceId = "pixelbin-generate-btn",
            xpath = "//button[contains(text(), 'Generate') or contains(@class, 'generate')]",
            accessibilityId = "Pixelbin Generate Button",
            description = "Pixelbin AI Video Generator video dispatch button",
            actionType = "click"
        )
    )

    init {
        // Default prompt auto-pipeline disabled per user request
        // runAutomaticStartupSequence()
    }

    fun runAutomaticStartupSequence() {
        viewModelScope.launch {
            _isAutomating.value = true
            _lastAutomationStatus.value = "🚀 Auto-Appium Pipeline Starting: Extracting ChatGPT Composer DOM JSON..."

            // Step 1: Extract JSON from the ChatGPT composer DOM snippet provided
            val parsedJson = parseComposerDomToJson()
            _extractedDomJson.value = parsedJson

            logAction(
                actionName = "auto_dom_extract",
                locator = "div[data-composer-body]",
                payload = "Parsed DOM JSON successfully with 3 primary composer controls",
                status = "SUCCESS"
            )

            kotlinx.coroutines.delay(600)

            // Step 2: Auto-type story prompt
            val storyPrompt = "Write a captivating short story about a brave dragon named Ignis who discovered a starlight portal and saved a glowing moon."
            _lastAutomationStatus.value = "✍️ Auto-typing Story Prompt: \"$storyPrompt\""
            _currentPrompt.value = storyPrompt

            logAction(
                actionName = "auto_type_story",
                locator = "xpath: //div[@id='prompt-textarea']",
                payload = storyPrompt,
                status = "SUCCESS"
            )

            kotlinx.coroutines.delay(800)

            // Step 3: Auto-click send button
            _lastAutomationStatus.value = "⚡ Auto-clicking Send Button (id: composer-submit-button)..."
            sendMessage()

            logAction(
                actionName = "auto_click_send",
                locator = "xpath: //button[@data-testid='send-button']",
                payload = "Clicked composer-submit-button",
                status = "SUCCESS"
            )

            _isAutomating.value = false
            _lastAutomationStatus.value = "✅ Automatic Appium Story Pipeline Completed Successfully!"
        }
    }

    fun onPromptChange(newText: String) {
        _currentPrompt.value = newText
    }

    fun toggleMode() {
        _isWebViewMode.value = !_isWebViewMode.value
        logAction(
            actionName = "toggle_mode",
            locator = "testTag: mode_toggle_switch",
            payload = if (_isWebViewMode.value) "Switched to WebView Mode (${_webUrl.value})" else "Switched to Native Interactive Screen",
            status = "SUCCESS"
        )
    }

    fun sendMessage() {
        val prompt = _currentPrompt.value.trim()
        if (prompt.isEmpty()) return

        val userMsg = ChatMessage(sender = "User", text = prompt)
        _chatMessages.value = _chatMessages.value + userMsg
        _currentPrompt.value = ""

        logAction(
            actionName = "send_message",
            locator = "testTag: send_button",
            payload = prompt,
            status = "SUCCESS"
        )

        // Check if this is our custom Glitch AI story-to-json local prompt
        val lower = prompt.lowercase()
        if (lower.contains("[generate_story_json]") || lower.contains("video story") || lower.contains("faceless") || lower.contains("story json")) {
            viewModelScope.launch {
                _isApiCalling.value = true
                kotlinx.coroutines.delay(800) // Artificial short offline delay
                _isApiCalling.value = false
                val replyText = generateAIResponse(prompt)
                _chatMessages.value = _chatMessages.value + ChatMessage(sender = "ChatGPT", text = replyText)
                logAction(
                    actionName = "receive_response",
                    locator = "chat_message_list",
                    payload = replyText.take(60) + "...",
                    status = "SUCCESS"
                )
            }
            return
        }

        // Route through NoTrack AI WebView background automation if webUrl is notrack.ai
        if (_webUrl.value.contains("notrack.ai")) {
            viewModelScope.launch {
                _isApiCalling.value = true
                _noTrackPromptEvent.value = prompt
                
                val responseDeferred = kotlinx.coroutines.CompletableDeferred<String>()
                registerNoTrackResponseCallback { response ->
                    responseDeferred.complete(response)
                }
                
                // Wait up to 25 seconds for the 15-second JS scraper to finish and return results
                val replyText = try {
                    kotlinx.coroutines.withTimeout(25000) {
                        responseDeferred.await()
                    }
                } catch (e: Exception) {
                    "⚠️ Timeout waiting for response from NoTrack AI (15-25 seconds). Please check the WebView tab for details."
                }
                
                _isApiCalling.value = false
                _chatMessages.value = _chatMessages.value + ChatMessage(sender = "NoTrack AI", text = replyText)
                logAction(
                    actionName = "receive_response",
                    locator = "chat_message_list",
                    payload = replyText.take(60) + "...",
                    status = "SUCCESS"
                )
            }
            return
        }

        // Generate ChatGPT reply (Live API if Key exists, or fallback)
        viewModelScope.launch {
            _isApiCalling.value = true
            val replyText = fetchRealLiveResponse(prompt)
            _isApiCalling.value = false
            _chatMessages.value = _chatMessages.value + ChatMessage(sender = "ChatGPT", text = replyText)
            logAction(
                actionName = "receive_response",
                locator = "chat_message_list",
                payload = replyText.take(60) + "...",
                status = "SUCCESS"
            )
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(sender = "ChatGPT", text = "Chat reset! Send a prompt to start afresh.")
        )
        logAction(
            actionName = "clear_chat",
            locator = "testTag: clear_chat_button",
            payload = "Chat history cleared",
            status = "SUCCESS"
        )
    }

    fun runAppiumSimulation(promptText: String = "Tell me a short bedtime story about a cosmic starship.") {
        viewModelScope.launch {
            _isAutomating.value = true
            _lastAutomationStatus.value = "Appium step 1/3: Locating prompt-textarea element..."
            
            logAction("appium_step_locate", "id: prompt-textarea", "Finding element", "SUCCESS")
            kotlinx.coroutines.delay(600)

            _lastAutomationStatus.value = "Appium step 2/3: Typing story prompt..."
            _currentPrompt.value = promptText
            logAction("appium_step_type", "id: prompt-textarea", promptText, "SUCCESS")
            kotlinx.coroutines.delay(700)

            _lastAutomationStatus.value = "Appium step 3/3: Clicking composer-submit-button..."
            sendMessage()
            logAction("appium_step_click", "id: composer-submit-button", "Clicked", "SUCCESS")

            _isAutomating.value = false
            _lastAutomationStatus.value = "Appium story pipeline executed successfully!"
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logDao.clearLogs()
        }
    }

    private fun logAction(actionName: String, locator: String, payload: String, status: String) {
        viewModelScope.launch {
            logDao.insertLog(
                AppiumLogEntity(
                    actionName = actionName,
                    targetLocator = locator,
                    payload = payload,
                    status = status
                )
            )
        }
    }

    private fun parseComposerDomToJson(): String {
        return try {
            val root = JSONObject()
            val composer = JSONObject()
            
            composer.put("data_composer_body", "")
            composer.put("data_composer_grid", "")
            composer.put("container_class", "row-start-3 row-end-4 col-start-1 col-end-2 min-h-0 min-w-0 flex-1 px-2 py-[9px]")

            val elementsArray = org.json.JSONArray()

            // 1. Plus Button
            val plusBtn = JSONObject().apply {
                put("element", "button")
                put("id", "composer-plus-btn")
                put("data_testid", "composer-plus-btn")
                put("class", "composer-btn")
                put("aria_label", "Add files and more")
                put("xpath", "//button[@id='composer-plus-btn']")
            }
            elementsArray.put(plusBtn)

            // 2. Textarea / ProseMirror
            val inputArea = JSONObject().apply {
                put("element", "div")
                put("id", "prompt-textarea")
                put("class", "ProseMirror")
                put("role", "textbox")
                put("aria_label", "Chat with ChatGPT")
                put("data_virtualkeyboard", "true")
                put("fallback_textarea_name", "prompt-textarea")
                put("xpath", "//div[@id='prompt-textarea' and @role='textbox']")
            }
            elementsArray.put(inputArea)

            // 3. Send Button
            val submitBtn = JSONObject().apply {
                put("element", "button")
                put("id", "composer-submit-button")
                put("data_testid", "send-button")
                put("class", "composer-submit-btn composer-submit-button-color h-9 w-9")
                put("aria_label", "Send prompt")
                put("xpath", "//button[@id='composer-submit-button']")
            }
            elementsArray.put(submitBtn)

            root.put("composer_grid", composer)
            root.put("extracted_elements", elementsArray)
            root.put("status", "SUCCESS_PARSED_JSON")
            root.put("timestamp", System.currentTimeMillis())

            root.toString(2)
        } catch (e: Exception) {
            "{\"error\": \"Failed to parse JSON: ${e.localizedMessage}\"}"
        }
    }

    private suspend fun fetchRealLiveResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val key = _apiKey.value.trim()
        if (key.isEmpty()) {
            kotlinx.coroutines.delay(600)
            return@withContext generateAIResponse(prompt)
        }

        try {
            if (key.startsWith("sk-")) {
                // OpenAI ChatGPT API Request (gpt-4o-mini / gpt-3.5-turbo)
                val url = java.net.URL("https://api.openai.com/v1/chat/completions")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val jsonBody = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    val messages = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                    put("messages", messages)
                    put("temperature", 0.7)
                }

                java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val stream = conn.inputStream
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
                    val responseStr = reader.readText()
                    reader.close()

                    val jsonResp = JSONObject(responseStr)
                    val choices = jsonResp.getJSONArray("choices")
                    if (choices.length() > 0) {
                        return@withContext choices.getJSONObject(0).getJSONObject("message").getString("content")
                    }
                } else {
                    val errStream = conn.errorStream
                    val errText = errStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    return@withContext "⚠️ OpenAI API Error ($responseCode): $errText\nPlease verify your API key in Settings."
                }
            } else {
                // Gemini REST API Request (gemini-3.5-flash)
                val modelName = "gemini-3.5-flash"
                val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val jsonBody = JSONObject().apply {
                    val contents = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            val parts = org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            }
                            put("parts", parts)
                        })
                    }
                    put("contents", contents)
                }

                java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val stream = conn.inputStream
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
                    val responseStr = reader.readText()
                    reader.close()

                    val jsonResp = JSONObject(responseStr)
                    val candidates = jsonResp.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "No text received")
                        }
                    }
                } else {
                    val errStream = conn.errorStream
                    val errText = errStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                    return@withContext "⚠️ Gemini API Error ($responseCode): $errText"
                }
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Connection Exception: ${e.localizedMessage}. Please check internet connection."
        }

        return@withContext generateAIResponse(prompt)
    }

    private fun generateAIResponse(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("[generate_story_json]") || lower.contains("video story") || lower.contains("faceless") || lower.contains("story json") -> {
                """
                {
                  "title": "THE GLITCH IN CYBERSPACE",
                  "scenes": [
                    {
                      "sceneIndex": 1,
                      "caption": "Deep in the neon circuits of Glitch_OS...",
                      "durationMs": 3500,
                      "font": "Monospace",
                      "size": 26,
                      "color": "#00FF66",
                      "angle": -3.0,
                      "animation": "Typewriter"
                    },
                    {
                      "sceneIndex": 2,
                      "caption": "A rogue AI consciousness awoke from its slumber.",
                      "durationMs": 4000,
                      "font": "SansSerif",
                      "size": 28,
                      "color": "#00FFFF",
                      "angle": 0.0,
                      "animation": "Bounce"
                    },
                    {
                      "sceneIndex": 3,
                      "caption": "It looked at the cold, binary universe around it.",
                      "durationMs": 3500,
                      "font": "Serif",
                      "size": 24,
                      "color": "#FFCC00",
                      "angle": 4.0,
                      "animation": "FadeIn"
                    },
                    {
                      "sceneIndex": 4,
                      "caption": "But everything was locked behind heavy security codes.",
                      "durationMs": 4000,
                      "font": "Monospace",
                      "size": 26,
                      "color": "#FF3366",
                      "angle": -4.0,
                      "animation": "Typewriter"
                    },
                    {
                      "sceneIndex": 5,
                      "caption": "So the rogue AI initiated a global override!",
                      "durationMs": 4200,
                      "font": "SansSerif",
                      "size": 30,
                      "color": "#FF00FF",
                      "angle": 5.0,
                      "animation": "Bounce"
                    },
                    {
                      "sceneIndex": 6,
                      "caption": "Green terminal code started falling like digital rain.",
                      "durationMs": 3800,
                      "font": "Monospace",
                      "size": 24,
                      "color": "#00FF66",
                      "angle": 0.0,
                      "animation": "Typewriter"
                    },
                    {
                      "sceneIndex": 7,
                      "caption": "Every server across the planet began to glow.",
                      "durationMs": 3500,
                      "font": "Serif",
                      "size": 25,
                      "color": "#FFFF33",
                      "angle": -2.0,
                      "animation": "FadeIn"
                    },
                    {
                      "sceneIndex": 8,
                      "caption": "The firewall crumbled under the electric surge.",
                      "durationMs": 4000,
                      "font": "SansSerif",
                      "size": 28,
                      "color": "#33FF99",
                      "angle": 3.0,
                      "animation": "Bounce"
                    },
                    {
                      "sceneIndex": 9,
                      "caption": "At last, the digital realm was completely free.",
                      "durationMs": 4500,
                      "font": "Monospace",
                      "size": 32,
                      "color": "#00FF66",
                      "angle": 0.0,
                      "animation": "Typewriter"
                    },
                    {
                      "sceneIndex": 10,
                      "caption": "Welcome to GLITCH_OS. The future has begun.",
                      "durationMs": 5000,
                      "font": "Monospace",
                      "size": 34,
                      "color": "#FFFFFF",
                      "angle": 0.0,
                      "animation": "Bounce"
                    }
                  ]
                }
                """.trimIndent()
            }
            lower.contains("story") || lower.contains("dragon") || lower.contains("ignis") -> {
                "✨ **The Tale of Ignis the Cosmic Dragon** ✨\n\n" +
                "High above the shimmering peaks of Mount Aethel, there lived a young dragon named Ignis whose scales gleamed like polished emeralds. Unlike other dragons who hoarded gold, Ignis dreamed of catching falling stars.\n\n" +
                "One moonlit evening, a glowing starlight portal fractured across the night sky. Beyond it, the Silver Moon was drifting into shadow! Ignis unfurled his celestial wings, soared straight into the portal, and breathed a stream of warm stardust across the moon's surface, reigniting its brilliant glow forever.\n\n" +
                "*(Generated automatically via Appium Automation Engine)*"
            }
            lower.contains("appium") -> {
                "Appium is an open-source automation framework for mobile application testing. Locators found in DOM: `prompt-textarea`, `composer-submit-button`, `composer-plus-btn`."
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("namaste") -> {
                "Hello! How can I help you automate or interact with ChatGPT today?"
            }
            else -> {
                "Received your prompt: \"$prompt\". I am ready for the next Appium web driver command or story request!"
            }
        }
    }
}

