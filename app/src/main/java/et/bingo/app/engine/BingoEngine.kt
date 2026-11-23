package et.bingo.app.engine

import et.bingo.app.model.BingoCard
import et.bingo.app.model.BingoGameState
import et.bingo.app.model.GameConfig
import et.bingo.app.model.GameMode
import et.bingo.app.model.GamePhase
import et.bingo.app.model.PaymentChannel
import et.bingo.app.model.PaymentDepositRecord
import et.bingo.app.model.PlayerEntry
import et.bingo.app.model.PlayerProfile
import et.bingo.app.model.WithdrawalRecord
import java.security.MessageDigest

class BingoEngine(private val config: GameConfig = GameConfig()) {

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidEthiopianPhone(raw: String): Boolean {
        val cleaned = raw.trim().replace(" ", "")
        return Regex("^(09\\d{8}|07\\d{8}|\\+2519\\d{8}|\\+2517\\d{8})$").matches(cleaned)
    }

    fun isValidReceiptAccount(raw: String, channel: PaymentChannel): Boolean {
        val cleaned = raw.trim().replace(" ", "")
        if (cleaned.isEmpty()) return false
        return when (channel) {
            PaymentChannel.TELEBIRR -> isValidEthiopianPhone(cleaned) || Regex("^[A-Za-z0-9]{6,24}$").matches(cleaned)
            PaymentChannel.CBE, PaymentChannel.BOA -> Regex("^[A-Za-z0-9]{7,24}$").matches(cleaned) || isValidEthiopianPhone(cleaned)
        }
    }

    fun isValidFiveDigitPin(pin: String): Boolean {
        return Regex("^\\d{5}$").matches(pin)
    }

    fun extractTransactionNumber(rawText: String): String? {
        val candidates = Regex("\\b[A-Z0-9]{8,20}\\b")
            .findAll(rawText.uppercase())
            .map { it.value }
            .filter { token ->
                token.any { it.isDigit() } &&
                    token !in setOf(
                        "TELEBIRR", "ETHIO", "TELECOM", "COMMERCIAL",
                        "BANK", "ETHIOPIA", "ABYSSINIA", "RECEIPT", "TRANSACTION"
                    )
            }
            .toList()
        return candidates.firstOrNull()
    }

    fun submitPaymentProof(
        state: BingoGameState,
        transactionNumber: String,
        amountBirr: Double,
        channel: PaymentChannel,
        receiptAccount: String,
        sourceType: String
    ): Pair<BingoGameState, String?> {
        val cleanTx = transactionNumber.trim().uppercase()
        if (cleanTx.length < 6) {
            return state to "invalid_tx"
        }
        if (amountBirr <= 0.0) {
            return state to "invalid_deposit_amount"
        }
        val cleanReceipt = receiptAccount.trim().ifEmpty { channel.defaultReceiptAccount }
        val record = PaymentDepositRecord(
            id = state.deposits.size + 1,
            transactionNumber = cleanTx,
            receiptAccount = cleanReceipt,
            beneficiaryName = channel.beneficiaryName,
            channel = channel,
            amountBirr = amountBirr,
            sourceType = sourceType,
            status = "Verified"
        )
        val updatedProfile = state.profile.copy(
            balanceBirr = state.profile.balanceBirr + amountBirr,
            preferredChannel = channel,
            receiptAccount = if (state.profile.receiptAccount.isBlank()) cleanReceipt else state.profile.receiptAccount
        )
        return state.copy(
            profile = updatedProfile,
            deposits = listOf(record) + state.deposits,
            statusKey = "deposit_success"
        ) to null
    }

    fun registerProfile(
        state: BingoGameState,
        accountName: String,
        phone: String,
        receiptAccount: String,
        pin: String,
        channel: PaymentChannel
    ): Pair<BingoGameState, String?> {
        val cleanName = accountName.trim().replace(Regex("\\s+"), " ")
        val cleanedPhone = phone.trim().replace(" ", "")
        val cleanReceipt = receiptAccount.trim().replace(" ", "").ifEmpty { channel.defaultReceiptAccount }

        if (cleanName.length < 2) {
            return state to "invalid_name"
        }
        if (!isValidEthiopianPhone(cleanedPhone)) {
            return state to "invalid_phone"
        }
        if (!isValidReceiptAccount(cleanReceipt, channel)) {
            return state to "invalid_receipt"
        }
        if (!isValidFiveDigitPin(pin)) {
            return state to "invalid_pin"
        }

        val updatedProfile = state.profile.copy(
            accountName = cleanName,
            phoneNumber = cleanedPhone,
            receiptAccount = cleanReceipt,
            pinHash = hashPin(pin),
            preferredChannel = channel,
            isRegistered = true
        )
        val updatedEntries = state.entries.map { entry ->
            if (entry.isCurrentUser) entry.copy(name = cleanName) else entry
        }
        return state.copy(
            profile = updatedProfile,
            entries = updatedEntries,
            statusKey = "profile_saved"
        ) to null
    }

    fun requestWithdrawal(
        state: BingoGameState,
        amountBirr: Double,
        channel: PaymentChannel,
        receiptAccount: String,
        pin: String
    ): Pair<BingoGameState, String?> {
        val profile = state.profile
        if (!profile.isRegistered) {
            return state to "not_registered"
        }
        if (!isValidFiveDigitPin(pin) || hashPin(pin) != profile.pinHash) {
            return state to "wrong_pin"
        }
        if (amountBirr <= 0.0 || amountBirr > profile.balanceBirr) {
            return state to "invalid_amount"
        }
        val targetReceipt = receiptAccount.trim().replace(" ", "").ifEmpty { profile.activeReceiptAccount }
        if (!isValidReceiptAccount(targetReceipt, channel)) {
            return state to "invalid_receipt"
        }

        val record = WithdrawalRecord(
            id = state.withdrawals.size + 1,
            accountName = profile.displayName,
            phoneNumber = profile.phoneNumber,
            receiptAccount = targetReceipt,
            beneficiaryName = channel.beneficiaryName,
            channel = channel,
            amountBirr = amountBirr,
            status = "Completed"
        )
        val updatedProfile = profile.copy(
            balanceBirr = profile.balanceBirr - amountBirr,
            receiptAccount = targetReceipt,
            preferredChannel = channel
        )
        return state.copy(
            profile = updatedProfile,
            withdrawals = listOf(record) + state.withdrawals,
            statusKey = "withdraw_success"
        ) to null
    }

    fun createInitialState(profile: PlayerProfile = PlayerProfile()): BingoGameState {
        val initialEntries = listOf(
            PlayerEntry(name = profile.displayName, card = makeCard(1), isCurrentUser = true)
        )
        return BingoGameState(
            entries = initialEntries,
            nextCardId = 2,
            totalPotBirr = initialEntries.size * config.cardPriceBirr,
            selectionSecondsLeft = config.selectionIntervalSec,
            alarmSecondsLeft = config.alarmCountdownSec,
            profile = profile
        )
    }

    fun makeCard(id: Int): BingoCard {
        val ranges = listOf(1..15, 16..30, 31..45, 46..60, 61..75)
        val columns = ranges.map { range -> range.shuffled().take(5) }
        val values = (0 until 5).flatMap { row ->
            (0 until 5).map { col ->
                if (row == 2 && col == 2) 0 else columns[col][row]
            }
        }
        return BingoCard(id = id, values = values)
    }

    fun hasWon(card: BingoCard, called: List<Int>, mode: GameMode): Boolean {
        val active = called.toSet() + card.marked + 0
        val lineCount = (0 until 5).count { row ->
            (0 until 5).all { col -> (row == 2 && col == 2) || card.value(row, col) in active }
        } + (0 until 5).count { col ->
            (0 until 5).all { row -> (row == 2 && col == 2) || card.value(row, col) in active }
        } + (if ((0 until 5).all { i -> i == 2 || card.value(i, i) in active }) 1 else 0) +
            (if ((0 until 5).all { i -> i == 2 || card.value(i, 4 - i) in active }) 1 else 0)

        val fourStars = listOf(
            card.value(0, 0),
            card.value(0, 4),
            card.value(4, 0),
            card.value(4, 4)
        )
        val hasFourStar = fourStars.all { it in active }

        return when (mode) {
            GameMode.SHORT_ONE_OR_STAR -> lineCount >= 1 || hasFourStar
            GameMode.SHORT_THREE_LINE -> lineCount >= 3
            GameMode.BINGO_1020_CLASSIC -> lineCount >= 1 || hasFourStar
        }
    }

    fun addPlayerCard(state: BingoGameState): BingoGameState {
        if (state.phase != GamePhase.CARD_SELECTION) return state
        if (state.playerCards.size >= 10) return state.copy(statusKey = "max_cards")
        if (state.profile.balanceBirr < config.cardPriceBirr) {
            return state.copy(statusKey = "insufficient_balance")
        }
        val card = makeCard(state.nextCardId)
        val updated = state.entries + PlayerEntry(
            name = state.profile.displayName,
            card = card,
            isCurrentUser = true
        )
        val stats = state.profile.longTermStats
        val updatedProfile = state.profile.copy(
            balanceBirr = state.profile.balanceBirr - config.cardPriceBirr,
            longTermStats = stats.copy(
                totalCardsBought = stats.totalCardsBought + 1,
                totalSpentBirr = stats.totalSpentBirr + config.cardPriceBirr
            )
        )
        return state.copy(
            nextCardId = state.nextCardId + 1,
            entries = updated,
            totalPotBirr = updated.size * config.cardPriceBirr,
            profile = updatedProfile,
            statusKey = "card_added"
        )
    }

    fun addOpponentCard(state: BingoGameState): BingoGameState {
        if (state.phase != GamePhase.CARD_SELECTION) return state
        if (state.entries.size >= 20) return state.copy(statusKey = "max_total_cards")
        val botIndex = state.entries.count { !it.isCurrentUser } + 1
        val card = makeCard(state.nextCardId)
        val updated = state.entries + PlayerEntry(
            name = "Bingo 1020 #${1020 + botIndex}",
            card = card,
            isCurrentUser = false
        )
        return state.copy(
            nextCardId = state.nextCardId + 1,
            entries = updated,
            totalPotBirr = updated.size * config.cardPriceBirr,
            statusKey = "bot_added"
        )
    }

    fun toggleMark(state: BingoGameState, cardId: Int, number: Int): BingoGameState {
        val updated = state.entries.map { item ->
            if (item.card.id == cardId) {
                val nextMarked = if (number in item.card.marked) {
                    item.card.marked - number
                } else {
                    item.card.marked + number
                }
                item.copy(card = item.card.copy(marked = nextMarked))
            } else {
                item
            }
        }
        return state.copy(entries = updated)
    }

    fun startAlarm(state: BingoGameState): BingoGameState {
        if (state.phase != GamePhase.CARD_SELECTION) return state
        val userCardCount = state.playerCards.size.coerceAtLeast(1)
        val roundSpend = userCardCount * config.cardPriceBirr
        val stats = state.profile.longTermStats
        val updatedProfile = state.profile.copy(
            longTermStats = stats.copy(
                roundsPlayed = stats.roundsPlayed + 1,
                totalCardsBought = stats.totalCardsBought + (if (stats.totalCardsBought == 0) userCardCount else 0),
                totalSpentBirr = stats.totalSpentBirr + (if (stats.totalSpentBirr == 0.0) roundSpend else 0.0)
            )
        )
        return state.copy(
            phase = GamePhase.ALARM_COUNTDOWN,
            alarmSecondsLeft = config.alarmCountdownSec,
            profile = updatedProfile,
            statusKey = "alarm_started"
        )
    }

    fun startPlaying(state: BingoGameState): BingoGameState =
        state.copy(
            called = emptyList(),
            winningCardIds = emptyList(),
            winnerPayoutEachBirr = 0.0,
            phase = GamePhase.PLAYING,
            autoCallEnabled = true,
            statusKey = "playing"
        )

    fun drawNextNumber(state: BingoGameState): Pair<BingoGameState, Int?> {
        if (state.phase != GamePhase.PLAYING || state.called.size >= 75) {
            return state to null
        }
        val remaining = (1..75).filterNot { it in state.called }
        val drawn = remaining.random()
        val nextCalled = state.called + drawn
        val baseState = state.copy(called = nextCalled, statusKey = "drawn")
        val winners = baseState.entries.filter { hasWon(it.card, nextCalled, baseState.mode) }
        return if (winners.isNotEmpty()) {
            evaluateWinners(baseState) to drawn
        } else {
            baseState to drawn
        }
    }

    fun evaluateWinners(state: BingoGameState): BingoGameState {
        if (state.phase != GamePhase.PLAYING) return state
        val winners = state.entries.filter { hasWon(it.card, state.called, state.mode) }
        if (winners.isEmpty()) return state.copy(statusKey = "no_winner_yet")

        val totalWinnerPool = state.totalPotBirr * config.winnerShareRatio
        val perWinnerShare = totalWinnerPool / winners.size
        val userWinsCount = winners.count { it.isCurrentUser }
        val earnedBirr = perWinnerShare * userWinsCount

        val stats = state.profile.longTermStats
        val nextStreak = if (userWinsCount > 0) stats.currentStreak + 1 else 0
        val nextBestStreak = maxOf(stats.bestStreak, nextStreak)
        val updatedStats = stats.copy(
            roundsWon = stats.roundsWon + (if (userWinsCount > 0) 1 else 0),
            currentStreak = nextStreak,
            bestStreak = nextBestStreak,
            totalEarnedBirr = stats.totalEarnedBirr + earnedBirr
        )

        return state.copy(
            winningCardIds = winners.map { it.card.id },
            winnerPayoutEachBirr = perWinnerShare,
            profile = state.profile.copy(
                balanceBirr = state.profile.balanceBirr + earnedBirr,
                longTermStats = updatedStats
            ),
            phase = GamePhase.FINISHED,
            autoCallEnabled = false,
            selectionSecondsLeft = config.selectionIntervalSec,
            statusKey = "winners_declared"
        )
    }

    fun prepareNextSelectionCycle(state: BingoGameState): BingoGameState {
        val nextMode = if (state.mode == GameMode.SHORT_THREE_LINE) {
            GameMode.SHORT_ONE_OR_STAR
        } else {
            state.mode
        }
        val freshEntries = listOf(
            PlayerEntry(
                name = state.profile.displayName,
                card = makeCard(state.nextCardId),
                isCurrentUser = true
            )
        )
        return state.copy(
            mode = nextMode,
            nextCardId = state.nextCardId + 1,
            entries = freshEntries,
            totalPotBirr = freshEntries.size * config.cardPriceBirr,
            called = emptyList(),
            winningCardIds = emptyList(),
            winnerPayoutEachBirr = 0.0,
            selectionSecondsLeft = config.selectionIntervalSec,
            phase = GamePhase.CARD_SELECTION,
            statusKey = "new_cycle_selection"
        )
    }
}
