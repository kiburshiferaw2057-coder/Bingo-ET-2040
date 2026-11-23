package et.bingo.app.model

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
    val englishLabel: String
) {
    AMHARIC("am", "አማርኛ", "Amharic"),
    ENGLISH("en", "English", "English"),
    OROMO("om", "Afaan Oromo", "Oromo"),
    TIGRINYA("ti", "ትግርኛ", "Tigrinya")
}

enum class GamePhase {
    CARD_SELECTION,
    ALARM_COUNTDOWN,
    PLAYING,
    FINISHED
}

enum class GameMode(
    val isShortMode: Boolean = false
) {
    SHORT_ONE_OR_STAR(isShortMode = true),
    SHORT_THREE_LINE(isShortMode = true),
    BINGO_1020_CLASSIC(isShortMode = false)
}

enum class PaymentChannel(
    val shortCode: String,
    val defaultReceiptAccount: String,
    val secondaryReceiptAccount: String = "",
    val beneficiaryName: String = "Kibur Shiferaw Kassa"
) {
    TELEBIRR(
        shortCode = "TELEBIRR",
        defaultReceiptAccount = "0912341486",
        secondaryReceiptAccount = "0941705946",
        beneficiaryName = "Kibur"
    ),
    CBE(
        shortCode = "CBE",
        defaultReceiptAccount = "1000058081624",
        secondaryReceiptAccount = "",
        beneficiaryName = "Kibur Shiferaw Kassa"
    ),
    BOA(
        shortCode = "BOA",
        defaultReceiptAccount = "82323805",
        secondaryReceiptAccount = "",
        beneficiaryName = "Kibur Shiferaw Kassa"
    )
}

data class LongTermStats(
    val roundsPlayed: Int = 0,
    val roundsWon: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCardsBought: Int = 0,
    val totalSpentBirr: Double = 0.0,
    val totalEarnedBirr: Double = 0.0
)

data class PlayerProfile(
    val accountName: String = "",
    val phoneNumber: String = "",
    val receiptAccount: String = "",
    val pinHash: String = "",
    val balanceBirr: Double = 100.0,
    val preferredChannel: PaymentChannel = PaymentChannel.TELEBIRR,
    val isRegistered: Boolean = false,
    val longTermStats: LongTermStats = LongTermStats()
) {
    val displayName: String
        get() = accountName.trim().ifEmpty { "Player 1" }

    val activeReceiptAccount: String
        get() = receiptAccount.trim().ifEmpty {
            phoneNumber.trim().ifEmpty { preferredChannel.defaultReceiptAccount }
        }
}

data class WithdrawalRecord(
    val id: Int,
    val accountName: String,
    val phoneNumber: String,
    val receiptAccount: String,
    val beneficiaryName: String,
    val channel: PaymentChannel,
    val amountBirr: Double,
    val status: String
)

data class PaymentDepositRecord(
    val id: Int,
    val transactionNumber: String,
    val receiptAccount: String,
    val beneficiaryName: String,
    val channel: PaymentChannel,
    val amountBirr: Double,
    val sourceType: String,
    val status: String
)

data class BingoCard(
    val id: Int,
    val values: List<Int>,
    val marked: Set<Int> = emptySet()
) {
    fun value(row: Int, col: Int): Int = values[row * 5 + col]
}

data class PlayerEntry(
    val name: String,
    val card: BingoCard,
    val isCurrentUser: Boolean = false
)

data class GameConfig(
    val cardPriceBirr: Double = 10.0,
    val winnerShareRatio: Double = 0.80,
    val selectionIntervalSec: Int = 120,
    val alarmCountdownSec: Int = 10,
    val callIntervalMs: Long = 2000L,
    val shortCallIntervalMs: Long = 1300L
)

data class BingoGameState(
    val language: AppLanguage = AppLanguage.AMHARIC,
    val mode: GameMode = GameMode.SHORT_ONE_OR_STAR,
    val phase: GamePhase = GamePhase.CARD_SELECTION,
    val nextCardId: Int = 2,
    val entries: List<PlayerEntry> = emptyList(),
    val called: List<Int> = emptyList(),
    val statusKey: String = "select_cards",
    val totalPotBirr: Double = 0.0,
    val winnerPayoutEachBirr: Double = 0.0,
    val winningCardIds: List<Int> = emptyList(),
    val selectionSecondsLeft: Int = 120,
    val alarmSecondsLeft: Int = 10,
    val autoCallEnabled: Boolean = true,
    val profile: PlayerProfile = PlayerProfile(),
    val withdrawals: List<WithdrawalRecord> = emptyList(),
    val deposits: List<PaymentDepositRecord> = emptyList()
) {
    val playerCards: List<BingoCard>
        get() = entries.filter { it.isCurrentUser }.map { it.card }
}
