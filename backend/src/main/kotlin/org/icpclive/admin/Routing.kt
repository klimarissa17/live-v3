package org.icpclive.admin

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.icpclive.admin.advertisement.configureAdvertisement
import org.icpclive.admin.overlayEvents.configureOverlayEvents
import org.icpclive.admin.picture.configurePicture
import org.icpclive.admin.queue.configureQueue
import org.icpclive.admin.scoreboard.configureScoreboard
import org.icpclive.admin.statistics.configureStatistics
import org.icpclive.admin.ticker.configureTicker

private lateinit var topLevelLinks: List<Pair<String, String>>

internal fun BODY.adminHead() {
    table {
        tr {
            for ((url, text) in topLevelLinks) {
                td {
                    a(url) { +text }
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.catchAdminAction(back: String, block: ApplicationCall.() -> Unit) = try {
    block()
} catch (e: AdminActionException) {
    respondHtml {
        body {
            h1 {
                +"Error: ${e.message}"
            }
            a {
                href = back
                +"Back"
            }
        }
    }
}


fun Application.configureAdminRouting() {
    routing {
        val advertisementUrls =
            configureAdvertisement(environment!!.config.property("live.presets.advertisements").getString())
        val pictureUrls = configurePicture(environment!!.config.property("live.presets.pictures").getString())
        val overlayEventsUrls = configureOverlayEvents()
        val queueUrls = configureQueue()
        val scoreboardUrls = configureScoreboard()
        val statisticsUrls = configureStatistics()
        val tickerUrls = configureTicker(environment!!.config.property("live.presets.ticker").getString())

        topLevelLinks = listOf(
            advertisementUrls.mainPage to "Advertisement",
            pictureUrls.mainPage to "Picture",
            overlayEventsUrls.mainPage to "Events",
            queueUrls.mainPage to "Queue",
            scoreboardUrls.mainPage to "Scoreboard",
            statisticsUrls.mainPage to "Statistics",
            tickerUrls.mainPage to "Ticker"
        )
        get("/admin") { call.respondRedirect(topLevelLinks[0].first) }
    }
}
