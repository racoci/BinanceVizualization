import OrderType.ASK
import OrderType.BID
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.sql.Time
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <FirstType, LastType> both(first: FirstType?, last: LastType?): Pair<FirstType, LastType>? {
    return first?.let {
        last?.let {
            first to last
        }
    }
}

val <FirstType, SecondType> Pair<FirstType?, SecondType?>.both: Pair<FirstType, SecondType>? get() = both(first, second)

const val HISTORY_SIZE = 1024
var currentIndex = 0

val now: Long get() = System.currentTimeMillis()


data class OrderWindow(
    val type: OrderType,
    val frames: Array<OrderFrame> = Array(HISTORY_SIZE) { OrderFrame() }
) {
    val pair get() = type to frames
    override fun equals(other: Any?) = pair == (other as? OrderWindow)?.pair
    override fun hashCode() = pair.hashCode()
    operator fun plusAssign(frame: Pair<Long, List<Pair<Double, Double>>>) {
        frames[currentIndex % frames.size] = frame.frame
    }

    operator fun plusAssign(frame: OrderFrame) {
        frames[currentIndex % frames.size] = frame
    }
}

enum class OrderType { ASK, BID }

interface MutScope<T, X>: MutableList<() -> ReadWriteProperty<T, X>>

infix fun <T, X> MutScope<T,X>.add(observableProperty: suspend () -> ReadWriteProperty<T, X>) {

}

fun <T, R> mut(initial: R, mutBuilder: MutScope<T, R>.() -> Unit): ReadWriteProperty<T, R> {
    val context = object : MutScope<T, R>, MutableList<() -> ReadWriteProperty<T, R>> by mutableListOf() {
    }
    context.mutBuilder()
    val properties = context.map {
        it()
    }

    return object : ReadWriteProperty<T, R> {
        var last: R = initial
        override fun getValue(thisRef: T, property: KProperty<*>): R {
            properties.forEach {
                last = it.getValue(thisRef, property)
            }
            return last
        }

        override fun setValue(thisRef: T, property: KProperty<*>, value: R) {
            properties.forEach {
                it.setValue(thisRef, property, value)
            }
            last = value
        }

    }
}

infix fun <Self, Value> MutableState<Value>.property(self: Self): ReadWriteProperty<Self, Value> {
    val mutableState = this
    return object: ReadWriteProperty<Self, Value> {
        override fun getValue(thisRef: Self, property: KProperty<*>): Value {
            return mutableState.getValue(thisRef, property)
        }

        override fun setValue(thisRef: Self, property: KProperty<*>, value: Value) {
            mutableState.setValue(thisRef, property, value)
        }

    }
}

interface Reversible<A, B> : (A) -> B {
    operator fun get(result: B): A
}

infix fun<A, B> ((A) -> B).reverseWith(reverseWith: (B) -> A): Reversible<A,B> {
    return object: ((A) -> B) by this, Reversible<A,B> {
        override fun get(result: B): A = reverseWith(result)
    }
}

val <A,B> Reversible<A, B>.reversed: Reversible<B, A> get() = object : Reversible<B, A> {
    override fun get(result: A): B = invoke(result)
    override fun invoke(parameter: B): A = get(parameter)
}

interface SelfKeeper<Self, Type> {
    var self: Self
    val reversible: Reversible<Type, Unit>
}

fun <Self, Type> Self.fromReversible(rev: Reversible<Type, Unit>): SelfKeeper<Self, Type> {
    val selfie = this
    return object : SelfKeeper<Self, Type> {
        override var self = selfie
        override val reversible = rev

    }
}

infix fun <Self, Type> Reversible<Type, Unit>.keepOn(self: Self) = self.fromReversible(this)

val <Self, Type> SelfKeeper<Self, Type>.rw: ReadWriteProperty<Self, Type> get() = object: ReadWriteProperty<Self, Type> {
    override fun getValue(thisRef: Self, property: KProperty<*>): Type {
        self = thisRef
        return reversible[Unit]
    }

    override fun setValue(thisRef: Self, property: KProperty<*>, value: Type) {
        self = thisRef
        reversible(value)
    }
}

interface MutableProperty<Self, Value>: SelfKeeper<Self, Value> {

}

fun <Self, C: Comparable<C>> Self.keepMax(initialMax: C): MutableProperty<Self, C> {
    var maxValue: C = initialMax
    var currentValue: C = initialMax
    val reversible: Reversible<Unit, C> = { _: Unit ->
        currentValue
    } reverseWith {
        if (it > maxValue) {
            maxValue = it
        }
        currentValue = it
    }
    return object : MutableProperty<Self, C>, SelfKeeper<Self, C> by reversible.reversed.keepOn(this) {}
}
class OrderChange(
    val at: Double,
    val change: Double
) {
    operator fun component1(): Double = at
    operator fun component2(): Double = change
}

infix fun Double.change(change: Double) = OrderChange(this, change)

val Pair<Double, Double>.change get() = first change second

val Long.time get() = Time(this)

val Pair<Long, List<Pair<Double, Double>>>.frame
    get() = let { (time, changes) ->
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

var minFirst: Double = Double.MAX_VALUE
var minLast: Double = Double.MAX_VALUE
var maxFirst: Double = Double.MIN_VALUE
var maxLast: Double = Double.MIN_VALUE

data class OrderHistory(
    val type: OrderType,
    val window: OrderWindow,
    val frame: OrderFrame
)

val askWindow = OrderWindow(ASK)
val bidWindow = OrderWindow(BID)

val <Element> List<Element>?.list get() = this ?: listOf()

fun <T, R> Pair<T, T>.map(function: (T) -> R): Pair<R, R> = Pair(function(first), function(second))

fun <This, It, Return> uncurryReceiver(
    curried: This.(It) -> Return
): (This.() -> ((It) -> Return)) = {
    { parameter ->
        curried(parameter)
    }
}

fun <This, It, Return> uncurryParameter(
    curried: This.(It) -> Return
): ((This) -> ((It) -> Return)) = { receiver ->
    { parameter ->
        receiver.curried(parameter)
    }
}

fun <This, It, Return> curry(uncuried: (This) -> ((It) -> Return)): This.(It) -> Return = {
    uncuried(this)(it)
}

@JvmName("partialReceiver")
fun <This, It, Return> This.partial(uncuried: This.(It) -> Return): ((It) -> Return) = { parameter ->
    uncuried(parameter)
}

@JvmName("partialParameter")
fun <This, It, Return> partial(parameter: It, function: This.(It) -> Return): (This.() -> Return) = {
    function(parameter)
}

val List<Pair<Double, Double>>.changes: List<OrderChange> get() = map { it.change }
infix fun List<Array<String>>?.normalizeFrameOn(time: Time): OrderFrame {
    return time frame list.mapNotNull { strings ->
        strings.mapNotNull {
            it.toDoubleOrNull()
        }.let {
            it.firstOrNull() to it.lastOrNull()
        }.both?.also { (first, last) ->
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
    }.map { (first, last) ->
        first.normalize(minFirst, maxFirst) to last.normalize(minLast, maxLast)
    }.changes
}

infix fun Time.frame(changes: List<OrderChange>) = OrderFrame(this, changes)

infix fun List<OrderChange>.frameOn(time: Time) = time frame this


@Composable
fun StopWatchDisplay(
    formattedTime: String,
    timestamp: Long,
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
                text = formattedTime, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Color.Black
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
                askWindow += wsResponse?.asksToBeUpdated normalizeFrameOn timestamp.time
                bidWindow += wsResponse?.bidsToBeUpdated normalizeFrameOn timestamp.time

                currentIndex++

                Canvas(
                    modifier = modifier.fillMaxWidth().fillMaxHeight().background(Color.Black)
                ) {
                    drawWindow(askWindow, Color.Green)
                    drawWindow(bidWindow, Color.Red)
                }
            }
        }
    }
}

val DrawScope.width get() = drawContext.size.width
val DrawScope.height get() = drawContext.size.height

fun DrawScope.drawWindow(window: OrderWindow, color: Color) {
    val histSize = window.frames.size
    window.frames.forEachIndexed { index, (_, frame) ->
        frame.forEach { (first, last) ->
            val horizontal = width * (histSize - (currentIndex - index) % histSize).toFloat() / histSize
            val vertical = height * first.toFloat()
            drawCircle(
                color = color,
                radius = 16.dp.toPx() * last.toFloat(),
                center = Offset(horizontal, vertical),
                alpha = (1.0 - 0.7 * last).toFloat()
            )

            drawCircle(
                color = color,
                radius = 4.dp.toPx() * (1.0 - last).toFloat(),
                center = Offset(horizontal, vertical),
                alpha = (0.3 + 0.7 * last).toFloat()
            )
        }
    }
}

private operator fun Offset.times(factor: Float): Offset {
    return Offset(x * factor, y * factor)
}

private operator fun Float.times(offset: Offset) = offset * this

private fun Double.normalize(min: Double, max: Double): Double {
    val normalized = (this - min) / (max - min)
    return when {
        normalized in 0.0..1.0 -> normalized
        this < min -> 0.0
        this > max -> 1.0
        else -> 0.0
    }
}
