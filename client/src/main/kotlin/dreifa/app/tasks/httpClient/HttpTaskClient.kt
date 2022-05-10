package dreifa.app.tasks.httpClient

import dreifa.app.opentelemetry.ContextHelper
import dreifa.app.opentelemetry.OpenTelemetryContext
import dreifa.app.opentelemetry.OpenTelemetryProvider
import dreifa.app.registry.Registry
import dreifa.app.tasks.AsyncResultChannelSinkLocator
import dreifa.app.tasks.TaskDoc
import dreifa.app.tasks.client.ClientContext
import dreifa.app.tasks.client.TaskClient
import dreifa.app.tasks.httpCommon.BlockingTaskRequest
import dreifa.app.tasks.httpCommon.JavaClass
import dreifa.app.tasks.httpCommon.Serialiser
import dreifa.app.types.UniqueId
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class HttpTaskClient(
    reg: Registry,
    private val baseUrl: String
) : TaskClient {
    private val serializer = Serialiser()
    private val tracer = reg.getOrNull(Tracer::class.java)
    private val provider = reg.getOrNull(OpenTelemetryProvider::class.java)
    override fun <I : Any, O : Any> execAsync(
        ctx: ClientContext,
        taskName: String,
        channelLocator: AsyncResultChannelSinkLocator,
        channelId: UniqueId,
        input: I,
        outputClazz: KClass<O>
    ) {
        TODO("Not yet implemented")
    }

    override fun <I : Any, O : Any> execBlocking(
        ctx: ClientContext,
        taskName: String,
        input: I,
        outputClazz: KClass<O>
    ): O {
        return if (tracer != null && provider != null) {
            // run with telemetry
            return runBlocking {
                val helper = ContextHelper(provider)

                withContext(helper.createContext(ctx.telemetryContext().context()).asContextElement()) {
                    val span = startSpan(taskName)
                    try {
                        val telemetryContext = OpenTelemetryContext.fromSpan(span)
                        val result = makeRemoteCall(ctx.withTelemetryContext(telemetryContext), taskName, input)
                        val deserialized = serializer.deserialiseData(result)

                        if (deserialized.isValue() || deserialized.isNothing()) {
                            completeSpan(span)

                            @Suppress("UNCHECKED_CAST")
                            deserialized.any() as O
                        } else {
                            // the remote side had a problem
                            completeSpan(span, deserialized.exception())
                            throw deserialized.exception()
                        }
                    } catch (ex: Exception) {
                        // the client (this side) had a problem
                        completeSpan(span, ex)
                        throw ex
                    }
                }
            }
        } else {
            // run without telemetry
            try {
                val result = makeRemoteCall(ctx, taskName, input)
                val deserialized = serializer.deserialiseData(result)

                if (deserialized.isValue() || deserialized.isNothing()) {
                    @Suppress("UNCHECKED_CAST")
                    deserialized.any() as O
                } else {
                    // the remote side had a problem
                    throw deserialized.exception()
                }
            } catch (ex: Exception) {
                // the client (this side) had a problem
                throw ex
            }
        }
    }

    private fun <I : Any> makeRemoteCall(
        ctx: ClientContext,
        taskName: String,
        input: I
    ): String {
        val url = buildUrl(baseUrl, ctx, null)
        val model = BlockingTaskRequest(
            task = taskName,
            inputSerialized = inputToJsonString(input),
            loggingChannelLocator = ctx.logChannelLocator().locator,
            correlation = ctx.correlation(),
            telemetryContext = ctx.telemetryContext()
        )
        val body = serializer.serialiseBlockingTaskRequest(model)
        val request = Request(Method.POST, url).body(body)
        return runRequest(request, taskName, 10)
    }

    override fun <I : Any, O : Any> taskDocs(ctx: ClientContext, taskName: String): TaskDoc<I, O> {
        TODO("Not yet implemented")
    }

    private fun <I> inputToJsonString(input: I): String {
        return if (input != null) {
            serializer.serialiseData(input as Any)
        } else {
            ""
        }
    }

    private fun runRequest(request: Request, task: String, timeoutSec: Int = 120): String {
        val client: HttpHandler = apacheClient(timeoutSec)
        val result = client(request)

        if (result.status != Status.OK) {
            throw RuntimeException("opps, status of ${result.status} running ${task}\n${result.bodyString()} at $request")
        } else {
            return result.bodyString()
        }
    }

    private fun startSpan(taskName: String): Span {
        val javaClass = JavaClass(taskName)
        return tracer!!.spanBuilder(javaClass.shortName())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("dreifa.task.qualifiedName", taskName)
            .startSpan()
    }

    private fun completeSpan(span: Span) {
        span.setStatus(StatusCode.OK)
        span.end()
    }

    private fun completeSpan(span: Span, ex: Throwable) {
        span.recordException(ex)
        span.setStatus(StatusCode.ERROR)
        span.end()
    }

    private fun apacheClient(timeoutSec: Int): HttpHandler {
        val closeable = HttpClients.custom().setDefaultRequestConfig(
            RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectTimeout(Timeout.ofMilliseconds(1000))
                .setResponseTimeout(Timeout.of(timeoutSec.toLong(), TimeUnit.SECONDS))
                //.setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
        ).build()

        return ApacheClient(client = closeable)
    }

    private fun buildUrl(
        baseUrl: String,
        @Suppress("UNUSED_PARAMETER") ctx: ClientContext,
        timeout: Int?
    ): String {
        var paramMarker = "?"
        val sb = StringBuilder(baseUrl)
        if (!sb.endsWith("/")) sb.append("/")
        sb.append("api/exec/")
        if (timeout != null) {
            sb.append(paramMarker)
            sb.append("timeout=$timeout")
        }
        return sb.toString()
    }
}