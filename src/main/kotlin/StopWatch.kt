import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.websocket.*
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

    /**
    {
    "e": "aggTrade",  // Event type
    "E": 123456789,   // Event time
    "s": "BNBBTC",    // Symbol
    "a": 12345,       // Aggregate trade ID
    "p": "0.001",     // Price
    "q": "100",       // Quantity
    "f": 100,         // First trade ID
    "l": 105,         // Last trade ID
    "T": 123456785,   // Trade time
    "m": true,        // Is the buyer the market maker?
    "M": true         // Ignore
    }
    {"a":436931872,"p":"19602.38000000","q":"0.19665000","f":515840976,"l":515840976,"T":1664820852605,"m":false,"M":true}
     */

    data class AggregateTradeResponse(
        @SerializedName("a")
        val aggregateTradeId: Long,
        @SerializedName("p")
        val price: String,
        @SerializedName("q")
        val quantity: String,
        @SerializedName("f")
        val firstTradeId: Long,
        @SerializedName("l")
        val lastTradeId: Long,
        @SerializedName("T")
        val tradeTime: Long,
        @SerializedName("m")
        val isMarketMakerBuying: Boolean,

    )

    private val gson = Gson()

    private var coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isStopWatchActive: Boolean by Delegates.observable(false) { _, wasActive, isActive ->
        if(wasActive && !isActive) {
            client.close()
        } else if(isActive && !wasActive) {
            coroutineScope.launch {
                val response: HttpResponse = client.get{
                    url("https://www.binance.com/api/v1/aggTrades")
                    parameter("limit", 80)
                    parameter("symbol", "BTCBUSD")
                }

                aggTrade = response.body<String>().runCatching {
                    gson.fromJson<Array<AggregateTradeResponse>>(this, (object : TypeToken<Array<AggregateTradeResponse>>() {}).type)
                }.getOrNull()

                client.webSocket(
                    request = {
                        url("wss", "stream.binance.com", 9443, "/ws/btcbusd@depth@100ms")
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


    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20_000
        }
    }


    val formattedTime: String by ::timeMillis.map {
        formatTime(it)
    }

    var wsResponse by mutableStateOf<BinanceWsResponse?>(null)
    var aggTrade by mutableStateOf<Array<AggregateTradeResponse>?>(null)

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
