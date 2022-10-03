import OrderType.ASK
import OrderType.BID
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.sql.Time

fun <FirstType, LastType> both(first: FirstType?, last: LastType?): Pair<FirstType, LastType>? {
    return first?.let {
        last?.let {
            first to last
        }
    }
}

val <FirstType, SecondType> Pair<FirstType?, SecondType?>.both: Pair<FirstType, SecondType>? get() = both(first, second)

var minFirst = Double.MAX_VALUE
var minLast = Double.MAX_VALUE
var maxFirst = Double.MIN_VALUE
var maxLast = Double.MIN_VALUE

const val HISTORY_SIZE = 1024
var currentIndex = 0

val now: Long get() = System.currentTimeMillis()


data class OrderWindow(
    val type: OrderType,
    val frames: Array<OrderFrame> = Array(HISTORY_SIZE) {OrderFrame()}
) {
  val pair get() = type to frames
  override fun equals(other: Any?) = pair == (other as? OrderWindow)?.pair

  override fun hashCode() = pair.hashCode()
  operator fun plusAssign(frame: Pair<Long, List<Pair<Double, Double>>>) {
    frames[currentIndex % frames.size] = frame.frame
  }
}

enum class OrderType { ASK, BID }

data class OrderChange(
    val at: Double,
    val change: Double
)

infix fun Double.change(change: Double) = OrderChange(this, change)

val Pair<Double, Double>.change get() = first change second

val Long.time get() = Time(this)

val Pair<Long, List<Pair<Double, Double>>>.frame get() = let { (time, changes) ->
  OrderFrame(time.time, changes.map { it.change })
}

infix fun Array<Pair<Long?, List<Pair<Double, Double>>?>>.windowOf(type: OrderType) = OrderWindow(type, mapNotNull {
  it.both?.frame
}.toTypedArray())
val Array<Pair<Long?, List<Pair<Double, Double>>?>>.askWindow get() = this windowOf ASK
val Array<Pair<Long?, List<Pair<Double, Double>>?>>.bidWindow get() = this windowOf BID

class OrderFrame(
    val time: Time = now.time,
    val changes: List<OrderChange> = listOf()
) {
  operator fun component1() = time
  operator fun component2() = changes
}

data class OrderHistory(
    val type: OrderType,
    val window: OrderWindow,
    val frame: OrderFrame
)

val askWindow = OrderWindow(ASK)
val bidWindow = OrderWindow(BID)

val List<Array<String>>?.normalized: List<Pair<Double, Double>>
    get() {

        return this?.mapNotNull {
            it.limitPair
        }?.map { (first, last) ->
            first.normalize(minFirst, maxFirst) to last.normalize(minLast, maxLast)
        } ?: listOf()
    }


@Composable
fun StopWatchDisplay(
    formattedTime: String,
    t: Long,
    wsResponse: StopWatch.BinanceWsResponse? = null,
    aggTrade: Array<StopWatch.AggregateTradeResponse>? = null,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedTime,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color.Black
            )
            Spacer(
                modifier = modifier.height(16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.fillMaxWidth()
            ) {
                Button(onStartClick) {
                    Text("Start")
                }
                Spacer(
                    modifier = modifier.width(16.dp)
                )
                Button(onPauseClick) {
                    Text("Pause")
                }
                Spacer(
                    modifier = modifier.width(16.dp)
                )
                Button(onResetClick) {
                    Text("Reset")
                }
                Spacer(
                    modifier = modifier.width(16.dp)
                )
            }
            Spacer(
                modifier = modifier.height(16.dp)
            )
            Text("First in [$minFirst, $maxFirst]. Last in [$minLast, $maxLast]")
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.fillMaxWidth()
            ) {
                askWindow += t to wsResponse?.asksToBeUpdated.normalized
                bidWindow += t to wsResponse?.bidsToBeUpdated.normalized

                currentIndex++

                Canvas(
                    modifier = modifier.fillMaxWidth().fillMaxHeight().background(Color.Black)
                ) {
                    drawHistory(askWindow, Color.Green)
                    drawHistory(bidWindow, Color.Red)
                }
            }
        }
    }
}

val DrawScope.width get() = drawContext.size.width
val DrawScope.height get() = drawContext.size.height

fun DrawScope.drawHistory(window: OrderWindow, color: Color) {
    val histSize = window.frames.size
    window.frames.forEachIndexed { index, (_, asks) ->
        asks.forEach { (first, last) ->
            val horizontal = width * (histSize - (currentIndex - index) % histSize).toFloat() / histSize
            val vertical = height * first.toFloat()
            drawCircle(
                color = color,
                radius = 16.dp.toPx() * (1.0 - 0.5 * last).toFloat(),
                center = Offset(horizontal, vertical),
                alpha = (0.3 + 0.7 * last).toFloat()
            )
        }
    }
}

private operator fun Float.times(offset: Offset): Offset {
    return Offset(this * offset.x, this * offset.y)
}

private val Array<String>.limitPair: Pair<Double, Double>?
    get() = mapNotNull {
        it.toDoubleOrNull()
    }.let {
        it.firstOrNull() to it.lastOrNull()
    }.let { (first, last) ->
        both(first, last)
    }?.also { (first, last) ->
        if (first < minFirst) {
            minFirst = first
        }
        if (first > maxFirst) {
            maxFirst = first
        }
        if (last < minLast) {
            minLast = last
        }
        if (last > maxLast) {
            maxLast = last
        }
    }

private fun Double.normalize(min: Double, max: Double): Double {
    val normalized = (this - min) / (max - min)
    return when {
        normalized in 0.0..1.0 -> normalized
        this < min -> 0.0
        this > max -> 1.0
        else -> 0.0
    }
}
