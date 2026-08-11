package me.chosante.marketserver

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import me.chosante.marketserver.capture.CaptureConfig
import me.chosante.marketserver.capture.CaptureService
import me.chosante.marketserver.db.DatabaseFactory
import me.chosante.marketserver.plugins.installPlugins
import me.chosante.marketserver.routes.configureRouting

fun main() {
    embeddedServer(Netty, port = Config.httpPort, module = Application::module).start(wait = true)
}

fun Application.module(
    dbPath: String = Config.dbPath,
    captureService: CaptureService = CaptureService(CaptureConfig()),
) {
    installPlugins()
    val database = DatabaseFactory.init(dbPath)
    configureRouting(database, dbPath, captureService)
}
