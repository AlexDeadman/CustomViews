package ru.alexdeadman.customviewstest.ui.ganttchart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import ru.alexdeadman.customviewstest.R
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs

class GanttView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // region Paints

    private val rowPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val separatorPaint = Paint().apply {
        strokeWidth = resources.getDimension(R.dimen.gantt_separator_stroke_width)
        color = ContextCompat.getColor(context, R.color.grey_300)
    }

    private val periodNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gantt_period_name_text_size)
        color = ContextCompat.getColor(context, R.color.grey_500)
    }

    private val taskShapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val taskNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.gantt_task_name_text_size)
        color = Color.WHITE
    }

    // endregion

    // region Dimens

    private val periodWidth = resources.getDimensionPixelSize(R.dimen.gantt_period_width)

    private val contentWidth get() = periodWidth * periods.getValue(periodType).size

    private val rowHeight = resources.getDimensionPixelSize(R.dimen.gantt_row_height)

    private val taskCornerRadius = resources.getDimension(R.dimen.gantt_task_corner_radius)

    private val taskVerticalMargin = resources.getDimension(R.dimen.gantt_task_vertical_margin)

    private val cutOutRadius = (rowHeight - taskVerticalMargin * 2) / 6

    private val taskTextHorizontalMargin =
        resources.getDimension(R.dimen.gantt_task_text_horizontal_margin)

    // endregion

    // region Colors

    private val rowColors = listOf(
        ContextCompat.getColor(context, R.color.grey_100),
        Color.WHITE
    )

    private val gradientStartColor = ContextCompat.getColor(context, R.color.gradients_start_color)
    private val gradientEndColor = ContextCompat.getColor(context, R.color.gradient_end_color)

    // endregion

    // region Helpers

    private val rowRect = Rect()

    // endregion

    // region Tasks

    data class Task(
        val name: String,
        val dateStart: LocalDate,
        val dateEnd: LocalDate,
    )

    private inner class UiTask(val task: Task) {

        val rect = RectF()
        val untransformedRect = RectF()

        val path = Path()
        val cutOutPath = Path()

        val isRectOnScreen: Boolean
            get() = rect.top < height && (rect.right > 0 || rect.left < width)

        fun updateInitialRect(index: Int) {
            untransformedRect.set(
                getX(task.dateStart),
                rowHeight * (index + 1f) + taskVerticalMargin,
                getX(task.dateEnd),
                rowHeight * (index + 2f) - taskVerticalMargin
            )
            rect.set(untransformedRect)
        }

        private fun getX(date: LocalDate): Float {
            val periodIndex = periods.getValue(periodType).indexOf(periodType.getDateString(date))
            return periodWidth * (periodIndex + periodType.getPercentOfPeriod(date))
        }

        fun transform(matrix: Matrix) {
            matrix.mapRect(rect, untransformedRect)
            updatePath()
        }

        private fun updatePath() {
            if (isRectOnScreen) {
                with(cutOutPath) {
                    reset()
                    addCircle(
                        rect.left,
                        rect.centerY(),
                        cutOutRadius,
                        Path.Direction.CW
                    )
                }
                with(path) {
                    reset()
                    addRoundRect(rect, taskCornerRadius, taskCornerRadius, Path.Direction.CW)
                    op(cutOutPath, Path.Op.DIFFERENCE)
                }
            }
        }
    }

    var tasks: List<Task> = emptyList()
        set(value) {
            field = value

            updatePeriods()
            uiTasks = field.map(::UiTask)
            updateTasksRects()

            requestLayout()
            invalidate()
        }

    private var uiTasks: List<UiTask> = emptyList()

    // endregion

    // region Periods

    private enum class PeriodType {
        MONTH {
            override fun increment(date: LocalDate): LocalDate = date.plusMonths(1)

            override fun getDateString(date: LocalDate): String = date.month.name

            override fun getPercentOfPeriod(date: LocalDate): Float =
                (date.dayOfMonth - 1f) / date.lengthOfMonth()
        },
        WEEK {
            override fun increment(date: LocalDate): LocalDate = date.plusWeeks(1)

            override fun getDateString(date: LocalDate): String =
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()

            override fun getPercentOfPeriod(date: LocalDate): Float =
                (date.dayOfWeek.value - 1f) / 7
        };

        abstract fun increment(date: LocalDate): LocalDate

        abstract fun getDateString(date: LocalDate): String

        abstract fun getPercentOfPeriod(date: LocalDate): Float
    }

    private fun updatePeriods() {
        periods = PeriodType.values().associateWith { periodType ->
            if (tasks.isNotEmpty()) {
                val startDate = tasks.minBy { it.dateStart }.dateStart
                val endDate = tasks.maxBy { it.dateEnd }.dateEnd

                mutableListOf<String>().apply {
                    var date = startDate
                    while (date < endDate.plusMonths(1)) {
                        add(periodType.getDateString(date))
                        date = periodType.increment(date)
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    private var periodType = PeriodType.MONTH

    private var periods: Map<PeriodType, List<String>> = emptyMap()

    // endregion

    // region Transformations

    private inner class Transformer {
        var translationX = 0f
            private set
        var scaleX = 1f
            private set

        private val maxScale = 2f

        private val matrix = Matrix()

        private val minTranslation: Float
            get() = (width - contentWidth * scaleX).coerceAtMost(0f)

        fun onSaveInstanceState(state: SavedState) {
            state.translationX = translationX
            state.scaleX = scaleX
        }

        fun onRestoreInstanceState(state: SavedState) {
            translationX = state.translationX
            scaleX = state.scaleX
            recalculate()
        }

        fun addTranslation(dx: Float) {
            translationX = (translationX + dx).coerceIn(minTranslation, 0f)
            transformTasks()
        }

        fun addScale(scaleFactor: Float) {
            scaleX = (scaleX * scaleFactor).coerceIn(1f, maxScale)
            recalculateTranslationX()
            updatePeriodType(scaleX)
            transformTasks()
        }

        fun recalculate() {
            recalculateTranslationX()
            updatePeriodType(scaleX)
            transformTasks()
        }

        private fun recalculateTranslationX() {
            translationX = translationX.coerceIn(minTranslation, 0f)
        }

        private fun transformTasks() {
            with(matrix) {
                reset()
                setScale(scaleX, 1f)
                postTranslate(translationX, 0f)
            }
            uiTasks.forEach { it.transform(matrix) }
            invalidate()
        }

        private fun updatePeriodType(scale: Float) {
            val periodTypes = PeriodType.values()
            val scaleStep = (maxScale - 1f) / periodTypes.size
            val periodTypeIndex =
                ((scale - 1f) / scaleStep).toInt().coerceAtMost(periodTypes.lastIndex)
            val periodType = periodTypes[periodTypeIndex]
            if (this@GanttView.periodType != periodType) {
                this@GanttView.periodType = periodType
                updateTasksRects()
            }
        }
    }

    private val transformer = Transformer()

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            transformer.addScale(detector.scaleFactor)
            return true
        }
    }

    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

    // endregion

    // region Saving state

    private class SavedState : BaseSavedState {
        var translationX: Float = 0f
        var scaleX: Float = 0f

        constructor(superState: Parcelable?) : super(superState)

        constructor(source: Parcel?) : super(source) {
            source?.apply {
                translationX = readFloat()
                scaleX = readFloat()
            }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(translationX)
            out.writeFloat(scaleX)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()).also(transformer::onSaveInstanceState)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            transformer.onRestoreInstanceState(state)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    // endregion

    // region Measure

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                contentWidth
            } else {
                MeasureSpec.getSize(widthMeasureSpec)
            }

        val contentHeight = rowHeight * (tasks.size + 1)
        val heightSpecSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> contentHeight
            MeasureSpec.EXACTLY -> heightSpecSize
            MeasureSpec.AT_MOST -> contentHeight.coerceAtMost(heightSpecSize)
            else -> error("")
        }

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rowRect.set(0, 0, w, rowHeight)

        taskShapePaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            gradientStartColor,
            gradientEndColor,
            Shader.TileMode.CLAMP
        )

        updateTasksRects()
    }

    private fun updateTasksRects() {
        uiTasks.forEachIndexed { index, uiTask -> uiTask.updateInitialRect(index) }
        transformer.recalculate()
    }

    // endregion

    // region Draw

    override fun onDraw(canvas: Canvas) {
        drawRows(canvas)
        drawPeriods(canvas)
        drawTasks(canvas)
    }

    private fun drawRows(canvas: Canvas) {
        repeat(tasks.size + 1) { index ->
            rowRect.offsetTo(0, rowHeight * index)
            if (rowRect.top < height) {
                rowPaint.color = rowColors[index % rowColors.size]
                canvas.drawRect(rowRect, rowPaint)
            }
        }

        val horizontalSeparatorY = rowHeight.toFloat()
        canvas.drawLine(
            0f,
            horizontalSeparatorY,
            width.toFloat(),
            horizontalSeparatorY,
            separatorPaint
        )
    }

    private fun drawPeriods(canvas: Canvas) {
        val currentPeriods = periods[periodType]
        val nameY = periodNamePaint.getTextBaselineByCenter(rowHeight / 2f)

        currentPeriods?.forEachIndexed { index, periodName ->
            val textWidth = periodNamePaint.measureText(periodName)
            val periodCenter = periodWidth * transformer.scaleX * (index + 0.5f)
            val nameX = (periodCenter - textWidth / 2) + transformer.translationX
            canvas.drawText(
                periodName,
                nameX,
                nameY,
                periodNamePaint
            )

            val verticalSeparatorX =
                periodWidth * (index + 1f) * transformer.scaleX + transformer.translationX
            canvas.drawLine(
                verticalSeparatorX,
                0f,
                verticalSeparatorX,
                height.toFloat(),
                separatorPaint
            )
        }
    }

    private fun drawTasks(canvas: Canvas) {
        val minTextLeft = taskTextHorizontalMargin
        uiTasks.forEach { uiTask ->
            if (uiTask.isRectOnScreen) {
                canvas.drawPath(uiTask.path, taskShapePaint)

                val taskRect = uiTask.rect
                val taskName = uiTask.task.name

                val textLeft =
                    (taskRect.left + cutOutRadius + taskTextHorizontalMargin).coerceAtLeast(
                        minTextLeft
                    )
                val textWidth =
                    taskRect.right - taskTextHorizontalMargin - textLeft

                if (textWidth > 0) {
                    val textY = taskNamePaint.getTextBaselineByCenter(taskRect.centerY())
                    val charCount = taskNamePaint.breakText(
                        taskName,
                        true,
                        textWidth,
                        null
                    )

                    canvas.drawText(
                        taskName.substring(0, charCount),
                        textLeft,
                        textY,
                        taskNamePaint
                    )
                }
            }
        }
    }

    private fun Paint.getTextBaselineByCenter(center: Float) = center - (descent() + ascent()) / 2

    // endregion

    // region Touches

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false

        if (abs(event.x - lastPoint.x) > abs(event.y - lastPoint.y)) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        return if (event.pointerCount > 1) {
            scaleGestureDetector.onTouchEvent(event)
        } else {
            processMovement(event)
        }
    }

    private val lastPoint = PointF()
    private var lastPointerId = 0

    private fun processMovement(event: MotionEvent): Boolean =
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastPoint.set(event.x, event.y)
                lastPointerId = event.getPointerId(0)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (width < contentWidth) {
                    val pointerId = event.getPointerId(0)
                    if (lastPointerId == pointerId) {
                        transformer.addTranslation(event.x - lastPoint.x)
                    }

                    lastPoint.set(event.x, event.y)
                    lastPointerId = event.getPointerId(0)

                    true
                } else {
                    false
                }
            }

            else -> false
        }

    // endregion
}
