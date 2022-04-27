package dreifa.app.tasks.httpClient

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dreifa.app.opentelemetry.ZipKinOpenTelemetryProvider
import dreifa.app.opentelemetry.analyser
import dreifa.app.registry.Registry
import dreifa.app.tasks.client.SimpleClientContext
import dreifa.app.tasks.demo.CalcSquareTask
import dreifa.app.tasks.httpServer.TheServerApp
import dreifa.app.tasks.logging.DefaultLoggingChannelFactory
import dreifa.app.tasks.logging.InMemoryLoggingRepo
import io.opentelemetry.api.trace.Tracer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenTelemetryTests {
    private val theServer = TheServerApp()

    // client side (for callback)
    //private val registry = Registry().store(InMemoryLoggingRepo())// need a common InMemoryLoggingRepo
    //private val theClient = TheClientApp(registry)

    @BeforeAll
    fun `start`() {
        theServer.start()
        //theClient.start()
    }

    @AfterAll
    fun `stop`() {
        theServer.stop()
        //theClient.stop()
    }

    @Test
    fun `should call blocking task with telemetry`() {
        // 1. setup
        val (reg, provider, _) = init()

        // 2. test
        val client = HttpTaskClient(reg, "http://localhost:1234")
        val ctx = SimpleClientContext()
        val result = client.execBlocking(ctx, CalcSquareTask::class.qualifiedName!!, 10, Int::class)

        // 3. verify
        assertThat(result, equalTo(100))
        val spansAnalyser = provider.spans().analyser()
        assertThat(spansAnalyser.traceIds().size, equalTo(1))
        assertThat(spansAnalyser.spanIds().size, equalTo(1))

    }


    private fun init(): Triple<Registry, ZipKinOpenTelemetryProvider, Tracer> {
        val reg = Registry()
        val provider = ZipKinOpenTelemetryProvider()
        val tracer = provider.sdk().getTracer("OpenTelemetryScenarios")
        val inMemoryLogging = InMemoryLoggingRepo()
        reg.store(provider).store(tracer).store(inMemoryLogging)

        // is this needed ?
        val logChannelFactory = DefaultLoggingChannelFactory(reg)
        reg.store(logChannelFactory)

        return Triple(reg, provider, tracer)
    }
}