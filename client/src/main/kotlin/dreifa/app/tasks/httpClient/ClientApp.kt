package dreifa.app.tasks.httpClient

import dreifa.app.registry.Registry
import dreifa.app.tasks.logging.DefaultLoggingChannelFactory
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    // todo - how to inject in ?
    //   - server listner bindings
    val registry = Registry()
    TheClientApp(registry, 12345)
}

class TheClientApp(
    registry: Registry = Registry(),
    private val port: Int = 12345
) {
    private val server: Http4kServer

    init {
        registry.store(DefaultLoggingChannelFactory(registry))
        server = LogChannelController(registry).asServer(Jetty(port)).start()
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop()
    }

    fun baseUrl(): String = "ws://localhost:$port"
}
