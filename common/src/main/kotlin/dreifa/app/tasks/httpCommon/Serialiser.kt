package dreifa.app.tasks.httpCommon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dreifa.app.sis.JsonSerialiser
import dreifa.app.sis.SerialisationPacket

class Serialiser {
    private val mapper: ObjectMapper = ObjectMapper()
    private val rss = JsonSerialiser()

    init {
        val module = KotlinModule()
        mapper.registerModule(module)
    }

    fun serialiseData(data: Any): String {
        return rss.toPacket(data)
    }

    fun deserialiseData(serialised: String): SerialisationPacket {
        return rss.fromPacket(serialised)
    }


    fun serialiseBlockingTaskRequest(model: BlockingTaskRequest): String {
        return mapper.writeValueAsString(model)
    }

    fun deserialiseBlockingTaskRequest(json: String): BlockingTaskRequest {
        return mapper.readValue(json, BlockingTaskRequest::class.java)
    }
}

interface MapSerializable {
    fun toMap(): Map<String, Any>
    fun fromMap(map: Map<String, Any>): Any
}

data class WsCallbackLoggingContext(val baseUrl: String, val channelId: String)


data class BlockingTaskRequest(
    val task: String,
    val inputSerialized: String,
    val loggingChannelLocator: String
)



