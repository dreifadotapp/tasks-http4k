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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenTelemetryTests {
    val serverReg = Registry()
    val provider = ZipKinOpenTelemetryProvider()
    val serverTracer = provider.sdk().getTracer("OpenTelemetryTests")
    val theServer: TheServerApp

    init {
        serverReg.store(provider).store(serverTracer)   // share the same provider for easy end to end testing
        theServer = TheServerApp(serverReg)

    }


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
        val testCtx = init()

        // 2. test
        val client = HttpTaskClient(testCtx.clientReg, "http://localhost:1234")
        val ctx = SimpleClientContext(correlation = testCtx.correlation)
        val result = client.execBlocking(ctx, CalcSquareTask::class.qualifiedName!!, 10, Int::class)
        val correlation = testCtx.correlation.first()

        // 3. verify
        assertThat(result, equalTo(100))
        val spansAnalyser = testCtx.provider.spans().analyser()
            .filterHasAttributeValue(correlation.openTelemetryAttrName, correlation.id.id)
        assertThat(spansAnalyser.traceIds().size, equalTo(1))
        assertThat(spansAnalyser.spanIds().size, equalTo(1))

    }


    private fun init(): TestContext {
        val clientReg = Registry()
        //val provider = ZipKinOpenTelemetryProvider()
        val clientTracer = provider.sdk().getTracer("OpenTelemetryTests")
        val inMemoryLogging = InMemoryLoggingRepo()
        val correlationContext = CorrelationContexts.single("testId", String.random())
        clientReg.store(provider).store(clientTracer).store(inMemoryLogging)

        //val serverReg = Registry()
        //val serverTracer = provider.sdk().getTracer("OpenTelemetryTests")
        //serverReg.store(provider).store(serverTracer)   // share the same provider for easy end to end testing

        // is this needed ?
        val logChannelFactory = DefaultLoggingChannelFactory(clientReg)
        clientReg.store(logChannelFactory)

        val client = HttpTaskClient(clientReg, "http://localhost:1234")

        return TestContext(clientReg, serverReg, client, provider, correlationContext)
    }

    data class TestContext(
        val clientReg: Registry,
        val serverReg: Registry,
        val client: HttpTaskClient,
        val provider: ZipKinOpenTelemetryProvider,
        val correlation: CorrelationContexts
        //val tracer: Tracer
    )
}