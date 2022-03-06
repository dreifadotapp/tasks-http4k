package dreifa.app.tasks.httpClient

import dreifa.app.registry.Registry
import dreifa.app.sis.JsonSerialiser
import dreifa.app.tasks.logging.LogMessage
import dreifa.app.tasks.logging.LoggingChannelFactory
import dreifa.app.tasks.logging.LoggingChannelLocator
import org.http4k.core.Request
import org.http4k.lens.Path
import org.http4k.routing.*
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsConsumer
import org.http4k.websocket.WsFilter

class LogChannelController(registry: Registry) : RoutingWsHandler {
    private val factory = registry.get(LoggingChannelFactory::class.java)
    private val serializer = JsonSerialiser()
    override fun invoke(p1: Request): WsConsumer {
        return handler.invoke(p1)
    }

    override fun match(request: Request): WsRouterMatch {
        return handler.match(request)
    }

    override fun withBasePath(new: String): RoutingWsHandler {
        return handler.withBasePath(new)
    }

    override fun withFilter(new: WsFilter): RoutingWsHandler {
        return handler.withFilter(new)
    }

    val idLens = Path.of("id")
    private val handler = websockets(
        "/logChannel/{id}/stdout" bind { ws: Websocket ->
            val id = idLens(ws.upgradeRequest)
            ws.onMessage {
                val locator = LoggingChannelLocator.inMemory(id)
                factory.consumer(locator).acceptStdout(it.bodyString())
            }
        },
        "/logChannel/{id}/stderr" bind { ws: Websocket ->
            val id = idLens(ws.upgradeRequest)
            ws.onMessage {
                val locator = LoggingChannelLocator.inMemory(id)
                factory.consumer(locator).acceptStderr(it.bodyString())
            }
        },
        "/logChannel/{id}/log" bind { ws: Websocket ->
            val id = idLens(ws.upgradeRequest)
            ws.onMessage {
                val msg = serializer.deserialiseData(it.bodyString()).data as LogMessage
                val locator = LoggingChannelLocator.inMemory(id)
                factory.consumer(locator).acceptLog(msg)
            }
        }
    )
}