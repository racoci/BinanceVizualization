import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.MILLISECONDS


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
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
      ) {

        Canvas(
          modifier = modifier.fillMaxWidth().fillMaxHeight()
        ) {
          for(radioMultiplier in 1 .. 10) {
            drawCircle(
              color = Color.Blue,
              radius =  (size.minDimension * sin(
                timestamp.div(radioMultiplier * 1.seconds.toDouble(MILLISECONDS))
              ) * radioMultiplier / 10).toFloat(),
              alpha = 0.1f
            )
          }
        }
      }
    }
  }
}