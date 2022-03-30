package org.icpclive.adminapi

import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.icpclive.api.Widget
import org.icpclive.data.WidgetManager

class WidgetWrapper<WidgetType : Widget>(
        private val createWidget: (Parameters) -> WidgetType) {
    private val mutex = Mutex()

    private var widgetId: String? = null

    val status
        get() = widgetId != null

    suspend fun show(parameters: Parameters) = mutex.withLock {
        if (widgetId != null)
            return
        val widget = createWidget(parameters)
        WidgetManager.showWidget(widget)
        widgetId = widget.widgetId
    }

    suspend fun hide() = mutex.withLock {
        widgetId?.let {
            WidgetManager.hideWidget(it)
        }
        widgetId = null
    }
}