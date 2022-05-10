package dreifa.app.tasks.httpClient

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dreifa.app.helpers.random
import dreifa.app.opentelemetry.ZipKinOpenTelemetryProvider
import dreifa.app.opentelemetry.analyser
import dreifa.app.registry.Registry
import dreifa.app.tasks.client.SimpleClientContext
import dreifa.app.tasks.demo.CalcSquareTask
import dreifa.app.tasks.httpServer.TheServerApp
import dreifa.app.tasks.logging.DefaultLoggingChannelFactory
import dreifa.app.tasks.logging.InMemoryLoggingRepo
import dreifa.app.types.CorrelationContexts
import io.opentelemetry.api.trace.SpanKind
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenTelemetryTests {
    // share a single provider across server and client - makes it easier to verify
    private val provider = ZipKinOpenTelemetryProvider()

    private val serverReg = Registry()
    private val serverTracer = provider.sdk().getTracer("Server")
    private val theServer: TheServerApp
    init {
        serverReg.store(provider).store(serverTracer)   // share the same provider for easy end to end testing
        theServer = TheServerApp(serverReg)
    }

    @BeforeAll
    fun `start`() {
        theServer.start()
    }

    @AfterAll
    fun `stop`() {
        theServer.stop()
    }

    @Test
    fun `should call blocking task with telemetry`() {
        // 1. setup
        val testCtx = init()

        // 2. test
        val client = HttpTaskClient(testCtx.clientReg, "http://localhost:1234")
        val ctx = SimpleClientContext(correlation = testCtx.correlation)
        val result = client.execBlocking(ctx, CalcSquareTask::class.qualifiedName!!, 10, Int::class)
        assertThat(result, equalTo(100))

        // 3. verify telemetry
        val correlation = testCtx.correlation.first()
        val spansAnalyser = provider.spans().analyser()
            .filterHasAttributeValue(correlation.openTelemetryAttrName, correlation.id.id)
        assertThat(spansAnalyser.traceIds().size, equalTo(1))
        assertThat(spansAnalyser.spanIds().size, equalTo(2))
        val clientSpan = spansAnalyser[0]
        assertThat(clientSpan.kind, equalTo(SpanKind.CLIENT))
        assertThat(clientSpan.name, equalTo("CalcSquareTask"))
        val serverSpan = spansAnalyser[1]
        assertThat(serverSpan.kind, equalTo(SpanKind.SERVER))
        assertThat(serverSpan.name, equalTo("CalcSquareTask"))
    }

    private fun init(): TestContext {
        val clientReg = Registry()
        val clientTracer = provider.sdk().getTracer("Client")
        val inMemoryLogging = InMemoryLoggingRepo()
        val correlationContext = CorrelationContexts.single("testId", String.random())
        clientReg.store(provider).store(clientTracer).store(inMemoryLogging)

        // is this needed ?
        val logChannelFactory = DefaultLoggingChannelFactory(clientReg)
        clientReg.store(logChannelFactory)

        val client = HttpTaskClient(clientReg, "http://localhost:1234")
        return TestContext(clientReg, client, correlationContext)
    }

    data class TestContext(
        val clientReg: Registry,
        val client: HttpTaskClient,
        val correlation: CorrelationContexts
    )
}