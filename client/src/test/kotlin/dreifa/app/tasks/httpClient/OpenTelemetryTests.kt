package dreifa.app.tasks.httpClient

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import dreifa.app.helpers.random
import dreifa.app.opentelemetry.JaegerOpenTelemetryProvider
import dreifa.app.opentelemetry.analyser
import dreifa.app.registry.Registry
import dreifa.app.tasks.client.SimpleClientContext
import dreifa.app.tasks.demo.CalcSquareTask
import dreifa.app.tasks.demo.ExceptionGeneratingBlockingTask
import dreifa.app.tasks.httpServer.TheServerApp
import dreifa.app.tasks.logging.DefaultLoggingChannelFactory
import dreifa.app.tasks.logging.InMemoryLoggingRepo
import dreifa.app.types.CorrelationContext
import dreifa.app.types.CorrelationContexts
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.StatusData
import org.junit.jupiter.api.*
import java.lang.RuntimeException


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenTelemetryTests {
    // share a single provider across server and client - makes it easier to verify
    private val provider = JaegerOpenTelemetryProvider(true, "tasks-http4k")

    private val serverReg = Registry()
    private val serverTracer = provider.sdk().getTracer("Server")
    private val theServer: TheServerApp

    init {
        serverReg.store(provider).store(serverTracer)   // share the same provider for easy end to end testing
        theServer = TheServerApp(serverReg)
    }

    lateinit var testInfo: TestInfo

    @BeforeEach
    fun init(testInfo: TestInfo?) {
        this.testInfo = testInfo!!
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
    fun `should call remote blocking task with telemetry`() {
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
        assertThat(clientSpan.status, equalTo(StatusData.ok()))
        assertThat(clientSpan.name, equalTo("CalcSquareTask"))
        val serverSpan = spansAnalyser[1]
        assertThat(serverSpan.kind, equalTo(SpanKind.SERVER))
        assertThat(serverSpan.status, equalTo(StatusData.ok()))
        assertThat(serverSpan.name, equalTo("CalcSquareTask"))
    }

    @Test
    fun `should record exception in telemetry when remote task fails`() {
        // 1. setup
        val testCtx = init()

        // 2. test
        val client = HttpTaskClient(testCtx.clientReg, "http://localhost:1234")
        val ctx = SimpleClientContext(correlation = testCtx.correlation)
        assertThrows<RuntimeException> {
            client.execBlocking(ctx,
                ExceptionGeneratingBlockingTask::class.qualifiedName!!,
                "An Exception", String::class)
        }
        //assertThat(result, equalTo(100))

        // 3. verify telemetry
        val correlation = testCtx.correlation.first()
        val spansAnalyser = provider.spans().analyser()
            .filterHasAttributeValue(correlation.openTelemetryAttrName, correlation.id.id)
        assertThat(spansAnalyser.traceIds().size, equalTo(1))
        assertThat(spansAnalyser.spanIds().size, equalTo(2))
        val clientSpan = spansAnalyser[0]
        assertThat(clientSpan.kind, equalTo(SpanKind.CLIENT))
        assertThat(clientSpan.status, equalTo(StatusData.create(StatusCode.ERROR,"An Exception")))
        assertThat(clientSpan.name, equalTo("ExceptionGeneratingBlockingTask"))
        val serverSpan = spansAnalyser[1]
        assertThat(serverSpan.kind, equalTo(SpanKind.SERVER))
        assertThat(serverSpan.status, equalTo(StatusData.create(StatusCode.ERROR,"An Exception")))
        assertThat(serverSpan.name, equalTo("ExceptionGeneratingBlockingTask"))
    }

    //ExceptionGeneratingBlockingTask

    private fun init(): TestContext {
        val clientReg = Registry()
        val clientTracer = provider.sdk().getTracer("Client")
        val inMemoryLogging = InMemoryLoggingRepo()
        clientReg.store(provider).store(clientTracer).store(inMemoryLogging)

        // is this needed ?
        val logChannelFactory = DefaultLoggingChannelFactory(clientReg)
        clientReg.store(logChannelFactory)

        val client = HttpTaskClient(clientReg, "http://localhost:1234")
        // include test details in the correlation context
        val testId = CorrelationContext("testId", String.random())
        val testName = CorrelationContext("testName", testInfo.displayName.replace(' ', '-').removeSuffix("()"))
        val testClass = CorrelationContext("testClass", this::class.java.simpleName)
        val correlationContext = CorrelationContexts(listOf(testId, testName, testClass))
        return TestContext(clientReg, client, correlationContext)
    }

    data class TestContext(
        val clientReg: Registry,
        val client: HttpTaskClient,
        val correlation: CorrelationContexts
    )
}