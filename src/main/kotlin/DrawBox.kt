import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput


import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.skia.Bitmap


//Model
data class PathWrapper(
    var points: SnapshotStateList<Offset>,
    val strokeWidth: Float = 5f,
    val strokeColor: Color,
    val alpha: Float = 1f
)

data class DrawBoxPayLoad(val bgColor: Color, val path: List<PathWrapper>)

fun createPath(points: List<Offset>) = Path().apply {
    if (points.size > 1) {
        var oldPoint: Offset? = null
        this.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val point: Offset = points[i]
            oldPoint?.let {
                val midPoint = calculateMidpoint(it, point)
                if (i == 1) {
                    this.lineTo(midPoint.x, midPoint.y)
                } else {
                    this.quadraticBezierTo(it.x, it.y, midPoint.x, midPoint.y)
                }
            }
            oldPoint = point
        }
        oldPoint?.let { this.lineTo(it.x, oldPoint.y) }
    }
}

private fun calculateMidpoint(start: Offset, end: Offset): Offset {
    return Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
}

class DrawController internal constructor(val trackHistory: (undoCount: Int, redoCount: Int) -> Unit = { _, _ -> }) {

    private val _redoPathList = mutableStateListOf<PathWrapper>()
    private val _undoPathList = mutableStateListOf<PathWrapper>()
    internal val pathList: SnapshotStateList<PathWrapper> = _undoPathList


    private val _historyTracker = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val historyTracker = _historyTracker.asSharedFlow()

    fun trackHistory(
        scope: CoroutineScope,
        trackHistory: (undoCount: Int, redoCount: Int) -> Unit
    ) {
        historyTracker
            .onEach { trackHistory(_undoPathList.size, _redoPathList.size) }
            .launchIn(scope)
    }

    var opacity by mutableStateOf(1f)
        private set

    var strokeWidth by mutableStateOf(10f)
        private set

    var color by mutableStateOf(Color.Red)
        private set

    var bgColor by mutableStateOf(Color.Black)
        private set

    fun changeOpacity(value: Float) {
        opacity = value
    }

    fun changeColor(value: Color) {
        color = value
    }

    fun changeBgColor(value: Color) {
        bgColor = value
    }

    fun changeStrokeWidth(value: Float) {
        strokeWidth = value
    }

    fun importPath(drawBoxPayLoad: DrawBoxPayLoad) {
        reset()
        bgColor = drawBoxPayLoad.bgColor
        _undoPathList.addAll(drawBoxPayLoad.path)
        _historyTracker.tryEmit("${_undoPathList.size}")
    }

    fun exportPath() = DrawBoxPayLoad(bgColor, pathList.toList())


    fun unDo() {
        if (_undoPathList.isNotEmpty()) {
            val last = _undoPathList.last()
            _redoPathList.add(last)
            _undoPathList.remove(last)
            trackHistory(_undoPathList.size, _redoPathList.size)
            _historyTracker.tryEmit("Undo - ${_undoPathList.size}")
        }
    }

    fun reDo() {
        if (_redoPathList.isNotEmpty()) {
            val last = _redoPathList.last()
            _undoPathList.add(last)
            _redoPathList.remove(last)
            trackHistory(_undoPathList.size, _redoPathList.size)
            _historyTracker.tryEmit("Redo - ${_redoPathList.size}")
        }
    }


    fun reset() {
        _redoPathList.clear()
        _undoPathList.clear()
        _historyTracker.tryEmit("-")
    }

    fun updateLatestPath(newPoint: Offset) {
        val index = _undoPathList.lastIndex
        _undoPathList[index].points.add(newPoint)
    }

    fun insertNewPath(newPoint: Offset) {
        val pathWrapper = PathWrapper(
            points = mutableStateListOf(newPoint),
            strokeColor = color,
            alpha = opacity,
            strokeWidth = strokeWidth,
        )
        _undoPathList.add(pathWrapper)
        _redoPathList.clear()
        _historyTracker.tryEmit("${_undoPathList.size}")
    }


}

@Composable
fun DrawBox(
    modifier: Modifier = Modifier.fillMaxSize(),
    drawController: DrawController = remember { DrawController() },
    backgroundColor: Color = MaterialTheme.colors.background,
    trackHistory: (undoCount: Int, redoCount: Int) -> Unit = { _, _ -> }
) {
    LaunchedEffect(drawController) {
        drawController.changeBgColor(backgroundColor)
        drawController.trackHistory(this, trackHistory)
    }
    Canvas(modifier = modifier
        .background(drawController.bgColor)
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    drawController.insertNewPath(offset)
                }
            ) { change, _ ->
                val newPoint = change.position
                drawController.updateLatestPath(newPoint)
            }
        }) {

        drawController.pathList.forEach { pathWrapper ->
            drawPath(
                createPath(pathWrapper.points),
                color = pathWrapper.strokeColor,
                alpha = pathWrapper.alpha,
                style = Stroke(
                    width = pathWrapper.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}




