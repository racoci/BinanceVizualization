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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun <A,B> both(a: A?, b: B?) = a?.let {
  b?.let{
    a to b
  }
}

var minFirst = Double.MAX_VALUE
var minLast = Double.MAX_VALUE
var maxFirst = Double.MIN_VALUE
var maxLast = Double.MIN_VALUE

val histSize = 1024
var currentIndex = 0

val askHist = Array<Pair<Long?, List<Pair<Double, Double>>?>>(histSize) {
  null to null
}

val bidHist = Array<Pair<Long?, List<Pair<Double, Double>>?>>(histSize) {
  null to null
}

val List<Array<String>>?.normalized: List<Pair<Double, Double>> get() {

  return this?.mapNotNull {
    it.limitPair
  }?.map { (first, last) ->
    first.normalize(minFirst, maxFirst) to last.normalize(minLast, maxLast)
  } ?: listOf()
}


@Composable
fun StopWatchDisplay(
  formattedTime: String,
  timestamp: Long,
  wsResponse: StopWatch.BinanceWsResponse? = null,
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
        askHist[currentIndex % histSize] = timestamp to wsResponse?.asksToBeUpdated.normalized
        bidHist[currentIndex % histSize] = timestamp to wsResponse?.bidsToBeUpdated.normalized

        currentIndex++

        Canvas(
          modifier = modifier.fillMaxWidth().fillMaxHeight().background(Color.Black)
        ) {
          askHist.forEachIndexed{ index, (_, asks) ->
            asks?.forEach{ (first, last) ->
              drawCircle(
                color = Color.Green,
                radius =  16.dp.toPx() * (1.0 - 0.5 * last).toFloat(),
                center = Offset(drawContext.size.width * (histSize - (currentIndex - index) % histSize).toFloat() / histSize, drawContext.size.height * first.toFloat()),
                alpha = (0.3 + 0.7 * last).toFloat()
              )
            }
          }

          bidHist.forEachIndexed{ index, (_, bids) ->
            bids?.forEach{ (first, last) ->
              drawCircle(
                color = Color.Red,
                radius =  16.dp.toPx() * (1.0 - 0.5 * last).toFloat(),
                center = Offset(drawContext.size.width * (histSize - (currentIndex - index) % histSize).toFloat() / histSize, drawContext.size.height * first.toFloat()),
                alpha = (0.3 + 0.7 * last).toFloat()
              )
            }
          }
        }
      }
    }
  }
}

private operator fun Float.times(offset: Offset): Offset {
  return Offset(this * offset.x,  this * offset.y)
}

private val Array<String>.limitPair: Pair<Double, Double>? get() = mapNotNull {
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
