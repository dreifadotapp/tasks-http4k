package dreifa.app.tasks.httpServer

import dreifa.app.sis.JsonSerialiser
import dreifa.app.tasks.logging.LogMessage
import dreifa.app.tasks.logging.LoggingConsumerContext
import org.http4k.client.WebsocketClient
import org.http4k.core.Uri
import org.http4k.websocket.WsMessage

class WsCallbackLoggingConsumerContext(
    private val baseUrl: String,
    private val channelId: String
) : LoggingConsumerContext {
    private val serializer = JsonSerialiser()

    override fun acceptLog(msg: LogMessage) {
        val json = serializer.toPacket(msg)
        makeWSCall(Uri.of("${baseUrl}/logChannel/${channelId}/log"), json)
    }

    override fun acceptStderr(error: String) {
        makeWSCall(Uri.of("${baseUrl}/logChannel/${channelId}/stderr"), error)
    }

    override fun acceptStdout(output: String) {
        val uri = Uri.of("${baseUrl}/logChannel/${channelId}/stdout")
        makeWSCall(uri, output)
    }

    private fun makeWSCall(uri: Uri, output: String) {
        val blockingClient = WebsocketClient.blocking(uri)
        blockingClient.send(WsMessage(output))
        blockingClient.close()
    }
}