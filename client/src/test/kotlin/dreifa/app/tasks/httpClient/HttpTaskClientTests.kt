package dreifa.app.tasks.httpClient

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import dreifa.app.helpers.random
import dreifa.app.registry.Registry
import dreifa.app.tasks.client.SimpleClientContext
import dreifa.app.tasks.demo.CalcSquareTask
import dreifa.app.tasks.demo.echo.*
import dreifa.app.tasks.httpServer.TheServerApp
import dreifa.app.tasks.logging.InMemoryLoggingRepo
import dreifa.app.tasks.logging.LoggingChannelLocator
import dreifa.app.tasks.logging.LoggingReaderContext
import dreifa.app.tasks.logging.LoggingReaderFactory
import org.junit.jupiter.api.*
import java.math.BigDecimal
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpTaskClientTests {
    // server side
    private val theServer = TheServerApp()

    // client side (for callback)
    private val registry = Registry().store(InMemoryLoggingRepo())// need a common InMemoryLoggingRepo
    private val theClient = TheClientApp(registry)

    @BeforeAll
    fun `start`() {
        theServer.start()
        theClient.start()
    }

    @AfterAll
    fun `stop`() {
        theServer.stop()
        theClient.stop()
    }

    @Test
    fun `should call blocking task`() {
        val client = HttpTaskClient("http://localhost:1234")
        val ctx = SimpleClientContext()

        val result = client.execBlocking(
            ctx, CalcSquareTask::class.qualifiedName!!, 10, Int::class
        )
        assertThat(result, equalTo(100))
    }

    @Test
    fun `should serialise echoed output back to original input`() {
        // basically we check "round-tripping" via the serialiser, i.e. do we get back the
        // original inpout
        val random = Random()
        val combinations = listOf(
            Pair(random.nextLong(), "EchoLongTask"),
            Pair(random.nextInt(), "EchoIntTask"),
            Pair(random.nextDouble(), "EchoDoubleTask"),
            Pair(random.nextFloat(), "EchoFloatTask"),
            Pair(random.nextBoolean(), "EchoBooleanTask"),
            Pair(BigDecimal(random.nextDouble()), "EchoBigDecimalTask"),
            Pair(UUID.randomUUID(), "EchoUUIDTask"),
            Pair(Colour.random(), "EchoEnumTask"),
            Pair(DemoModel(), "EchoDemoModelTask")
        )
        val client = HttpTaskClient("http://localhost:1234")
        val ctx = SimpleClientContext()

        combinations.forEach {
            try {
                val param = it.first
                val result = client.execBlocking(
                    ctx, "dreifa.app.tasks.demo.echo.${it.second}", param, param::class
                )
                assertThat(result, equalTo(param)) { "Didn't echo correct value for combination: $it" }
            } catch (ex: Exception) {
                fail { "Combination $it failed with ${ex.message}" }
            }
        }
    }

    @Test
    fun `should return stdout to client`() {
        val client = HttpTaskClient("http://localhost:1234")
        val loggingChannelId = String.random()
        val loggingChannelLocator = LoggingChannelLocator("WS;${theClient.baseUrl()};$loggingChannelId")
        val ctx = SimpleClientContext(loggingChannelLocator = loggingChannelLocator)

        client.execBlocking(
            ctx, "dreifa.app.tasks.demo.echo.EchoToStdOutTask",
            "Hello, world\n",
            Unit::class
        )

        // give it time to call back
        Thread.sleep(10L)

        val readerFactory = registry.get(LoggingReaderFactory::class.java)
        val localLocator = LoggingChannelLocator.inMemory(loggingChannelId)
        val readerContext: LoggingReaderContext = readerFactory.query(localLocator)
        assertThat(readerContext.stdout(), equalTo("Hello, world\n"))
        assertThat(readerContext.stderr(), isEmptyString)
        assertThat(readerContext.messages(), isEmpty)
    }

    @Test
    fun `should return stderr to client`() {
        val client = HttpTaskClient("http://localhost:1234")
        val loggingChannelId = String.random()
        val loggingChannelLocator = LoggingChannelLocator("WS;${theClient.baseUrl()};$loggingChannelId")
        val ctx = SimpleClientContext(loggingChannelLocator = loggingChannelLocator)

        client.execBlocking(
            ctx, "dreifa.app.tasks.demo.echo.EchoToStdErrTask",
            "Opps\n",
            Unit::class
        )

        // give it time to call back
        Thread.sleep(10L)

        val readerFactory = registry.get(LoggingReaderFactory::class.java)
        val localLocator = LoggingChannelLocator.inMemory(loggingChannelId)
        val readerContext: LoggingReaderContext = readerFactory.query(localLocator)
        assertThat(readerContext.stdout(), isEmptyString)
        assertThat(readerContext.stderr(), equalTo("Opps\n"))
        assertThat(readerContext.messages(), isEmpty)
    }
}