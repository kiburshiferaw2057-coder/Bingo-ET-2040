package et.bingo.app

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import et.bingo.app.engine.BingoEngine
import et.bingo.app.model.AppLanguage
import et.bingo.app.model.BingoCard
import et.bingo.app.model.GameConfig
import et.bingo.app.model.GameMode
import et.bingo.app.model.GamePhase
import et.bingo.app.model.LongTermStats
import et.bingo.app.model.PaymentChannel
import et.bingo.app.model.PlayerEntry
import et.bingo.app.model.PlayerProfile
import java.io.File
import kotlinx.coroutines.delay

// Green + Red Mix Palette for Bingo ET
private val ForestGreen = Color(0xFF0B4F2C)
private val EmeraldGreen = Color(0xFF15803D)
private val MintSoft = Color(0xFFEAF7EE)
private val LeafAccent = Color(0xFF166534)
private val SageBorder = Color(0xFF86EFAC)
private val LimeBg = Color(0xFFDCFCE7)
private val CrimsonRed = Color(0xFFB91C1C)
private val BrightRed = Color(0xFFDC2626)
private val RoseSoft = Color(0xFFFEE2E2)
private val RoseBorder = Color(0xFFFCA5A5)
private val AppBg = Color(0xFFF5FBF7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BingoETTheme {
                BingoETApp()
            }
        }
    }
}

@Composable
private fun BingoETTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = EmeraldGreen,
            onPrimary = Color.White,
            primaryContainer = MintSoft,
            onPrimaryContainer = ForestGreen,
            secondary = CrimsonRed,
            onSecondary = Color.White,
            secondaryContainer = RoseSoft,
            onSecondaryContainer = CrimsonRed,
            background = AppBg,
            onBackground = ForestGreen,
            surface = Color.White,
            onSurface = ForestGreen,
            surfaceVariant = MintSoft,
            onSurfaceVariant = LeafAccent
        ),
        content = content
    )
}

private fun playAlarmTone() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650)
    }
}

private fun playCallTone() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
    }
}

private fun loadSavedProfile(context: Context): PlayerProfile {
    val prefs = context.getSharedPreferences("bingo_et_longterm_profile", Context.MODE_PRIVATE)
    val accountName = prefs.getString("account_name", "") ?: ""
    val phone = prefs.getString("phone", "") ?: ""
    val channelName = prefs.getString("channel", PaymentChannel.TELEBIRR.name) ?: PaymentChannel.TELEBIRR.name
    val channel = runCatching { PaymentChannel.valueOf(channelName) }.getOrDefault(PaymentChannel.TELEBIRR)
    val receiptAccount = prefs.getString("receipt_account", channel.defaultReceiptAccount)
        ?: channel.defaultReceiptAccount
    val pinHash = prefs.getString("pin_hash", "") ?: ""
    val balance = prefs.getFloat("balance", 100f).toDouble()
    val registered = prefs.getBoolean("registered", false) || (phone.isNotBlank() && pinHash.isNotBlank())

    val stats = LongTermStats(
        roundsPlayed = prefs.getInt("lt_rounds_played", 0),
        roundsWon = prefs.getInt("lt_rounds_won", 0),
        currentStreak = prefs.getInt("lt_current_streak", 0),
        bestStreak = prefs.getInt("lt_best_streak", 0),
        totalCardsBought = prefs.getInt("lt_cards_bought", 0),
        totalSpentBirr = prefs.getFloat("lt_spent_birr", 0f).toDouble(),
        totalEarnedBirr = prefs.getFloat("lt_earned_birr", 0f).toDouble()
    )

    return PlayerProfile(
        accountName = accountName,
        phoneNumber = phone,
        receiptAccount = receiptAccount,
        pinHash = pinHash,
        balanceBirr = balance,
        preferredChannel = channel,
        isRegistered = registered,
        longTermStats = stats
    )
}

private fun saveProfile(context: Context, profile: PlayerProfile) {
    val prefs = context.getSharedPreferences("bingo_et_longterm_profile", Context.MODE_PRIVATE)
    val stats = profile.longTermStats
    prefs.edit()
        .putString("account_name", profile.accountName)
        .putString("phone", profile.phoneNumber)
        .putString("receipt_account", profile.activeReceiptAccount)
        .putString("pin_hash", profile.pinHash)
        .putFloat("balance", profile.balanceBirr.toFloat())
        .putString("channel", profile.preferredChannel.name)
        .putBoolean("registered", profile.isRegistered)
        .putInt("lt_rounds_played", stats.roundsPlayed)
        .putInt("lt_rounds_won", stats.roundsWon)
        .putInt("lt_current_streak", stats.currentStreak)
        .putInt("lt_best_streak", stats.bestStreak)
        .putInt("lt_cards_bought", stats.totalCardsBought)
        .putFloat("lt_spent_birr", stats.totalSpentBirr.toFloat())
        .putFloat("lt_earned_birr", stats.totalEarnedBirr.toFloat())
        .apply()
}

private fun createTempReceiptUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "bingo_et_receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun detectTransactionFromUri(
    context: Context,
    uri: Uri,
    engine: BingoEngine,
    onResult: (String, String) -> Unit
) {
    runCatching {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detected = engine.extractTransactionNumber(visionText.text)
                if (detected != null) {
                    onResult(detected, "ocr_found")
                } else {
                    onResult("", "ocr_not_found")
                }
            }
            .addOnFailureListener {
                onResult("", "ocr_failed")
            }
    }.onFailure {
        onResult("", "ocr_failed")
    }
}

private fun modeLabel(mode: GameMode, lang: AppLanguage): String = when (mode) {
    GameMode.SHORT_ONE_OR_STAR -> when (lang) {
        AppLanguage.AMHARIC -> "አጭር፡ 1 መስመር ወይም 4 ኮከብ"
        AppLanguage.ENGLISH -> "Short: 1 Line or 4 Star"
        AppLanguage.OROMO -> "Gabaabaa: Sarara 1 ykn Urjii 4"
        AppLanguage.TIGRINYA -> "ሓጺር፡ 1 መስመር ወይ 4 ኮከብ"
    }
    GameMode.SHORT_THREE_LINE -> when (lang) {
        AppLanguage.AMHARIC -> "አጭር፡ 3 መስመር ቢንጎ"
        AppLanguage.ENGLISH -> "Short: 3 Line Bingo"
        AppLanguage.OROMO -> "Gabaabaa: Sarara 3 Bingo"
        AppLanguage.TIGRINYA -> "ሓጺር፡ 3 መስመር ቢንጎ"
    }
    GameMode.BINGO_1020_CLASSIC -> when (lang) {
        AppLanguage.AMHARIC -> "ቢንጎ 1020 ስታይል"
        AppLanguage.ENGLISH -> "Bingo 1020 Style"
        AppLanguage.OROMO -> "Aadaa Bingo 1020"
        AppLanguage.TIGRINYA -> "ቢንጎ 1020 ቅዲ"
    }
}

private fun channelSummaryText(channel: PaymentChannel): String = when (channel) {
    PaymentChannel.TELEBIRR -> "Telebirr: 0912341486 / 0941705946 (Kibur)"
    PaymentChannel.CBE -> "CBE: 1000058081624 (Kibur Shiferaw Kassa)"
    PaymentChannel.BOA -> "BOA: 82323805 (Kibur Shiferaw Kassa)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BingoETApp() {
    val context = LocalContext.current
    val config = remember { GameConfig() }
    val engine = remember { BingoEngine(config) }

    var state by remember {
        val saved = loadSavedProfile(context)
        mutableStateOf(engine.createInitialState(saved))
    }

    var accountNameInput by remember { mutableStateOf(state.profile.accountName) }
    var phoneInput by remember { mutableStateOf(state.profile.phoneNumber) }
    var selectedChannel by remember { mutableStateOf(state.profile.preferredChannel) }
    var receiptAccountInput by remember {
        mutableStateOf(state.profile.receiptAccount.ifBlank { state.profile.preferredChannel.defaultReceiptAccount })
    }
    var pinInput by remember { mutableStateOf("") }
    var profileFeedbackKey by remember { mutableStateOf<String?>(null) }

    var txNumberInput by remember { mutableStateOf("") }
    var depositAmountInput by remember { mutableStateOf("50") }
    var proofSourceType by remember { mutableStateOf("Manual") }
    var proofFeedbackKey by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var withdrawAmountInput by remember { mutableStateOf("50") }
    var withdrawReceiptInput by remember {
        mutableStateOf(state.profile.receiptAccount.ifBlank { state.profile.preferredChannel.defaultReceiptAccount })
    }
    var withdrawPinInput by remember { mutableStateOf("") }
    var withdrawFeedbackKey by remember { mutableStateOf<String?>(null) }

    var showWalletSection by remember { mutableStateOf(false) }
    var showProfileSection by remember { mutableStateOf(!state.profile.isRegistered) }
    var showCardsSection by remember { mutableStateOf(true) }

    val lang = state.language

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            proofSourceType = "Gallery Receipt"
            detectTransactionFromUri(context, uri, engine) { detectedTx, statusKey ->
                if (detectedTx.isNotBlank()) txNumberInput = detectedTx
                proofFeedbackKey = statusKey
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            proofSourceType = "Camera Receipt"
            detectTransactionFromUri(context, captured, engine) { detectedTx, statusKey ->
                if (detectedTx.isNotBlank()) txNumberInput = detectedTx
                proofFeedbackKey = statusKey
            }
        }
    }

    LaunchedEffect(state.profile) {
        saveProfile(context, state.profile)
    }

    LaunchedEffect(state.phase, state.selectionSecondsLeft) {
        if (state.phase == GamePhase.FINISHED) {
            while (state.selectionSecondsLeft > 0 && state.phase == GamePhase.FINISHED) {
                delay(1000L)
                state = state.copy(selectionSecondsLeft = (state.selectionSecondsLeft - 1).coerceAtLeast(0))
            }
            if (state.phase == GamePhase.FINISHED) {
                state = engine.prepareNextSelectionCycle(state)
            }
        }
    }

    LaunchedEffect(state.phase, state.alarmSecondsLeft) {
        if (state.phase == GamePhase.ALARM_COUNTDOWN) {
            while (state.alarmSecondsLeft > 0 && state.phase == GamePhase.ALARM_COUNTDOWN) {
                playAlarmTone()
                delay(1000L)
                state = state.copy(alarmSecondsLeft = (state.alarmSecondsLeft - 1).coerceAtLeast(0))
            }
            if (state.phase == GamePhase.ALARM_COUNTDOWN) {
                state = engine.startPlaying(state)
            }
        }
    }

    val activeCallIntervalMs = if (state.mode.isShortMode) {
        config.shortCallIntervalMs
    } else {
        config.callIntervalMs
    }

    LaunchedEffect(state.phase, state.autoCallEnabled, state.called.size, state.mode) {
        while (state.phase == GamePhase.PLAYING && state.autoCallEnabled && state.called.size < 75) {
            delay(activeCallIntervalMs)
            if (state.phase == GamePhase.PLAYING && state.autoCallEnabled && state.called.size < 75) {
                val (nextState, drawn) = engine.drawNextNumber(state)
                state = nextState
                if (drawn != null) playCallTone()
                if (nextState.phase == GamePhase.FINISHED) playAlarmTone()
            }
        }
        if (state.phase == GamePhase.PLAYING && state.called.size == 75 && state.winningCardIds.isEmpty()) {
            state = state.copy(phase = GamePhase.FINISHED, autoCallEnabled = false, statusKey = "no_winner_draw")
        }
    }

    fun tr(key: String): String = when (key) {
        "select_cards" -> when (lang) {
            AppLanguage.AMHARIC -> "ካርድ 10 ብር • አሸናፊ 80% • ቢንጎ 1020"
            AppLanguage.ENGLISH -> "10 ETB / card • 80% winner share • Bingo 1020"
            AppLanguage.OROMO -> "10 ETB / kaardii • 80% mo'ataaf • Bingo 1020"
            AppLanguage.TIGRINYA -> "10 ETB / ካርድ • 80% ንተዓዋቲ • ቢንጎ 1020"
        }
        "insufficient_balance" -> when (lang) {
            AppLanguage.AMHARIC -> "በቂ ሂሳብ የለም፤ እባክዎ ሂሳብ ያስገቡ።"
            AppLanguage.ENGLISH -> "Insufficient balance; deposit via Telebirr, CBE, or BOA."
            AppLanguage.OROMO -> "Madaalliin ga'aa miti; Telebirr, CBE ykn BOAan galchi."
            AppLanguage.TIGRINYA -> "እኹል ሂሳብ የለን፤ ብTelebirr፣ CBE ወይ BOA የእትዉ።"
        }
        "max_cards" -> when (lang) {
            AppLanguage.AMHARIC -> "በአንድ ዙር እስከ 10 ካርድ ይቻላል።"
            AppLanguage.ENGLISH -> "Up to 10 personal cards per round."
            AppLanguage.OROMO -> "Marsaa tokkotti kaardii 10 hanga ta'u."
            AppLanguage.TIGRINYA -> "ኣብ ሓደ ዙር ክሳብ 10 ካርድ ይከኣል።"
        }
        "max_total_cards" -> when (lang) {
            AppLanguage.AMHARIC -> "ጠቅላላ ካርዶች 20 ደርሰዋል።"
            AppLanguage.ENGLISH -> "Maximum 20 total cards in the room."
            AppLanguage.OROMO -> "Walumaagalatti kaardii 20 guuteera."
            AppLanguage.TIGRINYA -> "ጠቕላላ ካርድታት 20 በጺሑ።"
        }
        "card_added" -> when (lang) {
            AppLanguage.AMHARIC -> "አዲስ ካርድ (10 ብር) ታክሏል።"
            AppLanguage.ENGLISH -> "Card added (10 ETB)."
            AppLanguage.OROMO -> "Kaardiin ida'ameera (10 ETB)."
            AppLanguage.TIGRINYA -> "ካርድ ተወሲኹ (10 ETB)።"
        }
        "bot_added" -> when (lang) {
            AppLanguage.AMHARIC -> "የቢንጎ 1020 ተጫዋች ታክሏል።"
            AppLanguage.ENGLISH -> "Bingo 1020 player added."
            AppLanguage.OROMO -> "Taphataan Bingo 1020 ida'ameera."
            AppLanguage.TIGRINYA -> "ተጻዋታይ ቢንጎ 1020 ተወሲኹ።"
        }
        "alarm_started" -> when (lang) {
            AppLanguage.AMHARIC -> "አላርም ተጀምሯል! (${state.alarmSecondsLeft}s)"
            AppLanguage.ENGLISH -> "Alarm active! (${state.alarmSecondsLeft}s)"
            AppLanguage.OROMO -> "Alarmin ka'eera! (${state.alarmSecondsLeft}s)"
            AppLanguage.TIGRINYA -> "ኣላርም ተጀሚሩ! (${state.alarmSecondsLeft}s)"
        }
        "playing" -> when (lang) {
            AppLanguage.AMHARIC -> "ጨዋታው በሂደት ላይ ነው።"
            AppLanguage.ENGLISH -> "Game live!"
            AppLanguage.OROMO -> "Taphni eegaleera!"
            AppLanguage.TIGRINYA -> "ጸወታ ይቕጽል ኣሎ!"
        }
        "drawn" -> when (lang) {
            AppLanguage.AMHARIC -> "ቁጥር ${state.called.lastOrNull() ?: ""} ተጠርቷል።"
            AppLanguage.ENGLISH -> "Called ${state.called.lastOrNull() ?: ""}."
            AppLanguage.OROMO -> "Lakkoofsi ${state.called.lastOrNull() ?: ""} waamameera."
            AppLanguage.TIGRINYA -> "ቁጽሪ ${state.called.lastOrNull() ?: ""} ተጸዊዑ።"
        }
        "no_winner_yet" -> when (lang) {
            AppLanguage.AMHARIC -> "እስካሁን አሸናፊ የለም።"
            AppLanguage.ENGLISH -> "No winner yet."
            AppLanguage.OROMO -> "Ammaaf mo'ataan hin jiru."
            AppLanguage.TIGRINYA -> "ክሳብ ሕጂ ተዓዋቲ የለን።"
        }
        "winners_declared" -> when (lang) {
            AppLanguage.AMHARIC -> "አሸናፊ ተረጋግጧል! 80% ሽልማት ተሰጥቷል።"
            AppLanguage.ENGLISH -> "Winner confirmed! 80% pot awarded."
            AppLanguage.OROMO -> "Mo'ataan mirkanaa'eera! 80% kennameera."
            AppLanguage.TIGRINYA -> "ተዓዋቲ ተረጋጊጹ! 80% ተዋሂቡ።"
        }
        "no_winner_draw" -> when (lang) {
            AppLanguage.AMHARIC -> "75 ቁጥሮች ተጠርተዋል።"
            AppLanguage.ENGLISH -> "All 75 numbers called."
            AppLanguage.OROMO -> "Lakkoofsi 75 waamameera."
            AppLanguage.TIGRINYA -> "75 ቁጽርታት ተጸዊዖም።"
        }
        "new_cycle_selection" -> when (lang) {
            AppLanguage.AMHARIC -> "አዲስ ዙር ጀምሯል።"
            AppLanguage.ENGLISH -> "New round started."
            AppLanguage.OROMO -> "Marsaan haaraan jalqabeera."
            AppLanguage.TIGRINYA -> "ሓድሽ ዙር ተጀሚሩ።"
        }
        "mode_changed" -> when (lang) {
            AppLanguage.AMHARIC -> "የጨዋታ ሁኔታ ተቀይሯል።"
            AppLanguage.ENGLISH -> "Game mode updated."
            AppLanguage.OROMO -> "Haalli taphaa haaromfameera."
            AppLanguage.TIGRINYA -> "ኩነታት ጸወታ ተቐይሩ።"
        }
        "language_changed" -> when (lang) {
            AppLanguage.AMHARIC -> "ቋንቋ ተቀይሯል።"
            AppLanguage.ENGLISH -> "Language updated."
            AppLanguage.OROMO -> "Afaan jijjiirameera."
            AppLanguage.TIGRINYA -> "ቋንቋ ተቐይሩ።"
        }
        "profile_saved" -> when (lang) {
            AppLanguage.AMHARIC -> "መለያ እና የመቀበያ ሂሳብ ተቀምጧል።"
            AppLanguage.ENGLISH -> "Account & receipt details saved."
            AppLanguage.OROMO -> "Herregni ol kaayameera."
            AppLanguage.TIGRINYA -> "ሕሳብ ተዓቂቡ።"
        }
        "deposit_success" -> when (lang) {
            AppLanguage.AMHARIC -> "ክፍያ ተረጋግጦ ሂሳብ ተጨምሯል።"
            AppLanguage.ENGLISH -> "Payment verified & credited."
            AppLanguage.OROMO -> "Kaffaltiin mirkanaa'ee dabaleera."
            AppLanguage.TIGRINYA -> "ክፍሊት ተረጋጊጹ ተወሲኹ።"
        }
        else -> ""
    }

    fun translateAuthOrWallet(key: String?): String = when (key) {
        "invalid_name" -> "Enter a valid account name (at least 2 characters)."
        "invalid_phone" -> "Enter a valid Ethiopian phone (09..., 07..., +251...)."
        "invalid_receipt" -> "Enter a valid Telebirr, CBE, or BOA account."
        "invalid_pin" -> "PIN must be 5 digits."
        "profile_saved" -> tr("profile_saved")
        "ocr_found" -> "Transaction reference detected from receipt."
        "ocr_not_found" -> "Reference not found in image; enter manually."
        "ocr_failed" -> "Could not read image; enter manually."
        "invalid_tx" -> "Enter a valid transaction ID (at least 6 characters)."
        "invalid_deposit_amount" -> "Enter an amount greater than 0 ETB."
        "deposit_success" -> tr("deposit_success")
        "not_registered" -> "Save your profile first."
        "wrong_pin" -> "Incorrect 5-digit PIN."
        "invalid_amount" -> "Enter a valid amount within balance."
        "withdraw_success" -> "Withdrawal recorded to receipt account."
        else -> ""
    }

    val stats = state.profile.longTermStats

    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bingo ET",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(BrightRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "1020",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showProfileSection = !showProfileSection },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when (lang) {
                                AppLanguage.AMHARIC -> "መለያ"
                                AppLanguage.ENGLISH -> "Account"
                                AppLanguage.OROMO -> "Herrega"
                                AppLanguage.TIGRINYA -> "ሕሳብ"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(
                        onClick = { showWalletSection = !showWalletSection },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.0f", state.profile.balanceBirr)} ETB",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 4-Language Selector: Amharic, English, Oromo, Tigrinya
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Amharic • English • Oromo • Tigrinya",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppLanguage.entries.forEach { option ->
                                val selected = state.language == option
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        state = state.copy(language = option, statusKey = "language_changed")
                                    },
                                    label = {
                                        Text(
                                            text = option.nativeLabel,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonRed,
                                        selectedLabelColor = Color.White,
                                        containerColor = MintSoft,
                                        labelColor = ForestGreen
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Summary Banner + Long-Term Bingo 1020 Tracking
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MintSoft),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SageBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bingo ET • ${modeLabel(state.mode, lang)}",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Pot: ${String.format("%.0f", state.totalPotBirr)} ETB",
                                color = CrimsonRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Card: 10 ETB • Winner: 80% • Rounds: ${stats.roundsPlayed} | Wins: ${stats.roundsWon} | Streak: ${stats.currentStreak} (Best ${stats.bestStreak})",
                            color = LeafAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Account & Receipt Account Setup
            if (showProfileSection) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Account & Receipt Account (Telebirr / CBE / BOA)",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            OutlinedTextField(
                                value = accountNameInput,
                                onValueChange = { accountNameInput = it },
                                label = { Text("Account Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Phone Number (09... / +251...)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            PaymentChannel.entries.forEach { ch ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedChannel = ch
                                            receiptAccountInput = ch.defaultReceiptAccount
                                            withdrawReceiptInput = ch.defaultReceiptAccount
                                        }
                                ) {
                                    RadioButton(
                                        selected = selectedChannel == ch,
                                        onClick = {
                                            selectedChannel = ch
                                            receiptAccountInput = ch.defaultReceiptAccount
                                            withdrawReceiptInput = ch.defaultReceiptAccount
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = CrimsonRed)
                                    )
                                    Text(channelSummaryText(ch), color = ForestGreen, fontSize = 13.sp)
                                }
                            }

                            OutlinedTextField(
                                value = receiptAccountInput,
                                onValueChange = { receiptAccountInput = it },
                                label = { Text("Receipt Account (${selectedChannel.shortCode})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("5-Digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (profileFeedbackKey != null) {
                                Text(
                                    text = translateAuthOrWallet(profileFeedbackKey),
                                    color = if (profileFeedbackKey == "profile_saved") EmeraldGreen else CrimsonRed,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val (nextState, err) = engine.registerProfile(
                                        state = state,
                                        accountName = accountNameInput,
                                        phone = phoneInput,
                                        receiptAccount = receiptAccountInput,
                                        pin = pinInput,
                                        channel = selectedChannel
                                    )
                                    if (err == null) {
                                        state = nextState
                                        pinInput = ""
                                        profileFeedbackKey = "profile_saved"
                                        showProfileSection = false
                                    } else {
                                        profileFeedbackKey = err
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Profile")
                            }
                        }
                    }
                }
            }

            // Receipt Deposit & Withdrawal Card (Telebirr / CBE / BOA)
            if (showWalletSection) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Receipt Accounts: Telebirr (0912341486 / 0941705946 Kibur) • CBE 1000058081624 • BOA 82323805 (Kibur Shiferaw Kassa)",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val uri = createTempReceiptUri(context)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CrimsonRed)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Camera", color = ForestGreen)
                                }
                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = EmeraldGreen)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receipt", color = ForestGreen)
                                }
                            }

                            OutlinedTextField(
                                value = receiptAccountInput,
                                onValueChange = { receiptAccountInput = it },
                                label = { Text("Receipt Account (${selectedChannel.shortCode})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = txNumberInput,
                                onValueChange = { txNumberInput = it },
                                label = { Text("Transaction ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = depositAmountInput,
                                onValueChange = { depositAmountInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Deposit Amount (ETB)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (proofFeedbackKey != null) {
                                Text(
                                    text = translateAuthOrWallet(proofFeedbackKey),
                                    color = if (proofFeedbackKey in listOf("ocr_found", "deposit_success")) EmeraldGreen else CrimsonRed,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val amt = depositAmountInput.toDoubleOrNull() ?: 0.0
                                    val (nextState, err) = engine.submitPaymentProof(
                                        state = state,
                                        transactionNumber = txNumberInput,
                                        amountBirr = amt,
                                        channel = selectedChannel,
                                        receiptAccount = receiptAccountInput,
                                        sourceType = proofSourceType
                                    )
                                    if (err == null) {
                                        state = nextState
                                        txNumberInput = ""
                                        proofFeedbackKey = "deposit_success"
                                    } else {
                                        proofFeedbackKey = err
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Confirm Deposit")
                            }

                            OutlinedTextField(
                                value = withdrawReceiptInput,
                                onValueChange = { withdrawReceiptInput = it },
                                label = { Text("Withdrawal Receipt Account") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = withdrawAmountInput,
                                onValueChange = { withdrawAmountInput = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Withdraw Amount (ETB)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = withdrawPinInput,
                                onValueChange = { withdrawPinInput = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("5-Digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (withdrawFeedbackKey != null) {
                                Text(
                                    text = translateAuthOrWallet(withdrawFeedbackKey),
                                    color = if (withdrawFeedbackKey == "withdraw_success") EmeraldGreen else CrimsonRed,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val amt = withdrawAmountInput.toDoubleOrNull() ?: 0.0
                                    val (nextState, err) = engine.requestWithdrawal(
                                        state = state,
                                        amountBirr = amt,
                                        channel = selectedChannel,
                                        receiptAccount = withdrawReceiptInput,
                                        pin = withdrawPinInput
                                    )
                                    if (err == null) {
                                        state = nextState
                                        withdrawPinInput = ""
                                        withdrawFeedbackKey = "withdraw_success"
                                    } else {
                                        withdrawFeedbackKey = err
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Withdraw (${selectedChannel.shortCode})")
                            }
                        }
                    }
                }
            }

            // Mode Selection: 1. Short 1 Line or 4 Star, 2. Short 3 Line Bingo, 3. Bingo 1020 Style
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (lang) {
                                AppLanguage.AMHARIC -> "የጨዋታ አይነት"
                                AppLanguage.ENGLISH -> "Game Mode"
                                AppLanguage.OROMO -> "Gosa Taphaa"
                                AppLanguage.TIGRINYA -> "ዓይነት ጸወታ"
                            },
                            color = ForestGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        val modeEditable = state.phase == GamePhase.CARD_SELECTION
                        GameMode.entries.forEachIndexed { idx, modeOption ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = modeEditable) {
                                        state = state.copy(mode = modeOption, statusKey = "mode_changed")
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = state.mode == modeOption,
                                    enabled = modeEditable,
                                    onClick = {
                                        state = state.copy(mode = modeOption, statusKey = "mode_changed")
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = if (modeOption.isShortMode) CrimsonRed else EmeraldGreen
                                    )
                                )
                                Text(
                                    text = "${idx + 1}. ${modeLabel(modeOption, lang)}",
                                    color = if (modeEditable) ForestGreen else Color.Gray,
                                    fontWeight = if (state.mode == modeOption) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Game Controls, Called Strip, Card Actions (10 ETB each, 80% Winner Share)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr(state.statusKey),
                            color = LeafAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )

                        when (state.phase) {
                            GamePhase.CARD_SELECTION -> {
                                Button(
                                    onClick = { state = engine.startAlarm(state) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start 10s Alarm & Play")
                                }
                            }
                            GamePhase.ALARM_COUNTDOWN -> {
                                Text(
                                    text = "${state.alarmSecondsLeft}s",
                                    color = CrimsonRed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            }
                            GamePhase.PLAYING -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val (nextState, drawn) = engine.drawNextNumber(state)
                                            state = nextState
                                            if (drawn != null) playCallTone()
                                            if (nextState.phase == GamePhase.FINISHED) playAlarmTone()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Call Ball")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            state = engine.evaluateWinners(state)
                                            if (state.phase == GamePhase.FINISHED) playAlarmTone()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("BINGO!", color = CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            GamePhase.FINISHED -> {
                                Button(
                                    onClick = { state = engine.prepareNextSelectionCycle(state) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Next Round (${state.selectionSecondsLeft}s)")
                                }
                            }
                        }

                        if (state.called.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.called.takeLast(12).forEach { num ->
                                    val isLatest = num == state.called.lastOrNull()
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isLatest) CrimsonRed else EmeraldGreen)
                                    ) {
                                        Text(
                                            text = num.toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { state = engine.addPlayerCard(state) },
                                enabled = state.phase == GamePhase.CARD_SELECTION && state.playerCards.size < 10,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ Card (10 ETB)", color = ForestGreen)
                            }
                            OutlinedButton(
                                onClick = { state = engine.addOpponentCard(state) },
                                enabled = state.phase == GamePhase.CARD_SELECTION && state.entries.size < 20,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+ 1020 Player", color = CrimsonRed)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cards (${state.entries.size})",
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showCardsSection = !showCardsSection }) {
                                Text(
                                    text = if (showCardsSection) "Hide" else "Show",
                                    color = CrimsonRed
                                )
                            }
                        }

                        if (state.winningCardIds.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RoseSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, RoseBorder, RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CrimsonRed)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "BINGO! ${state.winningCardIds.joinToString { "#" + it }} • ${String.format("%.2f", state.winnerPayoutEachBirr)} ETB",
                                        color = CrimsonRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCardsSection) {
                items(state.entries, key = { it.card.id }) { entry ->
                    BingoCardView(
                        entry = entry,
                        calledSet = state.called.toSet(),
                        isWinnerCard = entry.card.id in state.winningCardIds,
                        onToggleMark = { number ->
                            state = engine.toggleMark(state, entry.card.id, number)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BingoCardView(
    entry: PlayerEntry,
    calledSet: Set<Int>,
    isWinnerCard: Boolean,
    onToggleMark: (Int) -> Unit
) {
    val card: BingoCard = entry.card
    val headers = listOf("B", "I", "N", "G", "O")

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isWinnerCard) {
                    Modifier.border(2.dp, CrimsonRed, RoundedCornerShape(14.dp))
                } else if (entry.isCurrentUser) {
                    Modifier.border(1.dp, SageBorder, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#${card.id} • ${entry.name}",
                    color = ForestGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (entry.isCurrentUser) "YOU" else "1020",
                    color = if (entry.isCurrentUser) EmeraldGreen else CrimsonRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                headers.forEachIndexed { idx, letter ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(
                                if (idx % 2 == 0) EmeraldGreen else CrimsonRed,
                                RoundedCornerShape(6.dp)
                            )
                    ) {
                        Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            for (row in 0 until 5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 5) {
                        val value = card.value(row, col)
                        val isFree = row == 2 && col == 2
                        val isMarked = value in card.marked
                        val isCalled = value in calledSet

                        val bgColor = when {
                            isFree -> LimeBg
                            isMarked && isCalled -> RoseSoft
                            isMarked -> MintSoft
                            isCalled -> Color(0xFFFFF1F2)
                            else -> Color.White
                        }
                        val borderColor = when {
                            isFree -> EmeraldGreen
                            isMarked -> CrimsonRed
                            isCalled -> BrightRed
                            else -> Color(0xFFD1D5DB)
                        }
                        val textColor = when {
                            isMarked || isCalled -> CrimsonRed
                            else -> ForestGreen
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(bgColor, RoundedCornerShape(7.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(7.dp))
                                .clickable(enabled = !isFree) { onToggleMark(value) }
                        ) {
                            Text(
                                text = if (isFree) "FREE" else value.toString(),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isFree) 11.sp else 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (row < 4) Spacer(Modifier.height(4.dp))
            }
        }
    }
}
