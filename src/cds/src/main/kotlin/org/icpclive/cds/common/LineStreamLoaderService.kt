package org.icpclive.cds.common

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import org.icpclive.util.getLogger
import java.nio.file.Paths

fun getLineStreamLoaderFlow(url: String, auth: ClientAuth?) = flow {
    val httpClient = defaultHttpClient(auth)
    if (!isHttpUrl(url)) {
        Paths.get(url).toFile().useLines { lines ->
            lines.forEach { emit(it) }
        }
        return@flow
    }

    logger.debug("Requesting $url")
    httpClient.prepareGet(url) {
        timeout {
            socketTimeoutMillis = Long.MAX_VALUE
            requestTimeoutMillis = Long.MAX_VALUE
        }
    }.execute { httpResponse ->
        if (httpResponse.status != HttpStatusCode.OK) {
            logger.warn("Got ${httpResponse.status} from $url")
            return@execute
        }
        val channel = httpResponse.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: continue
            if (line.isEmpty()) continue
            emit(line)
        }
    }
}.flowOn(Dispatchers.IO)

object LineStreamLoaderService
val logger = getLogger(LineStreamLoaderService::class)