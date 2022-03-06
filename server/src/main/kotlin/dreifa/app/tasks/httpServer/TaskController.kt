package dreifa.app.tasks.httpServer

import dreifa.app.registry.Registry
import dreifa.app.tasks.BlockingTask
import dreifa.app.tasks.TaskFactory
import dreifa.app.tasks.executionContext.SimpleExecutionContext
import org.http4k.core.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import kotlin.reflect.KFunction1
import dreifa.app.tasks.httpCommon.Serialiser
import dreifa.app.tasks.httpCommon.json
import dreifa.app.tasks.httpCommon.text
import dreifa.app.tasks.logging.*


class TaskController(registry: Registry) : HttpHandler {
    private val serializer = registry.getOrElse(Serialiser::class.java, Serialiser())
    private val taskFactory = registry.get(TaskFactory::class.java)
    private val loggingChannelFactory = registry.get(LoggingChannelFactory::class.java)

    private val routes: RoutingHttpHandler = routes(
        "/api" bind routes(
            "/status" bind Method.GET to {
                Response.text("running")
            },
            "/exec" bind Method.POST to {
                exceptionWrapper(::handleExecTask, it)
            }
        ),
        "/ping" bind Method.GET to {
            Response.text("pong")
        }
    )

    private fun handleExecTask(it: Request): Response {
        val taskRequest = serializer.deserialiseBlockingTaskRequest(it.bodyString())

        @Suppress("UNCHECKED_CAST")
        val task = taskFactory.createInstance(taskRequest.task) as BlockingTask<Any, Any>
        val inputDeserialised = serializer.deserialiseData(taskRequest.inputSerialized)

        val loggingConsumerContext = loggingChannelFactory.consumer(LoggingChannelLocator(taskRequest.loggingChannelLocator))
        val producerContext = LoggingProducerToConsumer(loggingConsumerContext)

        val ctx = SimpleExecutionContext(loggingProducerContext = producerContext)

        return try {
            val output = task.exec(ctx, inputDeserialised.any())
            val outputSerialised = serializer.serialiseData(output)
            Response.json(outputSerialised)
        } catch (ex: Exception) {
            val exceptionSerialised = serializer.serialiseData(ex)
            Response.json(exceptionSerialised)
        }
    }

    private fun exceptionWrapper(x: KFunction1<Request, Response>, i: Request): Response {
        return try {
            x.invoke(i)
        } catch (ex: Exception) {
            ex.printStackTrace()
            Response(Status.INTERNAL_SERVER_ERROR).text(ex.message!!)
        }
    }

    override fun invoke(p1: Request): Response = routes(p1)

}