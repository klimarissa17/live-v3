@file:Suppress("unused")

package org.icpclive

import ClicsExporter
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.groups.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import com.github.ajalt.mordant.rendering.TextColors
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.icpclive.api.ContestInfo
import org.icpclive.api.RunInfo
import org.icpclive.api.tunning.AdvancedProperties
import org.icpclive.api.tunning.TeamInfoOverride
import org.icpclive.cds.ContestUpdate
import org.icpclive.cds.adapters.*
import org.icpclive.cds.settings.parseFileToCdsSettings
import org.icpclive.export.icpc.csv.IcpcCsvExporter
import org.icpclive.util.*
import org.icpclive.org.icpclive.export.pcms.PCMSExporter
import org.slf4j.Logger
import org.slf4j.event.Level
import java.nio.file.Paths
import java.time.Duration
import kotlin.io.path.*
import kotlin.system.exitProcess

object CommonOptions : OptionGroup("Common options") {
    val configDirectory by option(
        "-c", "--config-directory",
        help = "Path to config directory"
    ).path(mustExist = true, canBeFile = false, canBeDir = true).required()
    val credsFile by option(
        "--creds",
        help = "Path to file with credentials"
    ).path(mustExist = true, canBeFile = true, canBeDir = false)
    val advancedJsonPath by option("--advanced-json", help = "Path to advanced.json")
        .path(mustExist = true, canBeFile = true, canBeDir = false)
        .defaultLazy("configDirectory/advanced.json") { configDirectory.resolve("advanced.json") }
}

abstract class DumpFileCommand(
    name: String,
    help: String,
    defaultFileName: String,
    outputHelp: String
) : CliktCommand(name = name, help = help, printHelpOnEmptyArgs = true) {
    abstract fun format(info: ContestInfo, runs: Map<Int, List<RunInfo>>): String

    val commonOptions by CommonOptions
    private val output by option("-o", "--output", help = outputHelp).path().convert {
        if (it.isDirectory()) {
            it.resolve(defaultFileName)
        } else {
            it
        }
    }.required()
        .check({ "Directory ${it.absolute().parent} doesn't exist"}) { it.absolute().parent.isDirectory() }

    open fun Flow<ContestUpdate>.postprocess() = this


    override fun run() {
        val logger = getLogger(DumpFileCommand::class)
        logger.info("Would save result to ${output}")
        val flow = getFlow(
            fileJsonContentFlow<AdvancedProperties>(CommonOptions.advancedJsonPath, logger, AdvancedProperties()),
            logger
        )
        val data = runBlocking {
            logger.info("Waiting till contest become finalized...")
            val result = flow.postprocess().finalContestState()
            logger.info("Loaded contest data")
            result
        }
        val dump = format(
            data.infoAfterEvent!!,
            data.runs.values.groupBy { it.teamId },
        )
        output.toFile().printWriter().use {
            it.println(dump)
        }
    }
}

object PCMSDumpCommand : DumpFileCommand(
    name = "pcms",
    help = "Dump pcms xml",
    outputHelp = "Path to new xml file",
    defaultFileName = "standings.xml"
) {
    override fun format(info: ContestInfo, runs: Map<Int, List<RunInfo>>) = PCMSExporter.format(info, runs)
}

object IcpcCSVDumpCommand : DumpFileCommand(
    name = "icpc-csv",
    help = "Dump csv for icpc.global",
    outputHelp = "Path to new csv file",
    defaultFileName = "standings.csv"
) {
    val teamsMapping by option("--teams-map", help = "mapping from cds team id to icpc team id")
        .file(canBeFile = true, canBeDir = false, mustExist = true)

    override fun format(info: ContestInfo, runs: Map<Int, List<RunInfo>>) = IcpcCsvExporter.format(info, runs)
    override fun Flow<ContestUpdate>.postprocess(): Flow<ContestUpdate> {
        val mappingFile = teamsMapping
        if (mappingFile == null) {
            return this
        } else {
            val parser = CSVParser(mappingFile.inputStream().reader(), CSVFormat.TDF)
            val map = parser.records.associate {
                it[1]!! to it[0]!!
            }
            val advanced = AdvancedProperties(
                teamOverrides = map.mapValues {
                    TeamInfoOverride(
                        customFields = mapOf("icpc_id" to it.value)
                    )
                }
            )
            return applyAdvancedProperties(flow { emit(advanced) })
        }
    }
}


object ServerCommand : CliktCommand(name = "server", help = "Start as http server", printHelpOnEmptyArgs = true) {
    val commonOptions by CommonOptions
    val port: Int by option("-p", "--port", help = "Port to listen").int().default(8080)
    val ktorArgs by option("--ktor-arg", help = "Arguments to forward to ktor server").multiple()

    override fun run() {
        io.ktor.server.netty.EngineMain.main((listOf("-port=$port") + ktorArgs).toTypedArray())
    }
}

object MainCommand : CliktCommand(name = "java -jar cds-converter.jar", invokeWithoutSubcommand = true, treatUnknownOptionsAsArgs = true) {
    init {
        context {
            helpFormatter = { MordantHelpFormatter(it, showRequiredTag = true, showDefaultValues = true)}
        }
    }
    val unused by argument().multiple()
    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            if (unused.isNotEmpty()) {
                currentContext.terminal.danger("Unknown command ${unused.firstOrNull()}")
                currentContext.terminal.info("")
            }
            throw PrintHelpMessage(currentContext, true)
        }
    }
}

fun main(args: Array<String>): Unit = MainCommand.subcommands(
    PCMSDumpCommand,
    ServerCommand,
    IcpcCSVDumpCommand
).main(args)


private fun Application.setupKtorPlugins() {
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }
    install(StatusPages) {
        exception<Throwable> { call, ex ->
            call.application.environment.log.error("Query ${call.url()} failed with exception", ex)
            throw ex
        }
    }
    install(AutoHeadResponse)
    install(IgnoreTrailingSlash)
    install(ContentNegotiation) { json(defaultJsonSettings()) }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(CORS) {
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("*")
        allowMethod(HttpMethod.Delete)
        allowSameOrigin = true
        anyHost()
    }
}

@Suppress("unused") // application.yaml references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    setupKtorPlugins()

    val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
        environment.log.error("Uncaught exception in coroutine context $coroutineContext", throwable)
        exitProcess(1)
    }

    val loaded = getFlow(
        fileJsonContentFlow<AdvancedProperties>(CommonOptions.advancedJsonPath, environment.log, AdvancedProperties()),
        environment.log
    ).shareIn(this + handler, SharingStarted.Eagerly, Int.MAX_VALUE)

    routing {
        with (ClicsExporter) {
            route("/clics") {
                setUp(application + handler, loaded)
            }
        }
        with (PCMSExporter) {
            route("/pcms") {
                setUp(application + handler, loaded)
            }
        }
    }

    log.info("Configuration is done")
}

private fun getFlow(advancedProperties: Flow<AdvancedProperties>, log: Logger) : Flow<ContestUpdate> {
    log.info("Using config directory ${CommonOptions.configDirectory}")
    log.info("Current working directory is ${Paths.get("").toAbsolutePath()}")
    val path = CommonOptions.configDirectory.resolve("events.properties")
        .takeIf { it.exists() }
        ?.also { log.warn("Using events.properties is deprecated, use settings.json instead.") }
        ?: CommonOptions.configDirectory.resolve("settings.json5").takeIf { it.exists() }
        ?: CommonOptions.configDirectory.resolve("settings.json")
    val creds: Map<String, String> = CommonOptions.credsFile?.let {
        Json.decodeFromStream(it.toFile().inputStream())
    } ?: emptyMap()
    return parseFileToCdsSettings(path, creds)
        .toFlow()
        .applyAdvancedProperties(advancedProperties)
        .contestState()
        .filterUseless()
        .map { it.event }
        .processHiddenTeamsAndGroups()
}