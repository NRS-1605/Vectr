package com.vectr

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.provider.MediaStore
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.compose.ui.platform.ComposeView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, CAPTURE, MACROS, TOUCHPAD, FILES, INVENTORY, TELEMETRY, NEWS, TODO, SPACE, SCHEDWALL, GOALS, CGPA, CLIPBOARD_HISTORY, SETTINGS }

    private lateinit var content: View
    private lateinit var navButtons: Map<Screen, ImageButton>
    private var currentScreen = Screen.HOME
    private var isConnected = false
    private var connectionLabel: TextView? = null
    private var connectionDot: ImageView? = null
    private var connectionSource = "Discovering axon-core"
    private var macroGrid: LinearLayout? = null
    private var macroLog: LinearLayout? = null
    private var activeMacroPreset: String? = null
    private val macros = mutableMapOf<Int, MacroConfig>()
    private val pendingMacros = mutableMapOf<String, PendingMacro>()
    private var capturePhoto: Bitmap? = null
    private var inventoryPhotoUri: Uri? = null
    private var capturePreview: ImageView? = null
    private var removePhotoButton: TextView? = null
    private var pendingSharedClipboardText: String? = null
    private var clipboardHistoryList: LinearLayout? = null
    private var telemetry: org.json.JSONObject? = null
    private val telemetryHistory = mutableListOf<TelemetrySample>()
    private var isAppForeground = false
    private var activeScreenSubscription: String? = null
    private var activeTouchpadSurface: TouchpadSurfaceView? = null
    private var lastConnectedAt = 0L
    private val homeModules = listOf(
        HomeModule("Capture", "Text, photo, and voice notes", Screen.CAPTURE, 2, R.drawable.ic_capture),
        HomeModule("Macros", "Trigger keypresses and shell commands", Screen.MACROS, 1, R.drawable.ic_macros),
        HomeModule("Touchpad", "Remote mouse, click, and scroll", Screen.TOUCHPAD, 1, R.drawable.ic_touchpad),
        HomeModule("Files", "Send and receive files instantly", Screen.FILES, 2, R.drawable.ic_files),
        HomeModule("Todo", "Shared checklist", Screen.TODO, 1, R.drawable.ic_todo),
        HomeModule("Clipboard", "Send copied text", null, 1, R.drawable.ic_clipboard, action = ::sendClipboardFromDevice),
        HomeModule("Telemetry", "Live CPU, RAM, and temperature", Screen.TELEMETRY, 2, R.drawable.ic_telemetry),
        HomeModule("News", "Headlines from your saved feeds", Screen.NEWS, 1, R.drawable.ic_news),
        HomeModule("Space", "Browse saved captures", Screen.SPACE, 1, R.drawable.ic_space),
        HomeModule("SchedWall", "One-off schedule overlays", Screen.SCHEDWALL, 2, R.drawable.ic_schedwall_clean),
        HomeModule("Inventory", "Food and medicine expiry tracker", Screen.INVENTORY, 1, R.drawable.ic_inventory),
        HomeModule("Goals", "Plan and connect your long-term goals", Screen.GOALS, 2, R.drawable.ic_focus),
        HomeModule("CGPA", "Semester grade tracker", Screen.CGPA, 1, R.drawable.ic_focus),
        HomeModule("Clipboard History", "Copied text from all devices", Screen.CLIPBOARD_HISTORY, 1, R.drawable.ic_clipboard),
    )

    private data class PendingMacro(val buttonId: Int, val label: String, val button: Button, val timeout: Runnable)
    private data class HomeModule(
        val title: String,
        val description: String,
        val screen: Screen?,
        val span: Int,
        val icon: Int,
        val action: (() -> Unit)? = null,
    )

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 41
        private const val CAMERA_CAPTURE_REQUEST = 42
        private const val FILE_PICK_REQUEST = 43
        private const val MICROPHONE_PERMISSION_REQUEST = 44
        private const val PHOTO_PICK_REQUEST = 45
        private const val INVENTORY_PHOTO_PICK_REQUEST = 46
        private const val PREFS_NAME = "vectr_connection"
        private const val PREF_HOST = "core_host"
        private const val PREF_PORT = "core_port"
        private const val PREF_MANUAL_OVERRIDE = "manual_override"
        private const val DEFAULT_PORT = 4101
        private const val PREF_PRODUCT_ONBOARDING_SEEN = "product_onboarding_seen"
        private const val FRESH_INSTALL_PREFS = "vectr_fresh_install"
        private const val FRESH_INSTALL_VERSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        resetLocalDataForFreshInstall()
        DeviceWebSocket.initializeDeviceId(this)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.screen_content)
        navButtons = emptyMap()

        DeviceWebSocket.observeConnection { connected ->
            runOnUiThread {
                isConnected = connected
                activeTouchpadSurface?.connected = connected
                if (connected) lastConnectedAt = System.currentTimeMillis()
                updateConnectionIndicator()
                updateScreenSubscription()
                if (connected) sendPendingSharedClipboard()
                if (connected) SchedWallOfflineQueue.flush(this)
                if (connected) TodoRepository.flush(this)
                if (connected) InventoryOfflineQueue.flush(this)
                if (connected) OfflineFileQueue.flush(this)
            }
        }
        DeviceWebSocket.observeMessages { type, payload ->
            runOnUiThread {
                when (type) {
                    "macro.result" -> handleMacroResult(payload)
                    "clipboard.update" -> if (payload.optString("source") == "laptop") receiveLaptopClipboard(payload.optString("text"))
                    "clipboard.history" -> if (currentScreen == Screen.CLIPBOARD_HISTORY) clipboardHistoryRefresh()
                    "telemetry.update" -> recordTelemetry(payload)
                    "todos.update" -> if (currentScreen == Screen.TODO) payload.optJSONArray("items")?.let { renderTodos(TodoRepository.parse(it)) }
                }
            }
        }
        OfflineCaptureQueue.observe(this) { count -> runOnUiThread { updatePendingSyncIndicator(count) } }
        currentScreen = savedInstanceState?.getString("screen")?.let { Screen.valueOf(it) } ?: Screen.HOME
        showScreen(currentScreen)
        startCoreConnection()
        handleShareIntent(intent)
    }

    private fun resetLocalDataForFreshInstall() {
        val marker = getSharedPreferences(FRESH_INSTALL_PREFS, MODE_PRIVATE)
        if (marker.getInt("version", 0) >= FRESH_INSTALL_VERSION) return
        listOf(PREFS_NAME, "vectr_offline_captures", "vectr_schedwall_queue", "vectr_local_sync", "vectr_device").forEach { name -> getSharedPreferences(name, MODE_PRIVATE).edit().clear().apply() }
        cacheDir.deleteRecursively()
        marker.edit().putInt("version", FRESH_INSTALL_VERSION).apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", currentScreen.name)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isAppForeground = true
        updateScreenSubscription()
    }

    override fun onPause() {
        isAppForeground = false
        updateScreenSubscription()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentScreen != Screen.HOME) showScreen(Screen.HOME) else super.onBackPressed()
    }

    private fun showScreen(screen: Screen) {
        clearScreenSubscription()
        currentScreen = screen
        requestedOrientation = if (screen == Screen.MACROS || screen == Screen.TOUCHPAD || screen == Screen.TELEMETRY) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        navButtons.forEach { (item, button) -> button.isSelected = item == screen }
        (content as android.view.ViewGroup).removeAllViews()
        connectionLabel = null
        connectionDot = null
        activeTouchpadSurface = null

        val layout = when (screen) {
            Screen.HOME -> R.layout.screen_home
            Screen.CAPTURE -> R.layout.screen_capture
            Screen.MACROS -> R.layout.screen_macros
            Screen.TOUCHPAD -> R.layout.screen_touchpad
            Screen.FILES -> R.layout.screen_files
            Screen.INVENTORY -> R.layout.screen_inventory
            Screen.TELEMETRY -> R.layout.screen_telemetry
            Screen.NEWS -> R.layout.screen_news
            Screen.TODO -> R.layout.screen_todo
            Screen.SPACE -> R.layout.screen_space
            Screen.SCHEDWALL -> R.layout.screen_schedwall
            Screen.GOALS -> R.layout.screen_goals
            Screen.CGPA -> R.layout.screen_cgpa
            Screen.CLIPBOARD_HISTORY -> R.layout.screen_clipboard
            Screen.SETTINGS -> R.layout.screen_settings
        }
        val screenView = LayoutInflater.from(this).inflate(layout, content as android.view.ViewGroup, false)
        (content as android.view.ViewGroup).addView(screenView)
        when (screen) {
            Screen.HOME -> bindHome(screenView)
            Screen.CAPTURE -> bindCapture(screenView)
            Screen.MACROS -> bindMacros(screenView)
            Screen.TOUCHPAD -> bindTouchpad(screenView)
            Screen.FILES -> bindFiles(screenView)
            Screen.INVENTORY -> bindInventory(screenView)
            Screen.TELEMETRY -> bindTelemetry(screenView)
            Screen.NEWS -> bindNews(screenView)
            Screen.TODO -> bindTodo(screenView)
            Screen.SPACE -> bindSpace(screenView)
            Screen.SCHEDWALL -> bindSchedWall(screenView)
            Screen.GOALS -> bindGoals(screenView)
            Screen.CGPA -> bindCgpa(screenView)
            Screen.CLIPBOARD_HISTORY -> bindClipboardHistory(screenView)
            Screen.SETTINGS -> bindSettings(screenView)
        }
        updateScreenSubscription()
    }

    private fun subscriptionFor(screen: Screen): String? = when (screen) {
        Screen.TELEMETRY -> "telemetry"
        Screen.MACROS -> "macro"
        Screen.TOUCHPAD -> "touchpad"
        else -> null
    }

    private fun clearScreenSubscription() {
        activeScreenSubscription?.let { DeviceWebSocket.sendScreenSubscription(it, subscribe = false) }
        activeScreenSubscription = null
    }

    private fun updateScreenSubscription() {
        val requestedSubscription = if (isAppForeground) subscriptionFor(currentScreen) else null
        if (activeScreenSubscription == requestedSubscription) return
        clearScreenSubscription()
        if (requestedSubscription != null && DeviceWebSocket.sendScreenSubscription(requestedSubscription, subscribe = true)) {
            activeScreenSubscription = requestedSubscription
        }
    }

    private fun bindHome(view: View) {
        view.findViewById<TextView>(R.id.home_wordmark).text = SpannableString("VeCTR").apply {
            setSpan(RelativeSizeSpan(.64f), 1, 2, 0)
            setSpan(SuperscriptSpan(), 1, 2, 0)
            setSpan(ForegroundColorSpan(getColor(R.color.accent_amber)), 2, 3, 0)
        }
        ObjectAnimator.ofFloat(view.findViewById(R.id.home_blink_dot), View.ALPHA, 1f, .15f, 1f).apply { duration = 900; repeatCount = ObjectAnimator.INFINITE; start() }
        connectionLabel = view.findViewById(R.id.connection_status_label)
        connectionDot = view.findViewById(R.id.connection_status_dot)
        view.findViewById<Button>(R.id.home_reconnect).setOnClickListener { VectrForegroundService.reconnect(this) }
        view.findViewById<Button>(R.id.home_edit).setOnClickListener { showScreen(Screen.SETTINGS) }
        val grid = view.findViewById<GridLayout>(R.id.home_module_grid)
        homeModules.forEachIndexed { index, module ->
            val isFirstSmallCard = module.span == 1 && homeModules.getOrNull(index - 1)?.span == 2
            grid.addView(homeModuleView(module), GridLayout.LayoutParams().apply {
                width = 0
                height = 118.dp
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, module.span, 1f)
                setMargins(0, 0, if (isFirstSmallCard) 16.dp else 0, 8.dp)
            })
        }
        val uptime = view.findViewById<TextView>(R.id.home_uptime); val seen = view.findViewById<TextView>(R.id.home_last_seen); val endpoint = view.findViewById<TextView>(R.id.home_endpoint)
        val update = object : Runnable { override fun run() { val elapsed = (System.currentTimeMillis() - lastConnectedAt).coerceAtLeast(0); uptime.text = "UPTIME ${formatElapsed(elapsed)}"; seen.text = if (lastConnectedAt == 0L) "Last seen never" else "Last seen ${formatElapsed(elapsed)} ago"; endpoint.text = connectionSource; view.postDelayed(this, 1000) } }; update.run()
        updateConnectionIndicator()
        updatePendingSyncIndicator(OfflineCaptureQueue.count(this))
        if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_PRODUCT_ONBOARDING_SEEN, false)) {
            view.post {
                AlertDialog.Builder(this)
                    .setTitle("Welcome to VeCTR")
                    .setMessage("Connect to your computer, then choose a module below.")
                    .setPositiveButton("Get started") { _, _ -> getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_PRODUCT_ONBOARDING_SEEN, true).apply() }
                    .show()
            }
        }
    }

    private fun homeModuleView(module: HomeModule) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_home_tile); setPadding(14.dp, 10.dp, 14.dp, 10.dp); isClickable = true; isFocusable = true; setOnClickListener { module.action?.invoke() ?: module.screen?.let(::showScreen) }
        addView(android.widget.FrameLayout(this@MainActivity).apply {
            background = getDrawable(R.drawable.bg_icon_button)
            layoutParams = LinearLayout.LayoutParams(34.dp, 34.dp)
            addView(ImageView(this@MainActivity).apply { setImageResource(module.icon); setColorFilter(getColor(R.color.accent_amber)); layoutParams = android.widget.FrameLayout.LayoutParams(19.dp, 19.dp, android.view.Gravity.CENTER) })
        })
        addView(TextView(this@MainActivity).apply { text = module.title; textSize = 14f; setTextColor(getColor(R.color.text_primary)); setPadding(0, 4.dp, 0, 0) })
        addView(TextView(this@MainActivity).apply { text = module.description; textSize = 10f; setTextColor(getColor(R.color.text_muted)); maxLines = 1; setPadding(0, 2.dp, 0, 0) })
    }
    private fun formatElapsed(millis: Long): String { val seconds = millis / 1000; return "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60) }
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
        pendingSharedClipboardText = text
        sendPendingSharedClipboard()
    }

    private fun sendPendingSharedClipboard() {
        val text = pendingSharedClipboardText ?: return
        if (!isConnected) return
        sendClipboardToCore(text) {
            if (pendingSharedClipboardText == text) pendingSharedClipboardText = null
        }
    }

    private fun sendClipboardToCore(text: String, onSent: (() -> Unit)? = null) {
        DeviceWebSocket.sendClipboardUpdate(
            text,
            onSent = { runOnUiThread {
                onSent?.invoke()
                Toast.makeText(this, R.string.clipboard_sent, Toast.LENGTH_SHORT).show()
            } },
            onError = { error -> runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show() } },
        )
    }

    private fun sendClipboardFromDevice() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (text == null) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        sendClipboardToCore(text)
    }

    private fun receiveLaptopClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(this, R.string.clipboard_received, Toast.LENGTH_SHORT).show()
    }

    private fun clipboardHistoryRefresh() {
        val container = clipboardHistoryList ?: return
        val client = OkHttpClient()
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/clipboard/history")).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { runOnUiThread { Toast.makeText(this@MainActivity, "Could not load clipboard history", Toast.LENGTH_SHORT).show() } }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.use {
                if (!it.isSuccessful) { runOnUiThread { Toast.makeText(this@MainActivity, "Could not load clipboard history (${it.code})", Toast.LENGTH_SHORT).show() }; return }
                val body = it.body?.string() ?: return
                runOnUiThread {
                    container.removeAllViews()
                    try {
                        val entries = org.json.JSONArray(body)
                        if (entries.length() == 0) {
                            container.addView(TextView(this@MainActivity).apply { text = "No clipboard entries yet."; setTextColor(getColor(R.color.text_muted)); textSize = 13f; gravity = android.view.Gravity.CENTER; setPadding(0, 24.dp, 0, 24.dp) })
                            return@runOnUiThread
                        }
                        for (i in 0 until entries.length()) {
                            val entry = entries.getJSONObject(i)
                            val entryText = entry.getString("text")
                            val entrySource = entry.getString("source")
                            val entryTime = entry.getString("timestamp")
                            val card = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                background = getDrawable(R.drawable.bg_macro_log)
                                setPadding(14.dp, 12.dp, 14.dp, 12.dp)
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8.dp) }
                                isClickable = true; isFocusable = true
                                setOnClickListener {
                                    val clip = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), entryText))
                                    Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                addView(LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    addView(TextView(this@MainActivity).apply {
                                        text = if (entrySource == "laptop") "LAPTOP" else "MOBILE"
                                        textSize = 10f
                                        typeface = android.graphics.Typeface.MONOSPACE
                                        setTextColor(getColor(if (entrySource == "laptop") R.color.accent_amber else R.color.failure_muted_red))
                                        setPadding(4.dp, 2.dp, 4.dp, 2.dp)
                                    })
                                    addView(TextView(this@MainActivity).apply {
                                        text = "  ·  $entryTime"
                                        textSize = 10f
                                        setTextColor(getColor(R.color.text_muted))
                                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                    })
                                })
                                addView(TextView(this@MainActivity).apply {
                                    text = entryText
                                    textSize = 13f
                                    setTextColor(getColor(R.color.text_primary))
                                    setPadding(0, 6.dp, 0, 0)
                                    maxLines = 4
                                })
                            }
                            container.addView(card)
                        }
                    } catch (e: Exception) {
                        container.addView(TextView(this@MainActivity).apply { text = "Could not parse clipboard history."; setTextColor(getColor(R.color.text_muted)); textSize = 13f; gravity = android.view.Gravity.CENTER; setPadding(0, 24.dp, 0, 24.dp) })
                    }
                }
            } }
        })
    }

    private fun bindClipboardHistory(view: View) {
        clipboardHistoryList = view.findViewById(R.id.clipboard_list)
        clipboardHistoryRefresh()
    }

    private fun bindSchedWall(view: View) {
        val date = view.findViewById<EditText>(R.id.schedwall_date)
        val start = view.findViewById<EditText>(R.id.schedwall_start)
        val end = view.findViewById<EditText>(R.id.schedwall_end)
        val title = view.findViewById<EditText>(R.id.schedwall_title)
        val status = view.findViewById<TextView>(R.id.schedwall_status)
        date.setText(java.time.LocalDate.now().toString()); start.setText("9"); end.setText("10")
        fun refresh() {
            if (!isConnected) {
                status.text = if (SchedWallOfflineQueue.count(this) > 0) "${SchedWallOfflineQueue.count(this)} event(s) waiting to sync" else "Offline — new overlay events will be queued."
                return
            }
            SchedWallRepository.fetchOverlays({ overlays -> runOnUiThread {
                val list = view.findViewById<LinearLayout>(R.id.schedwall_list); list.removeAllViews()
                overlays.sortedWith(compareBy<SchedWallOverlay> { it.date }.thenBy { it.start }).forEach { event ->
                    list.addView(TextView(this).apply { text = "${event.date}  ${event.start}:00–${event.end}:00  ${event.title}"; setTextColor(getColor(R.color.text_primary)); setPadding(0, 12.dp, 0, 12.dp) })
                }
                status.text = if (SchedWallOfflineQueue.count(this) > 0) "${SchedWallOfflineQueue.count(this)} event(s) waiting to sync" else ""
            } }, { error -> runOnUiThread { status.text = if (SchedWallOfflineQueue.count(this) > 0) "${SchedWallOfflineQueue.count(this)} event(s) waiting to sync" else error } })
        }
        view.findViewById<Button>(R.id.schedwall_add).setOnClickListener {
            val eventDate = date.text.toString().trim(); val eventStart = start.text.toString().toIntOrNull(); val eventEnd = end.text.toString().toIntOrNull(); val eventTitle = title.text.toString().trim()
            if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(eventDate) || eventDate < java.time.LocalDate.now().toString() || eventStart == null || eventEnd == null || eventStart !in 7..23 || eventEnd !in 8..24 || eventEnd <= eventStart || eventTitle.isBlank()) { status.text = "Enter a future date, valid hours, and a title."; return@setOnClickListener }
            if (!isConnected) { SchedWallOfflineQueue.enqueue(this, eventDate, eventStart, eventEnd, eventTitle); title.setText(""); status.text = "Saved offline — it will sync on reconnect."; return@setOnClickListener }
            SchedWallRepository.addOverlay(eventDate, eventStart, eventEnd, eventTitle, { runOnUiThread { title.setText(""); status.text = "Overlay event saved"; refresh() } }, { runOnUiThread { SchedWallOfflineQueue.enqueue(this, eventDate, eventStart, eventEnd, eventTitle); title.setText(""); status.text = "Saved offline — it will sync on reconnect." } })
        }
        refresh()
    }

    private fun bindSettings(view: View) {
        val host = view.findViewById<EditText>(R.id.core_host)
        val port = view.findViewById<EditText>(R.id.core_port)
        val saved = savedEndpoint()
        host.setText(saved?.host.orEmpty())
        port.setText((saved?.port ?: DEFAULT_PORT).toString())

        view.findViewById<Button>(R.id.save_core_endpoint).setOnClickListener {
            val enteredHost = host.text.toString().trim()
            val enteredPort = port.text.toString().toIntOrNull()
            if (enteredHost.isBlank() || enteredPort == null || enteredPort !in 1..65535) {
                Toast.makeText(this, R.string.invalid_core_endpoint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveEndpoint(DeviceWebSocket.Endpoint(enteredHost, enteredPort), manual = true)
            connectToEndpoint(DeviceWebSocket.Endpoint(enteredHost, enteredPort), "Manual")
            Toast.makeText(this, R.string.core_endpoint_saved, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.use_auto_discovery).setOnClickListener {
            // A running foreground service ignores a plain start() call. Clear any stale
            // address and explicitly send it through the discovery path.
            endpointPreferences.edit().remove(PREF_MANUAL_OVERRIDE).remove(PREF_HOST).remove(PREF_PORT).apply()
            connectionSource = "Background service discovering axon-core"
            updateConnectionIndicator()
            VectrForegroundService.reconnect(this)
        }
    }

    private fun startCoreConnection() {
        val saved = savedEndpoint()
        connectionSource = when {
            endpointPreferences.getBoolean(PREF_MANUAL_OVERRIDE, false) && saved != null -> "Manual · ${saved.host}:${saved.port}"
            saved != null -> "Background service · ${saved.host}:${saved.port}"
            else -> "Background service discovering axon-core"
        }
        updateConnectionIndicator()
        VectrForegroundService.start(this)
    }

    private fun connectToEndpoint(endpoint: DeviceWebSocket.Endpoint, source: String) {
        connectionSource = "$source · ${endpoint.host}:${endpoint.port}"
        DeviceWebSocket.setEndpoint(endpoint.host, endpoint.port)
        VectrForegroundService.reconnect(this)
        updateConnectionIndicator()
    }

    private fun saveEndpoint(endpoint: DeviceWebSocket.Endpoint, manual: Boolean) {
        endpointPreferences.edit()
            .putString(PREF_HOST, endpoint.host)
            .putInt(PREF_PORT, endpoint.port)
            .putBoolean(PREF_MANUAL_OVERRIDE, manual)
            .apply()
    }

    private fun savedEndpoint(): DeviceWebSocket.Endpoint? {
        val host = endpointPreferences.getString(PREF_HOST, null)?.trim().orEmpty()
        val port = endpointPreferences.getInt(PREF_PORT, DEFAULT_PORT)
        return if (host.isNotBlank() && port in 1..65535) DeviceWebSocket.Endpoint(host, port) else null
    }

    private val endpointPreferences: SharedPreferences
        get() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun bindCapture(view: View) {
        val headingText = view.findViewById<EditText>(R.id.capture_heading)
        val tagText = view.findViewById<AutoCompleteTextView>(R.id.capture_tag)
        val bodyText = view.findViewById<EditText>(R.id.capture_text)
        capturePreview = view.findViewById(R.id.capture_photo_preview)
        removePhotoButton = view.findViewById(R.id.remove_capture_photo)
        updatePhotoPreview()
        loadTagSuggestions(tagText)
        val mic = view.findViewById<ImageButton>(R.id.mic_button)
        val camera = view.findViewById<ImageButton>(R.id.camera_button)
        val gallery = view.findViewById<ImageButton>(R.id.gallery_button)
        val send = view.findViewById<View>(R.id.capture_submit)
        val recordingIndicator = view.findViewById<TextView>(R.id.voice_recording_indicator)
        val recordingWave = view.findViewById<LinearLayout>(R.id.recording_wave)
        var recorder: VoiceRecorder? = null
        var startedAt = 0L
        val pulse = AnimatorSet().apply { playTogether((0 until recordingWave.childCount).map { index -> ObjectAnimator.ofFloat(recordingWave.getChildAt(index), View.SCALE_Y, .45f, 1.35f, .45f).apply { duration = 520; startDelay = index * 90L; repeatCount = ObjectAnimator.INFINITE } }) }
        lateinit var ticker: Runnable
        ticker = Runnable {
            if (recorder != null) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                recordingIndicator.text = "● Recording ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                recordingIndicator.postDelayed(ticker, 500)
            }
        }

        val lectureToggle = view.findViewById<CheckBox>(R.id.lecture_mode_toggle)
        val lectureSubject = view.findViewById<AutoCompleteTextView>(R.id.lecture_subject)
        val lectureDate = view.findViewById<EditText>(R.id.lecture_date)
        lectureToggle.setOnCheckedChangeListener { _, isChecked ->
            lectureSubject.visibility = if (isChecked) View.VISIBLE else View.GONE
            lectureDate.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                val today = java.time.LocalDate.now().toString()
                lectureDate.setText(today)
                LectureRepository.fetchSubjects({ subjects ->
                    if (subjects.isNotEmpty()) {
                        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, subjects.map { it.slug.replace("-", " ").replaceFirstChar { c -> c.uppercaseChar() } })
                        lectureSubject.setAdapter(adapter)
                        lectureSubject.threshold = 0
                    }
                }, {})
            }
        }

        mic.setOnClickListener {
            if (recorder == null) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
                    return@setOnClickListener
                }
                try {
                    val file = File(filesDir, "voice-recordings/${System.currentTimeMillis()}.wav")
                    recorder = VoiceRecorder(file).also { it.start() }
                    startedAt = System.currentTimeMillis()
                    mic.isSelected = true
                    mic.contentDescription = getString(R.string.voice_input_on)
                    recordingIndicator.visibility = View.VISIBLE
                    recordingWave.visibility = View.VISIBLE
                    camera.visibility = View.GONE
                    gallery.visibility = View.GONE
                    send.visibility = View.GONE
                    pulse.start()
                    ticker.run()
                } catch (error: Exception) { Toast.makeText(this, error.message ?: "Could not start recording", Toast.LENGTH_LONG).show() }
            } else {
                val activeRecorder = recorder ?: return@setOnClickListener
                recorder = null
                recordingIndicator.removeCallbacks(ticker)
                recordingIndicator.visibility = View.GONE
                recordingWave.visibility = View.GONE
                pulse.cancel()
                mic.isSelected = false
                mic.contentDescription = getString(R.string.voice_input)
                camera.visibility = View.VISIBLE
                gallery.visibility = View.VISIBLE
                send.visibility = View.VISIBLE
                try {
                    val voiceFile = activeRecorder.stop()
                    if (!DeviceWebSocket.isConnected()) {
                        OfflineCaptureQueue.enqueue(this, "Voice ${DateFormat.getDateTimeInstance().format(Date())}", "voice", "", voicePath = voiceFile.absolutePath)
                        Toast.makeText(this, R.string.capture_queued, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    recordingIndicator.visibility = View.VISIBLE
                    recordingIndicator.alpha = 1f

                    if (lectureToggle.isChecked) {
                        val subject = lectureSubject.text.toString().trim().lowercase().replace(" ", "-")
                        val date = lectureDate.text.toString().trim().ifBlank { java.time.LocalDate.now().toString() }
                        if (subject.isBlank()) {
                            recordingIndicator.visibility = View.GONE
                            Toast.makeText(this, "Enter a subject for lecture mode", Toast.LENGTH_SHORT).show()
                            voiceFile.delete()
                            return@setOnClickListener
                        }
                        recordingIndicator.text = "Transcribing lecture…"
                        CaptureRepository.uploadLectureVoice(voiceFile, subject = subject, date = date,
                            onSuccess = { result -> runOnUiThread {
                                voiceFile.delete()
                                recordingIndicator.visibility = View.GONE
                                Toast.makeText(this, "Lecture transcribed and saved to ${result.lectureSubject}", Toast.LENGTH_LONG).show()
                            } },
                            onError = { error -> runOnUiThread {
                                recordingIndicator.visibility = View.GONE
                                OfflineCaptureQueue.enqueue(this, "Lecture $subject $date", "lecture", "", voicePath = voiceFile.absolutePath)
                                Toast.makeText(this, "$error. Recording queued for retry.", Toast.LENGTH_LONG).show()
                            } },
                        )
                    } else {
                        recordingIndicator.text = "Transcribing locally…"
                        CaptureRepository.uploadVoiceFile(voiceFile, saveCapture = false,
                            onSuccess = { result -> runOnUiThread {
                                voiceFile.delete()
                                recordingIndicator.visibility = View.GONE
                                if (headingText.text.isBlank()) headingText.setText(result.heading)
                                tagText.setText("voice", false)
                                bodyText.setText(result.transcript)
                                Toast.makeText(this, "Voice capture transcribed", Toast.LENGTH_SHORT).show()
                            } },
                            onError = { error -> runOnUiThread {
                                recordingIndicator.visibility = View.GONE
                                OfflineCaptureQueue.enqueue(this, "Voice ${DateFormat.getDateTimeInstance().format(Date())}", "voice", "", voicePath = voiceFile.absolutePath)
                                Toast.makeText(this, "$error. Recording queued for retry.", Toast.LENGTH_LONG).show()
                            } },
                        )
                    }
                } catch (error: Exception) { Toast.makeText(this, error.message ?: "Could not stop recording", Toast.LENGTH_LONG).show() }
            }
        }
        view.findViewById<ImageButton>(R.id.camera_button).setOnClickListener { requestCameraCapture() }
        gallery.setOnClickListener { openPhotoAlbum() }
        removePhotoButton?.setOnClickListener {
            capturePhoto = null
            updatePhotoPreview()
        }
        view.findViewById<View>(R.id.capture_submit).setOnClickListener {
            val heading = headingText.text.toString()
            val tag = tagText.text.toString()
            val body = bodyText.text.toString()
            if (heading.isBlank() || tag.isBlank() || body.isBlank()) {
                Toast.makeText(this, R.string.capture_details_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val submit = view.findViewById<View>(R.id.capture_submit)
            if (!DeviceWebSocket.isConnected()) {
                OfflineCaptureQueue.enqueue(this, heading, tag, body, capturePhoto)
                headingText.text.clear()
                tagText.text.clear()
                bodyText.text.clear()
                capturePhoto = null
                updatePhotoPreview()
                Toast.makeText(this, R.string.capture_queued, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            submit.isEnabled = false
            submit.alpha = 0.6f
            fun sendCapture(imageFilename: String? = null) {
                DeviceWebSocket.sendDeviceCapture(
                    heading = heading,
                    tag = tag,
                    body = body,
                    imageFilename = imageFilename,
                    onSent = { runOnUiThread {
                        headingText.text.clear()
                        tagText.text.clear()
                        bodyText.text.clear()
                        capturePhoto = null
                        updatePhotoPreview()
                        submit.isEnabled = true
                        submit.alpha = 1f
                        Toast.makeText(this, R.string.capture_sent, Toast.LENGTH_SHORT).show()
                    } },
                    onError = { error -> runOnUiThread {
                        submit.isEnabled = true
                        submit.alpha = 1f
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    } },
                )
            }
            val photo = capturePhoto
            if (photo == null) sendCapture()
            else CaptureRepository.uploadImage(
                photo,
                onSuccess = { filename -> runOnUiThread { sendCapture(filename) } },
                onError = { error -> runOnUiThread {
                    submit.isEnabled = true
                    submit.alpha = 1f
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } },
            )
        }
    }

    private fun requestCameraCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.camera_permission_title)
                .setMessage(R.string.camera_permission_rationale)
                .setPositiveButton(R.string.allow) { _, _ -> requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(intent, CAMERA_CAPTURE_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) launchCamera()
            else Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
        if (requestCode == MICROPHONE_PERMISSION_REQUEST && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.microphone_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_CAPTURE_REQUEST && resultCode == RESULT_OK) {
            capturePhoto = data?.extras?.get("data") as? Bitmap
            if (capturePhoto == null) Toast.makeText(this, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
            else updatePhotoPreview()
        }
        if (requestCode == FILE_PICK_REQUEST && resultCode == RESULT_OK) {
            val selected = buildList {
                data?.data?.let(::add)
                data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) add(clip.getItemAt(index).uri)
                }
            }.distinct()
            if (selected.isNotEmpty()) uploadSelectedFiles(selected)
        }
        if (requestCode == PHOTO_PICK_REQUEST && resultCode == RESULT_OK) {
            capturePhoto = data?.data?.let { uri -> contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }
            if (capturePhoto == null) Toast.makeText(this, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
            else updatePhotoPreview()
        }
        if (requestCode == INVENTORY_PHOTO_PICK_REQUEST && resultCode == RESULT_OK) {
            inventoryPhotoUri = data?.data
            val preview = content.findViewById<ImageView>(R.id.inventory_photo_preview)
            val bitmap = inventoryPhotoUri?.let { uri -> contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }
            if (bitmap == null) Toast.makeText(this, "Could not read the selected photo", Toast.LENGTH_SHORT).show()
            else preview?.apply { setImageBitmap(bitmap); visibility = View.VISIBLE }
        }
    }

    private fun openPhotoAlbum() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, PHOTO_PICK_REQUEST)
    }

    private fun bindFiles(view: View) {
        val upload = view.findViewById<Button>(R.id.files_upload)
        upload.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, FILE_PICK_REQUEST)
        }
        loadFiles(view)
    }

    private fun bindInventory(view: View) {
        val name = view.findViewById<EditText>(R.id.inventory_name)
        val quantity = view.findViewById<EditText>(R.id.inventory_quantity)
        val mfg = view.findViewById<EditText>(R.id.inventory_mfg)
        val expiry = view.findViewById<EditText>(R.id.inventory_expiry)
        val status = view.findViewById<TextView>(R.id.inventory_status)
        val save = view.findViewById<Button>(R.id.inventory_save)
        fun refresh() = InventoryRepository.list({ items -> runOnUiThread { if (currentScreen == Screen.INVENTORY) renderInventory(view, items) } }, { error -> runOnUiThread { status.text = error } })
        view.findViewById<Button>(R.id.inventory_photo).setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, INVENTORY_PHOTO_PICK_REQUEST)
        }
        save.setOnClickListener {
            val title = name.text.toString().trim(); val count = quantity.text.toString().toIntOrNull(); val manufacture = mfg.text.toString().trim(); val expires = expiry.text.toString().trim()
            if (title.isBlank() || count == null || count < 1 || !isInventoryDate(manufacture) || !isInventoryDate(expires)) { status.text = "Enter a name, quantity, and dates in YYYY-MM-DD format."; return@setOnClickListener }
            if (expires < manufacture) { status.text = "Expiry date must be after manufacture date."; return@setOnClickListener }
            if (!DeviceWebSocket.isConnected()) {
                val item = InventoryOfflineQueue.enqueue(this, title, count, manufacture, expires, inventoryPhotoUri)
                name.text.clear(); quantity.text.clear(); mfg.text.clear(); expiry.text.clear(); inventoryPhotoUri = null; view.findViewById<ImageView>(R.id.inventory_photo_preview).visibility = View.GONE
                renderInventory(view, InventoryOfflineQueue.cached(this)); status.text = "Saved on this phone — it will sync when reconnected."; return@setOnClickListener
            }
            save.isEnabled = false; save.text = "SAVING…"; status.text = "Saving to your inventory…"
            InventoryRepository.add(contentResolver, title, count, manufacture, expires, inventoryPhotoUri, { _ -> runOnUiThread {
                name.text.clear(); quantity.text.clear(); mfg.text.clear(); expiry.text.clear(); inventoryPhotoUri = null; view.findViewById<ImageView>(R.id.inventory_photo_preview).visibility = View.GONE
                save.isEnabled = true; save.text = "SAVE ITEM"; status.text = "Item saved"; refresh()
            } }, { error -> runOnUiThread { save.isEnabled = true; save.text = "SAVE ITEM"; status.text = error } })
        }
        refresh()
    }

    private fun isInventoryDate(value: String) = runCatching { java.time.LocalDate.parse(value) }.isSuccess
    private fun renderInventory(view: View, items: List<InventoryItem>) {
        val list = view.findViewById<LinearLayout>(R.id.inventory_list); list.removeAllViews()
        if (items.isEmpty()) { list.addView(TextView(this).apply { text = "No items yet. Add your first food or medicine above."; textSize = 13f; setTextColor(getColor(R.color.text_muted)); setPadding(4.dp, 14.dp, 4.dp, 14.dp) }); return }
        items.forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; background = getDrawable(R.drawable.bg_home_tile); setPadding(12.dp, 12.dp, 10.dp, 12.dp); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8.dp) } }
            val image = ImageView(this).apply { setImageResource(R.drawable.ic_files); setColorFilter(getColor(R.color.accent_amber)); scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = LinearLayout.LayoutParams(54.dp, 54.dp) }
            item.photoUrl?.let { url -> InventoryRepository.photo(url, { bitmap -> runOnUiThread { image.setImageBitmap(bitmap); image.clearColorFilter() } }, { }) }
            row.addView(image)
            row.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12.dp }
                addView(TextView(this@MainActivity).apply { text = item.name; textSize = 15f; setTextColor(getColor(R.color.text_primary)) })
                addView(TextView(this@MainActivity).apply { text = "QTY ${item.quantity}  ·  MFG ${item.manufactureDate}  ·  EXP ${item.expiryDate}"; textSize = 10f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 4.dp, 0, 0) })
                val (message, color) = inventoryExpiry(item.expiryDate)
                addView(TextView(this@MainActivity).apply { text = message; textSize = 10f; setTextColor(getColor(color)); setPadding(0, 5.dp, 0, 0) })
            })
            row.addView(Button(this).apply { text = "×"; textSize = 18f; setTextColor(getColor(R.color.failure_muted_red)); setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("Remove ${item.name}?").setMessage("This removes the item and its photo.").setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ -> InventoryRepository.delete(item.id, { runOnUiThread { renderInventory(view, items.filterNot { it.id == item.id }) } }, { error -> runOnUiThread { view.findViewById<TextView>(R.id.inventory_status).text = error } }) }.show() } })
            list.addView(row)
        }
    }
    private fun inventoryExpiry(date: String): Pair<String, Int> {
        val days = runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(date)) }.getOrDefault(0)
        return when { days < 0 -> "EXPIRED ${-days} DAY${if (days == -1L) "" else "S"} AGO" to R.color.failure_muted_red; days <= 30 -> "EXPIRES IN $days DAY${if (days == 1L) "" else "S"}" to R.color.accent_amber; else -> "GOOD UNTIL $date" to R.color.ok }
    }

    private fun bindTelemetry(view: View) {
        renderTelemetry()
    }

    private fun bindNews(view: View) {
        val refresh = view.findViewById<SwipeRefreshLayout>(R.id.news_refresh)
        val load = { loadNews(view, refresh) }
        refresh.setOnRefreshListener(load)
        load()
    }

    private fun bindTodo(view: View) {
        val input = view.findViewById<EditText>(R.id.todo_input); val list = view.findViewById<LinearLayout>(R.id.todo_list); val status = view.findViewById<TextView>(R.id.todo_status)
        fun load() = TodoRepository.fetch({ items -> runOnUiThread { if (currentScreen == Screen.TODO) renderTodos(items) } }, { error -> runOnUiThread { status.text = error } })
        view.findViewById<Button>(R.id.todo_add).setOnClickListener { val text = input.text.toString().trim(); if (text.isNotBlank()) TodoRepository.add(text, { items -> runOnUiThread { input.text.clear(); renderTodos(items) } }, { error -> runOnUiThread { status.text = error } }) }
        view.findViewById<Button>(R.id.todo_clear).setOnClickListener { TodoRepository.clear({ items -> runOnUiThread { renderTodos(items) } }, { error -> runOnUiThread { status.text = error } }) }
        load()
    }

    private fun bindGoals(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.goals_container)
        fun render() {
            val goals = GoalsRepository.all(this)
            container.removeAllViews()
            GoalPeriod.entries.forEach { period ->
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_home_tile); setPadding(18.dp, 16.dp, 18.dp, 14.dp)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 230.dp).apply { setMargins(0, 0, 0, 12.dp) }
                }
                panel.addView(TextView(this).apply { text = "${period.label.uppercase()} GOALS"; textSize = 18f; letterSpacing = 0.08f; setTextColor(getColor(R.color.accent_amber)); typeface = android.graphics.Typeface.MONOSPACE })
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp, 0, 0) }
                val periodGoals = goals.filter { it.period == period }
                if (periodGoals.isEmpty()) list.addView(TextView(this).apply { text = "No goals yet"; textSize = 15f; setTextColor(getColor(R.color.text_muted)); setPadding(4.dp, 16.dp, 4.dp, 16.dp) })
                periodGoals.forEach { goal ->
                    val parent = goals.firstOrNull { it.id == goal.parentId }
                    list.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_macro_log); isClickable = true; setPadding(14.dp, 12.dp, 14.dp, 12.dp)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8.dp) }
                        setOnClickListener { GoalsRepository.toggle(this@MainActivity, goal.id); render() }
                        addView(TextView(this@MainActivity).apply { text = if (goal.checked) "✓  ${goal.title}" else "○  ${goal.title}"; textSize = 18f; setTextColor(getColor(if (goal.checked) R.color.ok else R.color.text_primary)) })
                        parent?.let { addView(TextView(this@MainActivity).apply { text = "Linked to: ${it.period.label} · ${it.title}"; textSize = 13f; setTextColor(getColor(R.color.text_muted)); setPadding(28.dp, 6.dp, 0, 0) }) }
                    })
                }
                panel.addView(android.widget.ScrollView(this).apply {
                    isFillViewport = true
                    isNestedScrollingEnabled = true
                    // The screen itself scrolls too. Keep a drag that begins in this list
                    // inside the timeframe panel rather than handing it to the outer page.
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    addView(list)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                })
                container.addView(panel)
            }
        }
        view.findViewById<Button>(R.id.goals_add).setOnClickListener {
            val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 0) }
            val title = EditText(this).apply { hint = "Goal title" }
            val period = android.widget.Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, GoalPeriod.entries.map { it.label }) }
            val existing = GoalsRepository.all(this)
            val parentChoices = listOf("No parent goal") + existing.map { "${it.period.label}: ${it.title}" }
            val parent = android.widget.Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, parentChoices) }
            form.addView(title); form.addView(TextView(this).apply { text = "TIMEFRAME"; setPadding(0, 18, 0, 0) }); form.addView(period)
            form.addView(TextView(this).apply { text = "LINK TO A PARENT GOAL (OPTIONAL)"; setPadding(0, 18, 0, 0) }); form.addView(parent)
            AlertDialog.Builder(this).setTitle("Add goal").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Add") { _, _ ->
                val value = title.text.toString().trim()
                if (value.isNotBlank()) {
                    val parentId = parent.selectedItemPosition.takeIf { it > 0 }?.let { existing[it - 1].id }
                    GoalsRepository.add(this, value, GoalPeriod.entries[period.selectedItemPosition], parentId); render()
                }
            }.show()
        }
        render()
    }

    private fun bindCgpa(view: View) {
        val semestersContainer = view.findViewById<LinearLayout>(R.id.cgpa_semesters)
        val cgpaDisplay = view.findViewById<TextView>(R.id.cgpa_display)
        val cgpaSubtitle = view.findViewById<TextView>(R.id.cgpa_subtitle)
        val semesters = CgpaRepository.load(this).toMutableList()

        lateinit var showAddCourseDialog: (Semester) -> Unit
        lateinit var reRender: () -> Unit

        reRender = {
            semestersContainer.removeAllViews()
            semesters.forEachIndexed { index, semester ->
                val semView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 4.dp) }
                }
                val divider = TextView(this).apply {
                    text = "──────── ${semester.name} ────────"
                    gravity = android.view.Gravity.CENTER
                    setTextColor(getColor(R.color.accent_amber))
                    textSize = 13f
                    typeface = android.graphics.Typeface.MONOSPACE
                    letterSpacing = 0.08f
                    setPadding(0, 8.dp, 0, 8.dp)
                }
                semView.addView(divider)

                semester.courses.forEach { course ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        background = getDrawable(R.drawable.bg_macro_log)
                        setPadding(12.dp, 10.dp, 8.dp, 10.dp)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 6.dp) }
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            addView(TextView(this@MainActivity).apply {
                                text = "${course.courseId} — ${course.courseName}"
                                textSize = 14f
                                setTextColor(getColor(R.color.text_primary))
                            })
                            addView(TextView(this@MainActivity).apply {
                                text = "${course.credits} cr · ${course.grade}"
                                textSize = 11f
                                setTextColor(getColor(R.color.text_muted))
                                setPadding(0, 3.dp, 0, 0)
                            })
                        })
                        addView(Button(this@MainActivity).apply {
                            text = "×"
                            textSize = 16f
                            setTextColor(getColor(R.color.failure_muted_red))
                            setOnClickListener {
                                CgpaRepository.deleteCourse(this@MainActivity, semesters, semester.name, course.id)
                                reRender()
                            }
                        })
                    }
                    semView.addView(row)
                }

                val addBtn = Button(this).apply {
                    text = "+ ADD COURSE"
                    textSize = 11f
                    letterSpacing = 0.08f
                    setTextColor(getColor(R.color.accent_amber))
                    background = getDrawable(R.drawable.bg_macro_log)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 42.dp).apply { setMargins(0, 0, 0, 12.dp) }
                    setOnClickListener { showAddCourseDialog(semester) }
                }
                semView.addView(addBtn)
                semestersContainer.addView(semView)
            }

            val (cgpa, totalCredits) = CgpaRepository.calculateCgpa(semesters)
            val totalCourses = CgpaRepository.totalCourses(semesters)
            cgpaDisplay.text = String.format("%.2f", cgpa)
            cgpaSubtitle.text = "$totalCredits credits · $totalCourses courses"
        }

        showAddCourseDialog = { semester ->
            val form = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48.dp, 12.dp, 48.dp, 0)
            }
            val courseIdInput = EditText(this).apply { hint = "Course ID (e.g. CS101)" }
            val courseNameInput = EditText(this).apply { hint = "Course name" }
            val creditsInput = EditText(this).apply {
                hint = "Credits"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            val gradeSpinner = android.widget.Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, CgpaRepository.GRADE_ORDER)
            }

            form.addView(courseIdInput)
            form.addView(TextView(this).apply { text = "COURSE NAME"; setPadding(0, 14.dp, 0, 0); textSize = 11f; setTextColor(getColor(R.color.text_muted)) })
            form.addView(courseNameInput)
            form.addView(TextView(this).apply { text = "CREDITS"; setPadding(0, 14.dp, 0, 0); textSize = 11f; setTextColor(getColor(R.color.text_muted)) })
            form.addView(creditsInput)
            form.addView(TextView(this).apply { text = "GRADE"; setPadding(0, 14.dp, 0, 0); textSize = 11f; setTextColor(getColor(R.color.text_muted)) })
            form.addView(gradeSpinner)

            AlertDialog.Builder(this)
                .setTitle("Add course — ${semester.name}")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add") { _, _ ->
                    val id = courseIdInput.text.toString().trim()
                    val name = courseNameInput.text.toString().trim()
                    val credits = creditsInput.text.toString().toIntOrNull()
                    val grade = CgpaRepository.GRADE_ORDER.getOrNull(gradeSpinner.selectedItemPosition) ?: ""
                    if (id.isBlank() || name.isBlank() || credits == null || credits <= 0 || grade.isBlank()) {
                        Toast.makeText(this@MainActivity, "Fill all fields correctly.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val course = Course(
                        id = java.util.UUID.randomUUID().toString(),
                        courseId = id,
                        courseName = name,
                        credits = credits,
                        grade = grade,
                    )
                    CgpaRepository.addCourse(this@MainActivity, semesters, semester.name, course)
                    reRender()
                }
                .show()
        }

        reRender()
    }

    private fun bindSpace(view: View) {
        val filter = view.findViewById<AutoCompleteTextView>(R.id.space_filter); val status = view.findViewById<TextView>(R.id.space_status)
        val notesTab = view.findViewById<TextView>(R.id.space_notes_tab)
        val lecturesTab = view.findViewById<TextView>(R.id.space_lectures_tab)
        var allNotes = emptyList<SpaceNote>()
        var allSubjects = emptyList<LectureSubject>()

        fun renderNotes(notes: List<SpaceNote>) {
            val list = view.findViewById<LinearLayout>(R.id.space_list); list.removeAllViews(); status.text = if (notes.isEmpty()) "No notes found." else ""
            notes.forEach { note ->
                list.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_macro_log); setPadding(14, 12, 14, 12); isClickable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) }; setOnClickListener { openSpaceNote(note.filename) }
                    addView(TextView(this@MainActivity).apply { text = note.heading; textSize = 16f; setTextColor(getColor(R.color.text_primary)) }); addView(TextView(this@MainActivity).apply { text = "${note.tag} · ${relativeTime(note.timestamp)}"; textSize = 12f; setTextColor(getColor(R.color.accent_amber)); setPadding(0, 4, 0, 0) }); addView(TextView(this@MainActivity).apply { text = note.preview; textSize = 12f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 6, 0, 0) })
                })
            }
        }

        fun renderLectures() {
            val list = view.findViewById<LinearLayout>(R.id.space_list); list.removeAllViews()
            filter.visibility = View.GONE
            status.text = if (allSubjects.isEmpty()) "No lectures yet. Record one from Capture." else ""
            allSubjects.forEach { subject ->
                list.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_macro_log); setPadding(14, 14, 14, 14); isClickable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) }; setOnClickListener { openSubjectLectures(subject.slug) }
                    addView(TextView(this@MainActivity).apply { text = subject.slug.replace("-", " ").replaceFirstChar { c -> c.uppercaseChar() }; textSize = 17f; setTextColor(getColor(R.color.text_primary)) })
                    addView(TextView(this@MainActivity).apply { text = "${subject.count} lecture${if (subject.count == 1) "" else "s"}"; textSize = 12f; setTextColor(getColor(R.color.accent_amber)); setPadding(0, 4, 0, 0) })
                })
            }
        }

        fun renderNotesMode() {
            filter.visibility = View.VISIBLE
            notesTab.setBackgroundResource(R.drawable.bg_action); notesTab.setTextColor(getColor(R.color.text_primary))
            lecturesTab.setBackgroundResource(R.drawable.bg_input); lecturesTab.setTextColor(getColor(R.color.text_muted))
            CaptureRepository.fetchTags({ tags -> runOnUiThread { filter.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf("All") + tags)); filter.setText("All", false) } }, { })
            SpaceRepository.fetchNotes({ notes -> runOnUiThread { if (currentScreen == Screen.SPACE) { allNotes = notes; renderNotes(notes) } } }, { error -> runOnUiThread { status.text = error } })
        }

        fun renderLecturesMode() {
            filter.visibility = View.GONE
            lecturesTab.setBackgroundResource(R.drawable.bg_action); lecturesTab.setTextColor(getColor(R.color.text_primary))
            notesTab.setBackgroundResource(R.drawable.bg_input); notesTab.setTextColor(getColor(R.color.text_muted))
            LectureRepository.fetchSubjects({ subjects -> runOnUiThread { if (currentScreen == Screen.SPACE) { allSubjects = subjects; renderLectures() } } }, { error -> runOnUiThread { status.text = error } })
        }

        filter.setOnItemClickListener { _, _, _, _ ->
            val selectedTag = filter.text.toString()
            renderNotes(if (selectedTag == "All" || selectedTag.isBlank()) allNotes else allNotes.filter { it.tag == selectedTag })
        }
        filter.setOnClickListener { if (filter.adapter?.count ?: 0 > 0) filter.showDropDown() }
        notesTab.setOnClickListener { renderNotesMode() }
        lecturesTab.setOnClickListener { renderLecturesMode() }
        renderNotesMode()
    }

    private fun openSubjectLectures(subjectSlug: String) {
        (content as ViewGroup).removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.screen_space, content as ViewGroup, false)
        (content as ViewGroup).addView(view)
        view.findViewById<TextView>(R.id.space_notes_tab).visibility = View.GONE
        view.findViewById<TextView>(R.id.space_lectures_tab).visibility = View.GONE
        view.findViewById<AutoCompleteTextView>(R.id.space_filter).visibility = View.GONE
        val status = view.findViewById<TextView>(R.id.space_status)
        val list = view.findViewById<LinearLayout>(R.id.space_list)
        val header = TextView(this).apply { text = subjectSlug.replace("-", " ").replaceFirstChar { c -> c.uppercaseChar() }; textSize = 17f; setTextColor(getColor(R.color.text_primary)); setPadding(0, 8, 0, 12) }
        list.addView(header)
        LectureRepository.fetchLectures(subjectSlug, { lectures -> runOnUiThread {
            if (lectures.isEmpty()) { status.text = "No lectures yet."; return@runOnUiThread }
            lectures.forEach { lecture ->
                list.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_macro_log); setPadding(14, 12, 14, 12); isClickable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) }; setOnClickListener { openLectureDetail(subjectSlug, lecture.filename) }
                    addView(TextView(this@MainActivity).apply { text = lecture.title; textSize = 15f; setTextColor(getColor(R.color.text_primary)) })
                    addView(TextView(this@MainActivity).apply { text = "${lecture.date} · ${lecture.time}"; textSize = 12f; setTextColor(getColor(R.color.accent_amber)); setPadding(0, 4, 0, 0) })
                    addView(TextView(this@MainActivity).apply { text = lecture.preview; textSize = 12f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 6, 0, 0) })
                })
            }
        } }, { error -> runOnUiThread { status.text = error } })
    }

    private fun openLectureDetail(subjectSlug: String, filename: String) {
        (content as ViewGroup).removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.screen_lecture_detail, content as ViewGroup, false)
        (content as ViewGroup).addView(view)
        val subjectText = view.findViewById<TextView>(R.id.lecture_detail_subject)
        val titleText = view.findViewById<TextView>(R.id.lecture_detail_title)
        val metaText = view.findViewById<TextView>(R.id.lecture_detail_meta)
        val bodyText = view.findViewById<TextView>(R.id.lecture_detail_body)
        subjectText.text = subjectSlug.replace("-", " ").replaceFirstChar { c -> c.uppercaseChar() }
        view.findViewById<Button>(R.id.lecture_detail_back).setOnClickListener { showScreen(Screen.SPACE) }
        LectureRepository.fetchLecture(subjectSlug, filename, { lecture -> runOnUiThread {
            titleText.text = lecture.title
            metaText.text = "${lecture.date} · ${lecture.time}"
            bodyText.text = lecture.transcript
        } }, { error -> runOnUiThread { bodyText.text = "Could not load lecture: $error" } })
    }

    private fun openSpaceNote(filename: String) {
        (content as ViewGroup).removeAllViews(); val view = LayoutInflater.from(this).inflate(R.layout.screen_space_detail, content as ViewGroup, false); (content as ViewGroup).addView(view)
        view.findViewById<Button>(R.id.space_detail_delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete note?")
                .setMessage("This permanently removes the note and its attached image.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    SpaceRepository.deleteNote(filename, { runOnUiThread { Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show(); showScreen(Screen.SPACE) } }, { error -> runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show() } })
                }
                .show()
        }
        SpaceRepository.fetchNote(filename, { note -> runOnUiThread { view.findViewById<TextView>(R.id.space_detail_heading).text = note.heading; view.findViewById<TextView>(R.id.space_detail_meta).text = "${note.tag} · ${relativeTime(note.timestamp)}"; view.findViewById<TextView>(R.id.space_detail_body).text = note.body; note.imageFilename?.let { image -> SpaceRepository.fetchImage(image, { bitmap -> runOnUiThread { view.findViewById<ImageView>(R.id.space_detail_image).apply { setImageBitmap(bitmap); visibility = View.VISIBLE } } }, { }) } } }, { error -> runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show(); showScreen(Screen.SPACE) } })
    }

    private fun renderTodos(items: List<TodoItem>) {
        if (currentScreen != Screen.TODO) return
        val list = content.findViewById<LinearLayout>(R.id.todo_list) ?: return
        list.removeAllViews()
        if (items.isEmpty()) { list.addView(TextView(this).apply { text = "Nothing to do."; setTextColor(getColor(R.color.text_muted)); setPadding(0, 16, 0, 0) }); return }
        items.forEach { todo ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; background = getDrawable(R.drawable.bg_macro_log); setPadding(10, 8, 8, 8); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 0) } }
            row.addView(android.widget.CheckBox(this).apply { isChecked = todo.checked; setOnCheckedChangeListener { _, checked -> TodoRepository.update(todo.id, checked, { next -> runOnUiThread { renderTodos(next) } }, { }) } })
            row.addView(TextView(this).apply { text = todo.text; setTextColor(getColor(if (todo.checked) R.color.text_muted else R.color.text_primary)); textSize = 15f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(Button(this).apply { text = "×"; textSize = 18f; setOnClickListener { TodoRepository.delete(todo.id, { next -> runOnUiThread { renderTodos(next) } }, { }) } })
            list.addView(row)
        }
    }

    private fun loadNews(view: View, refresh: SwipeRefreshLayout) {
        val list = view.findViewById<LinearLayout>(R.id.news_list)
        val status = view.findViewById<TextView>(R.id.news_status)
        if (!refresh.isRefreshing) status.text = getString(R.string.news_loading)
        NewsRepository.fetch(
            onSuccess = { entries -> runOnUiThread {
                if (currentScreen != Screen.NEWS) return@runOnUiThread
                refresh.isRefreshing = false
                list.removeAllViews()
                status.text = if (entries.isEmpty()) "No news found. Add RSS URLs in the admin console." else ""
                entries.forEach { entry ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = getDrawable(R.drawable.bg_macro_log)
                        setPadding(14, 12, 14, 12)
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8) }
                        setOnClickListener {
                            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.link))) }
                            catch (_: Exception) { Toast.makeText(this@MainActivity, "No browser available", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    row.addView(TextView(this).apply { text = entry.title; textSize = 15f; setTextColor(getColor(R.color.text_primary)) })
                    row.addView(TextView(this).apply { text = "${entry.source} · ${relativeTime(entry.publishedAt)}"; textSize = 12f; setTextColor(getColor(R.color.text_muted)); setPadding(0, 5, 0, 0) })
                    list.addView(row)
                }
            } },
            onError = { error -> runOnUiThread {
                if (currentScreen != Screen.NEWS) return@runOnUiThread
                refresh.isRefreshing = false
                status.text = error
            } },
        )
    }

    private fun relativeTime(publishedAt: String?): String {
        val timestamp = try { publishedAt?.let { Instant.parse(it).toEpochMilli() } } catch (_: Exception) { null } ?: return "Recently"
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60_000).toInt()
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1_440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1_440}d ago"
        }
    }

    private fun recordTelemetry(data: org.json.JSONObject) {
        telemetry = data
        val now = System.currentTimeMillis()
        val ram = data.optJSONObject("ram")
        val gpu = data.optJSONObject("gpu")
        val totalRam = ram?.optLong("total", 0) ?: 0
        telemetryHistory += TelemetrySample(
            timestampMs = now,
            cpu = data.optDouble("cpu", 0.0).toFloat(),
            ram = if (totalRam > 0) (ram!!.optLong("used").toFloat() / totalRam * 100f) else 0f,
            gpu = gpu?.optDouble("usage")?.toFloat(),
        )
        while ((telemetryHistory.firstOrNull()?.timestampMs ?: now) < now - 60_000) {
            telemetryHistory.removeAt(0)
        }
        renderTelemetry()
    }

    private fun renderTelemetry() {
        if (currentScreen != Screen.TELEMETRY) return
        val compose = content.findViewById<ComposeView>(R.id.telemetry_compose) ?: return
        val data = telemetry
        compose.setContent {
            TelemetryDashboard(
                samples = telemetryHistory.toList(),
                cpuTemp = data?.takeUnless { it.isNull("cpuTemp") }?.optDouble("cpuTemp")?.toFloat(),
                gpuTemp = data?.optJSONObject("gpu")?.optDouble("temp")?.toFloat(),
                gpuAvailable = data?.optJSONObject("gpu") != null,
            )
        }
    }

    private fun uploadSelectedFiles(uris: List<android.net.Uri>) {
        if (currentScreen != Screen.FILES) showScreen(Screen.FILES)
        val status = findViewById<TextView>(R.id.files_status)
        val progress = findViewById<android.widget.ProgressBar>(R.id.files_upload_progress)
        val upload = findViewById<Button>(R.id.files_upload)
        if (!DeviceWebSocket.isConnected()) {
            try {
                val names = uris.map { OfflineFileQueue.enqueue(this, it) }
                status.text = "Saved ${names.size} file(s) on this phone — ${OfflineFileQueue.count(this)} waiting to sync."
            } catch (error: Exception) { status.text = error.message ?: "Could not queue files" }
            return
        }
        upload.isEnabled = false
        progress.visibility = View.VISIBLE
        progress.progress = 0
        val uploaded = mutableListOf<String>()
        fun uploadNext(index: Int) {
            if (index >= uris.size) {
                progress.visibility = View.GONE
                upload.isEnabled = true
                status.text = "Uploaded ${uploaded.size} file${if (uploaded.size == 1) "" else "s"}"
                loadFiles(content)
                return
            }
            FileRepository.upload(contentResolver, uris[index],
                onProgress = { percent -> runOnUiThread {
                    progress.progress = percent
                    status.text = "Uploading ${index + 1} of ${uris.size}… $percent%"
                } },
                onSuccess = { filename -> runOnUiThread { uploaded += filename; uploadNext(index + 1) } },
                onError = { error -> runOnUiThread {
                    progress.visibility = View.GONE
                    upload.isEnabled = true
                    status.text = "Uploaded ${uploaded.size} of ${uris.size}. $error"
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } },
            )
        }
        uploadNext(0)
    }

    private fun loadFiles(view: View) {
        val list = view.findViewById<LinearLayout>(R.id.files_list)
        val status = view.findViewById<TextView>(R.id.files_status)
        status.text = "Loading files…"
        FileRepository.fetchFiles(
            onSuccess = { files -> runOnUiThread {
                if (currentScreen != Screen.FILES) return@runOnUiThread
                list.removeAllViews()
                status.text = ""
                if (files.isEmpty()) {
                    list.addView(TextView(this).apply { text = getString(R.string.no_files_available); setTextColor(getColor(R.color.text_muted)) })
                } else files.forEach { file ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        background = getDrawable(R.drawable.bg_macro_log)
                        setPadding(14, 10, 10, 10)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8) }
                    }
                    row.addView(TextView(this).apply {
                        text = "${file.filename}\n${formatFileSize(file.size)}"
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(Button(this).apply {
                        text = "DOWNLOAD"
                        textSize = 11f
                        setOnClickListener { button ->
                            isEnabled = false
                            status.text = "Downloading ${file.filename}…"
                            FileRepository.download(contentResolver, file,
                                onSuccess = { runOnUiThread { status.text = "Saved ${file.filename} to Downloads"; button.isEnabled = true } },
                                onError = { error -> runOnUiThread { status.text = error; button.isEnabled = true; Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show() } },
                            )
                        }
                    })
                    list.addView(row)
                }
            } },
            onError = { error -> runOnUiThread {
                if (currentScreen == Screen.FILES) {
                    status.text = error
                }
            } },
        )
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun updatePhotoPreview() {
        val photo = capturePhoto
        capturePreview?.setImageBitmap(photo)
        capturePreview?.visibility = if (photo == null) View.GONE else View.VISIBLE
        removePhotoButton?.visibility = if (photo == null) View.GONE else View.VISIBLE
    }

    private fun loadTagSuggestions(tagText: AutoCompleteTextView) {
        CaptureRepository.fetchTags(
            onSuccess = { tags -> runOnUiThread {
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tags)
                tagText.setAdapter(adapter)
                tagText.threshold = 0
                tagText.setOnFocusChangeListener { _, hasFocus -> if (hasFocus && adapter.count > 0) tagText.showDropDown() }
                tagText.setOnClickListener { if (adapter.count > 0) tagText.showDropDown() }
            } },
            onError = { /* Captures can still use a new tag while offline. */ },
        )
    }

    private fun bindTouchpad(view: View) {
        val surface = view.findViewById<TouchpadSurfaceView>(R.id.touchpad_surface)
        activeTouchpadSurface = surface
        surface.connected = isConnected
        surface.commandSender = object : TouchpadCommandSender {
            override fun sendMove(dx: Float, dy: Float) = DeviceWebSocket.sendTouchpadMove(dx, dy)
            override fun sendClick(button: ClickButton) = DeviceWebSocket.sendTouchpadClick(rightButton = button == ClickButton.RIGHT)
            override fun sendScroll(deltaY: Float) = DeviceWebSocket.sendTouchpadScroll(deltaY)
        }
        surface.onReconnect = { VectrForegroundService.reconnect(this) }
    }

    private fun flashTouchpadView(view: View, pressedBackground: Int, defaultBackground: Int) {
        view.background = getDrawable(pressedBackground)
        view.postDelayed({ view.background = getDrawable(defaultBackground) }, 150)
    }

    private fun bindMacros(view: View) {
        macroGrid = view.findViewById(R.id.macro_grid)
        macroLog = view.findViewById(R.id.macro_log)
        val presetSpinner = view.findViewById<android.widget.Spinner>(R.id.macro_preset_spinner)

        MacroRepository.fetchPresets(
            onSuccess = { presets -> runOnUiThread {
                if (currentScreen != Screen.MACROS) return@runOnUiThread
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    presets.names,
                )
                presetSpinner.adapter = adapter
                val activeIndex = presets.names.indexOf(presets.active).coerceAtLeast(0)
                presetSpinner.setSelection(activeIndex)
                activeMacroPreset = presets.names.getOrNull(activeIndex) ?: presets.active
                loadMacros(activeMacroPreset)
            } },
            onError = { error -> runOnUiThread {
                if (currentScreen != Screen.MACROS) return@runOnUiThread
                macroGrid?.removeAllViews()
                macroGrid?.addView(TextView(this).apply {
                    text = error
                    setTextColor(getColor(R.color.failure_muted_red))
                })
            } },
        )

        presetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val name = parent?.getItemAtPosition(position)?.toString() ?: return
                if (name == activeMacroPreset) return
                activeMacroPreset = name
                MacroRepository.activatePreset(
                    name,
                    onSuccess = { active -> runOnUiThread {
                        if (currentScreen != Screen.MACROS) return@runOnUiThread
                        activeMacroPreset = active
                        loadMacros(active)
                    } },
                    onError = { error -> runOnUiThread {
                        if (currentScreen != Screen.MACROS) return@runOnUiThread
                        macroGrid?.removeAllViews()
                        macroGrid?.addView(TextView(this@MainActivity).apply {
                            text = error
                            setTextColor(getColor(R.color.failure_muted_red))
                        })
                    } },
                )
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun loadMacros(preset: String?) {
        MacroRepository.fetchConfig(
            preset = preset,
            onSuccess = { config -> runOnUiThread {
                if (currentScreen != Screen.MACROS) return@runOnUiThread
                macros.clear()
                config.forEach { macro -> macros[macro.id] = macro }
                renderMacroButtons(config)
            } },
            onError = { error -> runOnUiThread {
                if (currentScreen != Screen.MACROS) return@runOnUiThread
                macroGrid?.removeAllViews()
                macroGrid?.addView(TextView(this).apply {
                    text = error
                    setTextColor(getColor(R.color.failure_muted_red))
                })
            } },
        )
    }

    private fun renderMacroButtons(config: List<MacroConfig>) {
        val grid = macroGrid ?: return
        grid.removeAllViews()
        config.chunked(4).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.macro_button_height),
                ).apply {
                    setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.macro_grid_gap))
                }
            }
            rowItems.forEach { macro ->
                val button = Button(this).apply {
                    text = macro.label
                    isAllCaps = false
                    textSize = 17f
                    elevation = 8f
                    setTextColor(getColor(R.color.text_primary))
                    background = getDrawable(R.drawable.bg_macro_button)
                    contentDescription = macro.label
                    setOnClickListener { triggerMacro(macro, this) }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        val gap = resources.getDimensionPixelSize(R.dimen.macro_grid_gap)
                        setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
                    }
                }
                row.addView(button)
            }
            grid.addView(row)
        }
    }

    private fun triggerMacro(macro: MacroConfig, button: Button) {
        button.background = getDrawable(R.drawable.bg_macro_button_pressed)
        button.setTextColor(getColor(R.color.background_graphite))
        button.postDelayed({
            button.background = getDrawable(R.drawable.bg_macro_button)
            button.setTextColor(getColor(R.color.text_primary))
        }, 180)
        val requestId = UUID.randomUUID().toString()
        val timeout = Runnable {
            val pending = pendingMacros.remove(requestId) ?: return@Runnable
            Log.w("VectrMacro", "result timed out requestId=$requestId buttonId=${pending.buttonId}")
            addMacroLog(pending.label, System.currentTimeMillis(), false, getString(R.string.macro_timeout))
        }
        pendingMacros[requestId] = PendingMacro(macro.id, macro.label, button, timeout)
        button.postDelayed(timeout, 3000)
        DeviceWebSocket.sendMacroTrigger(macro.id, requestId) { error ->
            runOnUiThread {
                pendingMacros.remove(requestId)?.button?.removeCallbacks(timeout)
                Log.w("VectrMacro", "trigger failed requestId=$requestId buttonId=${macro.id}: $error")
                addMacroLog(macro.label, System.currentTimeMillis(), false, error)
            }
        }
    }

    private fun handleMacroResult(payload: org.json.JSONObject) {
        val requestId = payload.optString("requestId")
        val pending = pendingMacros.remove(requestId)
        if (requestId.isBlank() || pending == null) {
            Log.w("VectrMacro", "result ignored requestId=${requestId.ifBlank { "missing" }}")
            return
        }
        pending.button.removeCallbacks(pending.timeout)
        Log.d("VectrMacro", "result matched requestId=$requestId buttonId=${pending.buttonId}")
        val label = payload.optString("label", pending.label)
        addMacroLog(label, payload.optLong("timestamp", System.currentTimeMillis()), payload.optBoolean("success"), payload.optString("error").takeIf { it.isNotBlank() })
    }

    private fun addMacroLog(label: String, timestamp: Long, success: Boolean, error: String?) {
        val log = macroLog ?: return
        val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))
        val entry = TextView(this).apply {
            text = if (success) "✓  $label\n$time" else "×  $label\n$time — ${error ?: getString(R.string.macro_failed)}"
            setTextColor(getColor(if (success) R.color.accent_amber else R.color.failure_muted_red))
            textSize = 13f
            setPadding(12, 10, 12, 10)
            background = getDrawable(R.drawable.bg_macro_log)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
        }
        log.addView(entry, 0)
        while (log.childCount > 40) log.removeViewAt(log.childCount - 1)
    }

    private fun updateConnectionIndicator() {
        connectionLabel?.text = "${getString(if (isConnected) R.string.connected else R.string.disconnected)} · $connectionSource"
        connectionDot?.setImageResource(if (isConnected) R.drawable.ic_status_connected else R.drawable.ic_status_disconnected)
    }

    private fun updatePendingSyncIndicator(count: Int) {
        if (currentScreen != Screen.HOME) return
        val indicator = content.findViewById<TextView>(R.id.pending_sync_indicator) ?: return
        indicator.apply {
            text = resources.getQuantityString(R.plurals.pending_sync_items, count, count)
            visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    private fun labelFor(screen: Screen) = getString(
        when (screen) {
            Screen.HOME -> R.string.nav_home
            Screen.CAPTURE -> R.string.nav_capture
            Screen.MACROS -> R.string.nav_macros
            Screen.TOUCHPAD -> R.string.nav_touchpad
            Screen.FILES -> R.string.nav_files
            Screen.INVENTORY -> R.string.nav_home
            Screen.TELEMETRY -> R.string.nav_telemetry
            Screen.NEWS -> R.string.nav_news
            Screen.TODO -> R.string.nav_todo
            Screen.SPACE -> R.string.nav_home
            Screen.SCHEDWALL -> R.string.nav_schedwall
            Screen.GOALS -> R.string.nav_home
            Screen.CGPA -> R.string.nav_home
            Screen.CLIPBOARD_HISTORY -> R.string.nav_home
            Screen.SETTINGS -> R.string.nav_settings
        },
    )

}
