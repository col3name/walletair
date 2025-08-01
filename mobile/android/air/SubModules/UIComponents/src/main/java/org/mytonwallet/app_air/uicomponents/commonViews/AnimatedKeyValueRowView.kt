package org.mytonwallet.app_air.uicomponents.commonViews

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import org.mytonwallet.app_air.uicomponents.drawable.SeparatorBackgroundDrawable
import org.mytonwallet.app_air.uicomponents.drawable.counter.Counter
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.extensions.getCenterAlignBaseline
import org.mytonwallet.app_air.uicomponents.extensions.setPaddingDp
import org.mytonwallet.app_air.uicomponents.helpers.ViewHelpers
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.helpers.typeface
import org.mytonwallet.app_air.uicomponents.widgets.WThemedView
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import kotlin.math.roundToInt

class AnimatedKeyValueRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle), WThemedView, Counter.Callback {
    val separator = SeparatorBackgroundDrawable().apply {
        offsetStart = 20f.dp
        offsetEnd = 20f.dp
    }
    private val rippleDrawable = ViewHelpers.roundedRippleDrawable(separator, 0, 0f)

    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = WFont.Regular.typeface
        textSize = 16f.dp
    }
    private val valueTextPaint = TextPaint(titleTextPaint)

    private val titleText = Counter(titleTextPaint, this)
    private var titleDrawable: Drawable? = null
    private val valueText = Counter(valueTextPaint, this)

    private var _title: String? = null
    var title: String?
        get() = _title
        set(value) {
            _title = value
            rebuild()
        }

    private var _value: String? = null
    var value: String?
        get() = _value
        set(value) {
            _value = value
            rebuild()
        }

    fun setTitleAndValue(title: String?, value: String?, animated: Boolean = true) {
        _title = title
        _value = value
        rebuild(animated)
    }

    private fun rebuild(animated: Boolean = true) {
        if (!isAttachedToWindow) {
            return
        }

        var availableWidth = measuredWidth - paddingLeft - paddingRight.toFloat()
        titleDrawable?.let {
            availableWidth -= it.minimumWidth
            availableWidth -= GAP_DRAWABLE.dp
        }

        val valueText = TextUtils.ellipsize(
            value ?: "",
            valueTextPaint,
            availableWidth,
            TextUtils.TruncateAt.END
        ).toString()
        availableWidth -= valueTextPaint.measureText(valueText)
        availableWidth -= GAP_TEXT.dp

        val titleText: String
        if (availableWidth > 0) {
            titleText = TextUtils.ellipsize(
                title ?: "",
                titleTextPaint,
                availableWidth,
                TextUtils.TruncateAt.END
            ).toString()
            availableWidth -= valueTextPaint.measureText(titleText)
        } else {
            titleText = ""
        }

        this.valueText.setValue(valueText, animated)
        this.titleText.setValue(titleText, animated)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cy = measuredHeight / 2f

        val titleY = titleTextPaint.fontMetrics.getCenterAlignBaseline(cy)
        val valueY = valueTextPaint.fontMetrics.getCenterAlignBaseline(cy)

        titleText.draw(canvas, paddingLeft.toFloat(), titleY, 1f)

        titleDrawable?.let {
            val x = paddingLeft + titleText.getVisibleWidth().roundToInt() + GAP_DRAWABLE
                .dp
            val y = (measuredHeight - it.minimumHeight) / 2

            it.setBounds(x, y, x + it.minimumWidth, y + it.minimumHeight)
            it.draw(canvas)
        }

        valueText.draw(
            canvas,
            measuredWidth.toFloat() - paddingRight - valueText.getVisibleWidth(),
            valueY,
            1f
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rebuild(false)
    }

    fun setTitleDrawable(res: Int, alpha: Float = 1f) {
        val drawable = ContextCompat.getDrawable(context, res)
        drawable?.alpha = (alpha * 255f).roundToInt()
        setTitleDrawable(drawable)
    }

    fun setTitleDrawable(drawable: Drawable?) {
        titleDrawable = drawable
        titleDrawable?.setTint(WColor.SecondaryText.color)
        rebuild(false)
    }

    init {
        background = rippleDrawable
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp)
        setPaddingDp(20, 0, 20, 0)

        updateTheme()
    }

    override fun updateTheme() {
        rippleDrawable.setColor(ColorStateList.valueOf(WColor.backgroundRippleColor))
        titleTextPaint.color = WColor.SecondaryText.color
        valueTextPaint.color = WColor.PrimaryText.color
        titleDrawable?.setTint(WColor.SecondaryText.color)
    }

    override fun onCounterAppearanceChanged(counter: Counter, sizeChanged: Boolean) {
        invalidate()
    }

    companion object {
        private const val GAP_TEXT = 8
        private const val GAP_DRAWABLE = 4
    }
}
