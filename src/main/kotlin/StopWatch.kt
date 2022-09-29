import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KMutableProperty0

class StopWatch {
/**
{
    "e":"depthUpdate",
    "E":1664393872704,
    "s":"BNBUSDT",
    "U":7917738800,
    "u":7917738801,
    "b":[["278.50000000","253.53100000"],["278.00000000","73.70700000"]],
    "a":[]
} **/

    data class BinanceWsResponse(
        @SerializedName("e")
        val eventType: String,
        @SerializedName("E")
        val eventTime: Long,
        @SerializedName("s")
        val symbol: String,
        @SerializedName("U")
        val firstUpdateIdInEvent: Long,
        @SerializedName("u")
        val finalUpdateIdInEvent: Long,
        @SerializedName("b")
        val bidsToBeUpdated: List<Array<String>>,
        @SerializedName("a")
        val asksToBeUpdated: List<Array<String>>
    )

    private val gson = Gson()

    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isStopWatchActive: Boolean by Delegates.observable(false) { _, wasActive, isActive ->
        if(wasActive && !isActive) {
            client.close()
        } else if(isActive && !wasActive) {
            coroutineScope.launch {
                client.webSocket(
                    request = {
                        url("wss", "stream.binance.com", 9443, "/ws/bnbusdt@depth@100ms")
                    }
                ) {
                    while (isStopWatchActive) {
                        val receivedText = incoming.receive() as? Frame.Text
                        val receivedString = receivedText?.readText()
                        wsResponse = receivedString?.runCatching {
                            gson.fromJson(this, BinanceWsResponse::class.java)
                        }?.getOrNull()
                        delay(100)
                    }

                }
            }

        }
    }
    private var lastTimestamp = 0L
    var timeMillis by mutableStateOf(0L)


    val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20_000
        }
    }


    val formattedTime: String by ::timeMillis.map {
        formatTime(it)
    }

    var wsResponse by mutableStateOf<BinanceWsResponse?>(null)

    fun start() {
        if (isStopWatchActive) return

        coroutineScope.launch {
            lastTimestamp = System.currentTimeMillis()
            isStopWatchActive = true
            while (isStopWatchActive) {
                delay(10L)
                timeMillis += System.currentTimeMillis() - lastTimestamp
                lastTimestamp = System.currentTimeMillis()
            }
        }
    }

    fun pause() {
        isStopWatchActive = false
    }

    fun reset() {
        timeMillis = 0
    }

    private fun formatTime(timeMillis: Long): String {
        val localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timeMillis),
            ZoneId.systemDefault()
        )
        val formatter = DateTimeFormatter.ofPattern(
            "mm:ss:SSS",
            Locale.getDefault()
        )
        return localDateTime.format(formatter)
    }

}

private fun <T, V, R> KMutableProperty0<V>.map(function: T.(V) -> R) = ReadOnlyProperty<T, R> { self, _ ->
    self.function(get())
}
