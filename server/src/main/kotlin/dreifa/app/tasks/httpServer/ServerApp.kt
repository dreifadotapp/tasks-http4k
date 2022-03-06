package dreifa.app.tasks.httpServer

import dreifa.app.registry.Registry
import dreifa.app.tasks.TaskFactory
import dreifa.app.tasks.demo.DemoTasks
import dreifa.app.tasks.demo.echo.EchoTasks
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    // todo - how to inject in ?
    //   - server listner bindings
    //   - a list of TaskRegistrations
    val registry = Registry()
    TheServerApp(registry, 1234)
}


class TheServerApp(registry: Registry = Registry(), port: Int = 1234) {
    private val server: Http4kServer

    init {
        val taskFactory = TaskFactory()
        taskFactory.register(DemoTasks())
        taskFactory.register(EchoTasks())
        registry.store(ServerLoggingFactory(registry))
        registry.store(taskFactory)

        server = TaskController(registry).asServer(Jetty(port))
        println("Server started on $port")
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop()
    }
}