package org.mytonwallet.app_air.uiassets.viewControllers.nft.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView.ScaleType
import androidx.core.animation.doOnEnd
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.facebook.drawee.generic.RoundingParams
import org.mytonwallet.app_air.uicomponents.AnimationConstants
import org.mytonwallet.app_air.uicomponents.base.WNavigationBar
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.image.Content
import org.mytonwallet.app_air.uicomponents.image.WCustomImageView
import org.mytonwallet.app_air.uicomponents.widgets.WAnimationView
import org.mytonwallet.app_air.uicomponents.widgets.WBaseView
import org.mytonwallet.app_air.uicomponents.widgets.WLabel
import org.mytonwallet.app_air.uicomponents.widgets.WThemedView
import org.mytonwallet.app_air.uicomponents.widgets.coverFlow.WCoverFlowView
import org.mytonwallet.app_air.uicomponents.widgets.fadeIn
import org.mytonwallet.app_air.uicomponents.widgets.fadeOut
import org.mytonwallet.app_air.uicomponents.widgets.setBackgroundColor
import org.mytonwallet.app_air.walletcontext.R
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcontext.utils.AnimUtils.Companion.lerp
import org.mytonwallet.app_air.walletcore.moshi.ApiNft
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
open class NftHeaderView(
    context: Context,
    var nft: ApiNft,
    val collectionNFTs: List<ApiNft>,
    safeAreaPadding: Int,
    val viewWidth: Int,
    val delegate: WeakReference<Delegate>,
) : FrameLayout(context), WThemedView {

    interface Delegate {
        fun onNftChanged(nft: ApiNft)
        fun onExpandTapped()
        fun onPreviewTapped()
        fun onCollectionTapped()
        fun onHeaderExpanded()
        fun onHeaderCollapsed()
        fun showActions()
        fun hideActions()
    }

    companion object {
        const val OVERSCROLL_OFFSET = 100
        const val EXPAND_PERCENT = 0.4f
        const val EXPANDED_PERCENT = 0.9f
        const val TEXTS_FROM_BOTTOM = 80f
    }

    private var scrollState: ScrollState = ScrollState.NormalToCompact(this, percent = 0f)
    private var targetState: ScrollState = scrollState

    var realScrollOffset = 0
    private var currentScrollOffset = 0
        set(value) {
            field = value
            scrollState = scrollStateForOffset(value)
            render()
        }

    private var targetScrollOffset = 0
        set(value) {
            field = value
            targetState = scrollStateForOffset(value)
        }
    var targetIsCollapsed = true
        private set
    val isInCompactState: Boolean
        get() {
            return (scrollState as? ScrollState.NormalToCompact)?.percent == 1f
        }
    val isInExpandedState: Boolean
        get() {
            return scrollState is ScrollState.Expanded || scrollState is ScrollState.OverScroll
        }

    var titleCompactTranslationX = 0f
    var subtitleCompactTranslationX = 0f

    // Flag to handle expand animation when user taps on Nft Image
    var isAnimatingImageToExpand = false

    val imageSize = 144.dp
    val topExtraPadding = safeAreaPadding + WNavigationBar.DEFAULT_HEIGHT.dp

    // Height of the view when is in normal mode
    private val normalHeight = 244.dp + topExtraPadding

    private val compactOffset = OVERSCROLL_OFFSET.dp + viewWidth - topExtraPadding
    val collapsedOffset = OVERSCROLL_OFFSET.dp + viewWidth - normalHeight

    private var offsetAnimator: ValueAnimator? = null

    val avatarCoverFlowView = WCoverFlowView(context).apply {
        setCovers(collectionNFTs.map {
            WCoverFlowView.CoverItem(it.thumbnail)
        })
        setSelectedIndex(collectionNFTs.indexOf(nft))
    }

    val avatarImageView = WCustomImageView(context).apply {
        defaultRounding = Content.Rounding.Radius(12f.dp)
        defaultPlaceholder = Content.Placeholder.Color(WColor.Transparent)
        setOnClickListener {
            if (isTracking || avatarCoverFlowView.scrollState != WCoverFlowView.ScrollState.IDLE)
                return@setOnClickListener
            delegate.get()?.onExpandTapped()
        }
        setOnLongClickListener {
            if (isTracking || avatarCoverFlowView.scrollState != WCoverFlowView.ScrollState.IDLE)
                return@setOnLongClickListener true
            delegate.get()?.onPreviewTapped()
            return@setOnLongClickListener true
        }
    }
    val avatarPosition: Rect
        get() {
            val centerX = avatarImageView.x + avatarImageView.width * 0.5f
            val centerY = avatarImageView.y + avatarImageView.height * 0.5f

            val halfWidth = avatarImageView.width * 0.5f * avatarImageView.scaleX
            val halfHeight = avatarImageView.height * 0.5f * avatarImageView.scaleY

            return Rect(
                (centerX - halfWidth).roundToInt(),
                (centerY - halfHeight).roundToInt(),
                (centerX + halfWidth).roundToInt(),
                (centerY + halfHeight).roundToInt()
            )
        }
    val avatarCornerRadius: Float
        get() {
            return lerpProperty { it.avatarRounding }
        }
    private val isAnimatedNft: Boolean
        get() {
            return nft.metadata?.lottie?.isNotBlank() == true
        }
    val animationView: WAnimationView by lazy {
        val v = WAnimationView(context)
        v.setBackgroundColor(Color.TRANSPARENT, 12f.dp, true)
        v.scaleType = ScaleType.FIT_CENTER
        v
    }

    private val titleLabel = WLabel(context).apply {
        setStyle(22f, WFont.Medium)
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MARQUEE
        setSingleLine(true)
        marqueeRepeatLimit = -1
        pivotX = 0f
        isHorizontalFadingEdgeEnabled = true
    }
    private val subtitleLabel = WLabel(context).apply {
        setStyle(16f, WFont.Regular)
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MARQUEE
        setSingleLine(true)
        marqueeRepeatLimit = -1
        isHorizontalFadingEdgeEnabled = true
    }
    private val topGradientView = WBaseView(context).apply {
        alpha = 0f
    }
    private val bottomGradientView = WBaseView(context).apply {
        alpha = 0f
    }

    var isTracking: Boolean = false
        set(value) {
            field = value
            titleLabel.setSelected(!isTracking)
            subtitleLabel.setSelected(!isTracking)
        }

    init {
        id = generateViewId()
        addView(
            avatarCoverFlowView,
            LayoutParams(LayoutParams.MATCH_PARENT, 180.dp).apply {
                marginStart = (-10).dp
                marginEnd = (-10).dp
            }
        )
        addView(avatarImageView, LayoutParams(imageSize, imageSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        addView(animationView, LayoutParams(imageSize, imageSize).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        addView(
            topGradientView,
            LayoutParams(LayoutParams.MATCH_PARENT, 80.dp)
        )
        addView(
            bottomGradientView,
            LayoutParams(LayoutParams.MATCH_PARENT, 80.dp).apply {
                gravity = Gravity.BOTTOM
            }
        )
        addView(
            titleLabel,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.TOP
                marginStart = 16.dp
                marginEnd = 16.dp
            })
        addView(
            subtitleLabel,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.TOP
                marginStart = 16.dp
                marginEnd = 16.dp
            })
        // Start MARQUEE ellipsize after a second, if is not tracking.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isTracking) {
                titleLabel.isSelected = true
                subtitleLabel.isSelected = true
            }
        }, 1000)
        configNft()
        updateTheme()
        avatarCoverFlowView.setOnCoverSelectedListener { index ->
            nft = collectionNFTs[index]
            delegate.get()?.onNftChanged(nft)
            configNft()
        }
        avatarCoverFlowView.setOnScrollStateChangeListener({ scrollState ->
            when (scrollState) {
                WCoverFlowView.ScrollState.IDLE -> {
                    if (!avatarImageView.isVisible) {
                        avatarImageView.visibility = VISIBLE
                        avatarImageView.alpha = 0f
                        avatarImageView.fadeIn(AnimationConstants.VERY_VERY_QUICK_ANIMATION)
                        animationView.isGone = !isAnimatedNft
                        avatarImageView.alpha = 0f
                        animationView.fadeIn(AnimationConstants.VERY_VERY_QUICK_ANIMATION)
                    }
                }

                WCoverFlowView.ScrollState.DRAGGING -> {
                    avatarImageView.visibility = INVISIBLE
                    animationView.isGone = true
                }

                else -> {}
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        currentScrollOffset = expandPercentToOffset(0f)
    }

    fun configNft() {
        nft.image?.let {
            avatarImageView.set(Content.ofUrl(it), nft.thumbnail)
        } ?: run {
            avatarImageView.setImageDrawable(null)
        }
        animationView.isGone =
            !isAnimatedNft || avatarCoverFlowView.scrollState != WCoverFlowView.ScrollState.IDLE
        if (isAnimatedNft) {
            animationView.playFromUrl(nft.metadata!!.lottie!!, onStart = {})
        }
        updateTitleLabel()
        updateSubtitleText()
        render()
    }

    fun update(offset: Int) {
        updateLayoutParams {
            height = max(viewWidth + OVERSCROLL_OFFSET.dp - offset, topExtraPadding)
        }
        val percent = offsetToExpandPercent(offset)
        val wasCollapsed = targetIsCollapsed
        targetIsCollapsed = !isAnimatingImageToExpand && percent < EXPAND_PERCENT
        realScrollOffset = offset
        if (offset < OVERSCROLL_OFFSET.dp) {
            this.targetScrollOffset = offset
        } else {
            this.targetScrollOffset =
                if (targetIsCollapsed)
                    offset
                else {
                    val expandPercent = if (isAnimatingImageToExpand) 0f else EXPAND_PERCENT
                    expandPercentToOffset(EXPANDED_PERCENT + (percent - expandPercent) * (1 - EXPANDED_PERCENT) / (1 - expandPercent))
                }
        }
        if (wasCollapsed == targetIsCollapsed) {
            if (offsetAnimator == null) {
                currentScrollOffset = targetScrollOffset
            }
        } else {
            if (targetIsCollapsed) {
                delegate.get()?.onHeaderCollapsed()
            } else {
                delegate.get()?.onHeaderExpanded()
            }
            titleLabel.animateTextColor(
                if (targetIsCollapsed) WColor.PrimaryText.color else Color.WHITE,
                AnimationConstants.QUICK_ANIMATION
            )
            subtitleLabel.animateTextColor(
                if (targetIsCollapsed) WColor.SecondaryText.color else Color.WHITE,
                AnimationConstants.QUICK_ANIMATION
            )

            val startOffset = currentScrollOffset
            offsetAnimator?.cancel()
            offsetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                setDuration(AnimationConstants.QUICK_ANIMATION)
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    currentScrollOffset =
                        startOffset + ((this@NftHeaderView.targetScrollOffset - startOffset) * animator.animatedFraction).roundToInt()
                }
                doOnEnd {
                    offsetAnimator = null
                }
                start()
            }
        }
    }

    fun nearestScrollPosition(): Int? {
        if (currentScrollOffset < OVERSCROLL_OFFSET.dp) {
            return OVERSCROLL_OFFSET.dp
        } else if (currentScrollOffset < collapsedOffset) {
            return OVERSCROLL_OFFSET.dp + (if (targetIsCollapsed) viewWidth - normalHeight else 0)
        } else {
            val compactPercent = (scrollState as? ScrollState.NormalToCompact)?.percent ?: 1f
            return when {
                compactPercent == 1f -> {
                    null
                }

                compactPercent <= 0.8 -> {
                    OVERSCROLL_OFFSET.dp + viewWidth - normalHeight
                }

                else -> {
                    compactOffset
                }
            }
        }
    }

    fun expandPercentToOffset(percent: Float): Int {
        return (OVERSCROLL_OFFSET.dp + (1 - percent) * (viewWidth.toFloat() - normalHeight)).toInt()
    }

    fun onDestroy() {
    }

    fun onPreviewStarted() {
        avatarImageView.visibility = INVISIBLE
        avatarCoverFlowView.shouldRenderCenterItem = false
    }

    fun onPreviewEnded() {
        if (animationView.parent != this) {
            addView(animationView, 2, LayoutParams(imageSize, imageSize).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            })
            renderAnimatedNft()
        }
        avatarImageView.visibility = VISIBLE
        avatarCoverFlowView.shouldRenderCenterItem = true
    }

    fun showLabels() {
        topGradientView.fadeIn()
        bottomGradientView.fadeIn()
        titleLabel.fadeIn()
        subtitleLabel.fadeIn()
    }

    fun hideLabels() {
        topGradientView.fadeOut()
        bottomGradientView.fadeOut()
        titleLabel.fadeOut()
        subtitleLabel.fadeOut()
    }

    private fun offsetToExpandPercent(offset: Int): Float {
        return 1 - (offset - OVERSCROLL_OFFSET.dp) / (viewWidth.toFloat() - normalHeight)
    }

    private fun scrollStateForOffset(offset: Int): ScrollState {
        return if (offset < OVERSCROLL_OFFSET.dp) {
            ScrollState.OverScroll(
                headerView = this,
                overscroll = OVERSCROLL_OFFSET.dp - offset
            )
        } else if (offset < OVERSCROLL_OFFSET.dp + viewWidth - normalHeight) {
            val percent = offsetToExpandPercent(offset)
            if (!isAnimatingImageToExpand && percent < EXPAND_PERCENT) {
                ScrollState.NormalToExpand(
                    headerView = this,
                    percent = percent / EXPAND_PERCENT
                )
            } else {
                val expandStartPercent = if (isAnimatingImageToExpand) 0f else EXPAND_PERCENT
                val statePercent =
                    min(
                        1f,
                        (percent - expandStartPercent) / (EXPANDED_PERCENT - expandStartPercent)
                    )
                ScrollState.Expanded(
                    headerView = this,
                    percent = statePercent,
                    expandStartPercent = expandStartPercent
                )
            }
        } else {
            val diff = normalHeight - topExtraPadding
            val percent =
                (offset - (OVERSCROLL_OFFSET.dp + viewWidth.toFloat() - normalHeight)) / diff
            ScrollState.NormalToCompact(headerView = this, percent = min(1f, percent))
        }
    }

    private var isShowingActions = true

    private fun lerpProperty(a: Float, b: Float): Float {
        if (offsetAnimator == null ||
            (scrollState is ScrollState.Expanded && targetState is ScrollState.Expanded)
        )
            return a
        return lerp(a, b, offsetAnimator!!.animatedFraction)
    }

    private inline fun lerpProperty(selector: (ScrollState) -> Float): Float {
        return lerpProperty(selector(scrollState), selector(targetState))
    }

    private fun render() {
        val avatarWidth = lerpProperty { it.avatarWidth.toFloat() }.roundToInt()
        val avatarHeight = lerpProperty { it.avatarHeight.toFloat() }.roundToInt()
        val avatarRounding = lerpProperty { it.avatarRounding.toFloat() }
        val avatarTranslationX = lerpProperty { it.avatarTranslationX.toFloat() }
        val avatarTranslationY = lerpProperty { it.avatarTranslationY.toFloat() }
        val avatarScale = lerpProperty { it.avatarScale.toFloat() }
        val titlePivotX = lerpProperty { it.titlePivotX.toFloat() }
        val titleScale = lerpProperty { it.titleScale.toFloat() }
        val titleTranslationX = lerpProperty { it.titleTranslationX.toFloat() }
        val titleTranslationY = lerpProperty { it.titleTranslationY.toFloat() }
        val subtitlePivotX = lerpProperty { it.subtitlePivotX.toFloat() }
        val subtitleScale = lerpProperty { it.subtitleScale.toFloat() }
        val subtitleTranslationX = lerpProperty { it.subtitleTranslationX.toFloat() }
        val subtitleTranslationY = lerpProperty { it.subtitleTranslationY.toFloat() }

        if (avatarImageView.height != height) {
            avatarImageView.updateLayoutParams {
                width = avatarWidth
                height = avatarHeight
            }
            avatarImageView.hierarchy.roundingParams =
                RoundingParams.fromCornersRadius(avatarRounding)
        }
        avatarImageView.translationX = avatarTranslationX
        avatarImageView.translationY = avatarTranslationY
        avatarImageView.scaleX = avatarScale
        avatarImageView.scaleY = avatarImageView.scaleX

        titleLabel.pivotX = titlePivotX
        titleLabel.scaleX = titleScale
        titleLabel.scaleY = titleLabel.scaleX
        titleLabel.translationX = titleTranslationX
        titleLabel.translationY = titleTranslationY
        subtitleLabel.pivotX = subtitlePivotX
        subtitleLabel.scaleX = subtitleScale
        subtitleLabel.scaleY = subtitleLabel.scaleX
        subtitleLabel.translationX = subtitleTranslationX
        subtitleLabel.translationY = subtitleTranslationY
        subtitleLabel.alpha = 1f

        if (scrollState !is ScrollState.NormalToCompact || (scrollState as ScrollState.NormalToCompact).percent > 0f) {
            avatarImageView.visibility = VISIBLE
            if (isAnimatedNft)
                animationView.visibility = VISIBLE
        }

        when (scrollState) {
            is ScrollState.NormalToCompact -> {
                val percent = (scrollState as ScrollState.NormalToCompact).percent
                avatarCoverFlowView.setCollapsed(percent)

                if (!isShowingActions && percent <= 0.8)
                    showActions()
                if (isShowingActions && percent > 0.8)
                    hideActions()

                topGradientView.alpha = 0f
                bottomGradientView.alpha = 0f

                titleLabel.maxWidth =
                    lerp(viewWidth.toFloat(), viewWidth - 120f.dp, percent).roundToInt()
                subtitleLabel.maxWidth = titleLabel.maxWidth
            }

            is ScrollState.NormalToExpand -> {
                var percent = (scrollState as ScrollState.NormalToExpand).percent
                if (!isShowingActions)
                    showActions()
                if (!isAnimatingImageToExpand) {
                    avatarCoverFlowView.setExpanded((percent * EXPAND_PERCENT).pow(2f))
                }
                if (!isShowingActions)
                    showActions()
                topGradientView.alpha = 0f
                bottomGradientView.alpha = 0f

                titleLabel.maxWidth = viewWidth
                subtitleLabel.maxWidth = titleLabel.maxWidth
            }

            is ScrollState.Expanded -> {
                if (!isShowingActions)
                    showActions()
                val percent = (scrollState as ScrollState.Expanded).percent
                avatarCoverFlowView.setExpanded(
                    lerp(
                        ((scrollState as ScrollState.Expanded).expandStartPercent).pow(2),
                        1f,
                        (scrollState as ScrollState.Expanded).percent
                    )
                )
                if (percent == 1f)
                    isAnimatingImageToExpand = false
                topGradientView.alpha = percent
                bottomGradientView.alpha = percent

                titleLabel.maxWidth = viewWidth
                subtitleLabel.maxWidth = titleLabel.maxWidth
            }

            is ScrollState.OverScroll -> {
                if (!isShowingActions)
                    showActions()
                avatarCoverFlowView.setExpanded(1f)
                topGradientView.alpha = 1f
                bottomGradientView.alpha = 1f

                titleLabel.maxWidth = viewWidth
                subtitleLabel.maxWidth = titleLabel.maxWidth
            }
        }
        if (isAnimatedNft)
            renderAnimatedNft()
        avatarCoverFlowView.translationX = avatarImageView.translationX
        avatarCoverFlowView.translationY = avatarImageView.translationY - 18.dp
    }

    private fun renderAnimatedNft() {
        if (animationView.parent != this)
            return
        animationView.apply {
            updateLayoutParams {
                width = avatarImageView.width
                height = avatarImageView.height
            }
            setBackgroundColor(
                Color.TRANSPARENT,
                avatarImageView.hierarchy.roundingParams?.cornersRadii?.get(0) ?: 0f,
                true
            )
            scaleX = avatarImageView.scaleX
            scaleY = avatarImageView.scaleY
            translationX = avatarImageView.translationX
            translationY = avatarImageView.translationY
        }
    }

    override fun updateTheme() {
        titleLabel.setTextColor(if (targetIsCollapsed) WColor.PrimaryText.color else Color.WHITE)
        subtitleLabel.setTextColor(if (targetIsCollapsed) WColor.SecondaryText.color else Color.WHITE)
        topGradientView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.BLACK, Color.TRANSPARENT)
        ).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        bottomGradientView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.BLACK)
        ).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        updateSubtitleText()
    }

    private fun updateTitleLabel() {
        titleLabel.text = nft.name
        centerTitle()
    }

    private fun updateSubtitleText() {
        subtitleLabel.text =
            if (nft.collectionName.isNullOrBlank())
                LocaleController.getString(R.string.HiddenNFTs_Standalone)
            else
                nft.collectionName
        centerSubtitle()
    }

    private fun centerTitle() {
        val textWidth = titleLabel.paint.measureText(titleLabel.text.toString())
        val translationX = (viewWidth - textWidth - 32.dp) / 2f
        titleCompactTranslationX = max(0f, translationX)
    }

    private fun centerSubtitle() {
        val textWidth = subtitleLabel.paint.measureText(subtitleLabel.text.toString())
        val translationX = (viewWidth - textWidth - 32.dp) / 2f
        subtitleCompactTranslationX = max(0f, translationX)
    }

    private fun showActions() {
        isShowingActions = true
        delegate.get()?.showActions()
    }

    private fun hideActions() {
        isShowingActions = false
        delegate.get()?.hideActions()
    }

}
