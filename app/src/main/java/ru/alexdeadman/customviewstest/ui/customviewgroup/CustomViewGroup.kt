package ru.alexdeadman.customviewstest.ui.customviewgroup

import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import ru.alexdeadman.customviewstest.R
import kotlin.math.max
import kotlin.math.roundToInt

class CustomViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val firstChild: TextView?
        get() = if (childCount > 0) getChildAt(0) as TextView else null
    private val secondChild: View?
        get() = if (childCount > 1) getChildAt(1) else null

    private var offset = 0

    private val animator = ValueAnimator
        .ofObject(
            StringEvaluator(),
            "Lorem ipsum",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
        )
        .apply {
            duration = 4000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                firstChild?.text = it.animatedValue.toString()
            }
        }

    init {
        context.withStyledAttributes(attrs, R.styleable.CustomViewGroup, defStyleAttr) {
            offset = getDimensionPixelOffset(R.styleable.CustomViewGroup_offset, 0)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        checkChildCount()

        firstChild?.let { measureChild(it, widthMeasureSpec) }
        secondChild?.let { measureChild(it, widthMeasureSpec) }

        val firstWidth = firstChild?.measuredWidth ?: 0
        val firstHeight = firstChild?.measuredHeight ?: 0
        val secondWidth = secondChild?.measuredWidth ?: 0
        val secondHeight = secondChild?.measuredHeight ?: 0

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val areChildrenOnSameLine =
            firstWidth + secondWidth < widthSize || widthMode == MeasureSpec.UNSPECIFIED

        val width = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> firstWidth + secondWidth
            MeasureSpec.AT_MOST -> {
                if (areChildrenOnSameLine) {
                    firstWidth + secondWidth + offset
                } else {
                    max(firstWidth, secondWidth)
                }
            }

            MeasureSpec.EXACTLY -> widthSize
            else -> error("")
        }

        val height = if (areChildrenOnSameLine) {
            max(firstHeight, secondHeight)
        } else {
            firstHeight + secondHeight + offset
        }

        setMeasuredDimension(width + paddingLeft + paddingRight, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        firstChild?.layout(
            paddingLeft,
            paddingTop,
            paddingLeft + (firstChild?.measuredWidth ?: 0),
            paddingTop + (firstChild?.measuredHeight ?: 0)
        )
        secondChild?.layout(
            r - l - paddingRight - (secondChild?.measuredWidth ?: 0),
            b - t - paddingBottom - (secondChild?.measuredHeight ?: 0),
            r - l - paddingRight,
            b - t - paddingBottom
        )
    }

    private fun measureChild(child: View, widthMeasureSpec: Int) {
        val specSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val childWidthSpec = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> widthMeasureSpec
            MeasureSpec.AT_MOST -> widthMeasureSpec
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(specSize, MeasureSpec.AT_MOST)
            else -> error("")
        }
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        child.measure(childWidthSpec, childHeightSpec)
    }

    private fun checkChildCount() {
        if (childCount > 2) error("CustomViewGroup should not contain more than 2 children")
    }

    class StringEvaluator : TypeEvaluator<String> {
        override fun evaluate(fraction: Float, startValue: String, endValue: String): String {
            val coercedFraction = fraction.coerceIn(0f, 1f)

            val lengthDiff = endValue.length - startValue.length
            val currentDiff = (lengthDiff * coercedFraction).roundToInt()
            return if (currentDiff > 0) {
                endValue.substring(0, startValue.length + currentDiff)
            } else {
                startValue.substring(0, startValue.length + currentDiff)
            }
        }
    }
}
