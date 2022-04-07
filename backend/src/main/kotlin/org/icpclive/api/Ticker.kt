@file:Suppress("UNUSED")

package org.icpclive.api


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.icpclive.api.GenerateId

@Serializable
sealed class TickerMessage(
    override val id: String,
    val part: TickerPart,
    val periodMs: Long
) : TypeWithId

@Serializable
@SerialName("text")
class TextTickerMessage(val settings: TextTickerSettings) :
    TickerMessage(GenerateId(TICKER_ID_PREFIX), settings.part, settings.periodMs) {
    companion object {
        val TICKER_ID_PREFIX: String = "ticker_text"
    }
}

@Serializable
@SerialName("clock")
class ClockTickerMessage(val settings: ClockTickerSettings) :
    TickerMessage(GenerateId(TICKER_ID_PREFIX), settings.part, settings.periodMs) {
    companion object {
        val TICKER_ID_PREFIX: String = "ticker_clock"
    }
}

@Serializable
@SerialName("scoreboard")
class ScoreboardTickerMessage(val settings: ScoreboardTickerSettings) :
    TickerMessage(GenerateId(TICKER_ID_PREFIX), settings.part, settings.periodMs) {
    companion object {
        val TICKER_ID_PREFIX: String = "ticker_scoreboard"
    }
}