package dreifa.app.tasks.client

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import dreifa.app.tasks.logging.LoggingReaderContext

// these are the tests that should work for any TaskClient
abstract class BaseTaskClientTest {

    fun assertNoOutput(ctx: SimpleClientContext) {
        //assertNoOutput(ctx.inMemoryLoggingContext())
    }

    fun assertNoOutput(reader: LoggingReaderContext) {
        assertThat(reader.messages(), isEmpty)
        assertThat(reader.stdout(), isEmptyString)
        assertThat(reader.stdout(), isEmptyString)
    }

    fun assertPartialLogMessage(ctx: SimpleClientContext, message: String) {
        //assertPartialLogMessage(ctx.inMemoryLoggingContext(), message)
    }

    fun assertPartialLogMessage(reader: LoggingReaderContext, message: String) {
        // todo - badly named
        assertThat(reader.messages().filter { it.body.contains(message) }.size, equalTo(1))
    }

}