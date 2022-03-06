package dreifa.app.tasks.httpServer

import dreifa.app.registry.Registry
import dreifa.app.tasks.logging.*
import java.lang.RuntimeException

/**
 * The server side LoggingChannelFactory
 * This will find a local consumer that calls back to the client via websockets
 */
class ServerLoggingFactory(registry: Registry) : LoggingChannelFactory {
    private val defaultFactory = DefaultLoggingChannelFactory(registry)

    override fun consumer(locator: LoggingChannelLocator): LoggingConsumerContext {
        return try {
            defaultFactory.consumer(locator)
        } catch (re: RuntimeException) {
            if (locator.locator.startsWith("WS;")) {
                buildLocalWSConsumer(locator.locator)
            } else {
                throw RuntimeException("opps")
            }
        }
    }

    private fun buildLocalWSConsumer(locator: String): LoggingConsumerContext {
        val url = locator.split(";")[1]
        val id = locator.split(";")[2]
        return WsCallbackLoggingConsumerContext(url, id)
    }
}