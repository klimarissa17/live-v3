package org.icpclive.adminapi.queue

import io.ktor.routing.*
import org.icpclive.adminapi.setupSimpleWidgetRouting
import org.icpclive.api.QueueSettings
import org.icpclive.api.QueueWidget


fun Routing.configureQueueApi() =
        setupSimpleWidgetRouting<QueueSettings, QueueWidget>(
                prefix = "queue",
                createWidget = { QueueWidget(it) }
        )