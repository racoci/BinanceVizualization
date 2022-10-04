
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application


@Composable
@Preview
fun App() {
  MaterialTheme {
    val stopWatch = remember { StopWatch() }
    StopWatchDisplay(
      formattedTime = stopWatch.formattedTime,
      wsResponse = stopWatch.wsResponse,
      aggTrade = stopWatch.aggTrade,
      timestamp = stopWatch.timeMillis,
      onStartClick = stopWatch::start,
      onPauseClick = stopWatch::pause,
      onResetClick = stopWatch::reset
    )
  }
}

fun main() = application {
  Window(onCloseRequest = ::exitApplication) {
    App()
  }
}
