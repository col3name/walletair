package org.mytonwallet.app_air.uiinappbrowser.views

import WNavigationController
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import org.mytonwallet.app_air.icons.R
import org.mytonwallet.app_air.uicomponents.base.WNavigationBar
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.helpers.WFont
import org.mytonwallet.app_air.uicomponents.image.Content
import org.mytonwallet.app_air.uicomponents.image.WCustomImageView
import org.mytonwallet.app_air.uicomponents.widgets.BackDrawable
import org.mytonwallet.app_air.uicomponents.widgets.WImageButton
import org.mytonwallet.app_air.uicomponents.widgets.WLabel
import org.mytonwallet.app_air.uicomponents.widgets.WThemedView
import org.mytonwallet.app_air.uicomponents.widgets.WView
import org.mytonwallet.app_air.uicomponents.widgets.addRippleEffect
import org.mytonwallet.app_air.uicomponents.widgets.fadeIn
import org.mytonwallet.app_air.uicomponents.widgets.fadeOut
import org.mytonwallet.app_air.uicomponents.widgets.menu.WMenuPopup
import org.mytonwallet.app_air.uiinappbrowser.InAppBrowserVC
import org.mytonwallet.app_air.walletcontext.helpers.LocaleController
import org.mytonwallet.app_air.walletcontext.theme.WColor
import org.mytonwallet.app_air.walletcontext.theme.color
import org.mytonwallet.app_air.walletcontext.utils.colorWithAlpha
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class InAppBrowserTopBarView(
    private val viewController: InAppBrowserVC,
    private val tabBarController: WNavigationController.ITabBarController?,
    private val minimizeStarted: () -> Unit,
    private val maximizeFinished: () -> Unit,
) : WView(viewController.context), WThemedView {

    private val backDrawable = BackDrawable(context, false).apply {
        setRotation(1f, false)
    }

    private val iconView = WCustomImageView(context).apply {
        defaultRounding = Content.Rounding.Radius(8f.dp)
        alpha = 0f
    }

    private val titleLabel: WLabel by lazy {
        WLabel(context).apply {
            setTextColor(WColor.PrimaryText)
            setStyle(22F, WFont.Medium)
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this@apply,
                12,
                22,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            ellipsize = TextUtils.TruncateAt.END
            pivotX = 0f
        }
    }

    private val backButton: WImageButton by lazy {
        val btn = WImageButton(context)
        btn.setImageDrawable(backDrawable)
        btn.setOnClickListener {
            backPressed()
        }
        btn
    }

    private val minimizeButton: WImageButton by lazy {
        val v = WImageButton(context)
        v.setPadding(8.dp)
        v.setOnClickListener {
            minimize()
        }
        v
    }

    private val moreButton: WImageButton by lazy {
        val v = WImageButton(context)
        v.setPadding(8.dp)
        v
    }

    override fun setupViews() {
        super.setupViews()

        minHeight =
            WNavigationBar.DEFAULT_HEIGHT_TINY.dp +
                (viewController.navigationController?.getSystemBars()?.top ?: 0)
        maxHeight = minHeight
        addView(titleLabel, LayoutParams(0, WRAP_CONTENT))
        addView(backButton, ViewGroup.LayoutParams(40.dp, 40.dp))
        addView(moreButton, LayoutParams(40.dp, 40.dp))
        if (tabBarController != null) {
            addView(minimizeButton, LayoutParams(40.dp, 40.dp))
        }
        moreButton.setOnClickListener {
            morePressed()
        }
        setOnClickListener {
            tabBarController?.maximize()
        }
        addView(iconView, LayoutParams(24.dp, 24.dp))

        setConstraints {
            toTopPx(titleLabel, viewController.navigationController?.getSystemBars()?.top ?: 0)
            toBottom(titleLabel)
            startToEnd(titleLabel, backButton, 8f)
            endToStart(titleLabel, if (tabBarController != null) minimizeButton else moreButton, 8f)
            centerYToCenterY(iconView, titleLabel)
            startToStart(iconView, titleLabel, 4f)
            toTopPx(backButton, viewController.navigationController?.getSystemBars()?.top ?: 0)
            toBottom(backButton)
            toStart(backButton, 16f)
            toTopPx(moreButton, viewController.navigationController?.getSystemBars()?.top ?: 0)
            toBottom(moreButton)
            toEnd(moreButton, 16f)
            if (tabBarController != null) {
                endToStart(minimizeButton, moreButton, 4f)
                toTopPx(
                    minimizeButton,
                    viewController.navigationController?.getSystemBars()?.top ?: 0
                )
                toBottom(minimizeButton)
            }
        }

        setBackgroundColor(Color.TRANSPARENT)
        updateTheme()
    }

    override fun updateTheme() {
        if (minimized) {
            setBackgroundColor(WColor.SearchFieldBackground.color)
            backDrawable.setColor(WColor.PrimaryText.color)
            backDrawable.setRotatedColor(WColor.PrimaryText.color)
        } else {
            backDrawable.setColor(WColor.SecondaryText.color)
            backDrawable.setRotatedColor(WColor.SecondaryText.color)
        }
        val moreDrawable =
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_more
            )?.apply {
                setTint(WColor.SecondaryText.color)
            }
        moreButton.setImageDrawable(moreDrawable)
        moreButton.addRippleEffect(WColor.BackgroundRipple.color, 20f.dp)
        val minimizeDrawable =
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_arrow_bottom_24
            )?.apply {
                setTint(WColor.SecondaryText.color)
            }
        minimizeButton.setImageDrawable(minimizeDrawable)
        minimizeButton.addRippleEffect(WColor.BackgroundRipple.color, 20f.dp)
    }

    fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        return ColorUtils.blendARGB(color1, color2, ratio)
    }

    private var minimized = false
    private var isMinimizing = false
    private fun minimize() {
        if (isMinimizing)
            return
        if (minimized) {
            tabBarController?.maximize()
            return
        }
        isMinimizing = true
        minimizeStarted()
        viewController.view.post {
            titleLabel.pivotY = titleLabel.height / 2f
            backDrawable.setRotation(1f, true)
            tabBarController?.minimize(viewController.navigationController!!, onProgress = {
                val heightDiff = (viewController.navigationController?.getSystemBars()?.top ?: 0)
                val parent = parent as ViewGroup
                parent.layoutParams = (parent.layoutParams as MarginLayoutParams).apply {
                    topMargin = (-it * heightDiff).roundToInt()
                }
                moreButton.layoutParams = (moreButton.layoutParams as MarginLayoutParams).apply {
                    rightMargin = (16.dp - it * 56.dp).roundToInt()
                }
                backButton.layoutParams = (backButton.layoutParams as MarginLayoutParams).apply {
                    leftMargin = (4.dp + (1 - it) * 12.dp).roundToInt()
                }
                titleLabel.scaleX = 1 - 0.23f * it
                titleLabel.scaleY = titleLabel.scaleX
                minimizeButton.rotation = it * 180
                setBackgroundColor(WColor.SearchFieldBackground.color.colorWithAlpha((it * 255).toInt()))
                val drawableColor =
                    blendColors(
                        WColor.SecondaryText.color,
                        WColor.PrimaryText.color,
                        it
                    )
                backDrawable.setColor(drawableColor)
                backDrawable.setRotatedColor(drawableColor)
                minimizeButton.drawable.setTint(drawableColor)
                titleLabel.translationX = 35f.dp * it
                iconView.alpha = it
                if (it == 1f) {
                    minimized = true
                    isMinimizing = false
                }
            }, onMaximizeProgress = {
                if (it == 0f) {
                    updateBackButton(true)
                }
                val heightDiff = (viewController.navigationController?.getSystemBars()?.top ?: 0)
                val parent = parent as ViewGroup
                parent.layoutParams = (parent.layoutParams as MarginLayoutParams).apply {
                    topMargin = (-(1 - it) * heightDiff).roundToInt()
                }
                moreButton.layoutParams = (moreButton.layoutParams as MarginLayoutParams).apply {
                    rightMargin = (16.dp - (1 - it) * 56.dp).roundToInt()
                }
                backButton.layoutParams = (backButton.layoutParams as MarginLayoutParams).apply {
                    leftMargin = (4.dp + it * 12.dp).roundToInt()
                }
                titleLabel.scaleX = 1 - 0.23f * (1 - it)
                titleLabel.scaleY = titleLabel.scaleX
                minimizeButton.rotation = 180 + it * 180
                titleLabel.setTextColor(
                    blendColors(
                        WColor.SecondaryText.color,
                        WColor.PrimaryText.color,
                        it
                    )
                )
                setBackgroundColor(WColor.SearchFieldBackground.color.colorWithAlpha(((1 - it) * 255).toInt()))
                val drawableColor =
                    blendColors(
                        WColor.PrimaryText.color,
                        WColor.SecondaryText.color,
                        it
                    )
                backDrawable.setColor(drawableColor)
                backDrawable.setRotatedColor(drawableColor)
                minimizeButton.drawable.setTint(drawableColor)
                titleLabel.translationX = 36f.dp * (1 - it)
                iconView.alpha = 1 - it
                if (it == 1f) {
                    minimized = false
                    isMinimizing = false
                    maximizeFinished()
                }
            })
        }
    }

    fun backPressed() {
        if (isMinimizing)
            return
        if (minimized) {
            tabBarController?.dismissMinimized()
            return
        }
        if (viewController.webView.canGoBack()) {
            viewController.webView.goBack()
            updateBackButton(true)
        } else {
            viewController.window?.dismissLastNav()
        }
    }

    private var isShowingBackArrow = false
    fun updateBackButton(animated: Boolean) {
        isShowingBackArrow = if (viewController.webView.canGoBack() && !isShowingBackArrow) {
            true
        } else if (!viewController.webView.canGoBack() && isShowingBackArrow) {
            false
        } else {
            return
        }
        backDrawable.setRotation(if (isShowingBackArrow) 0f else 1f, animated)
    }

    private fun morePressed() {
        WMenuPopup.present(
            moreButton,
            listOf(
                WMenuPopup.Item(
                    null,
                    LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_Reload)
                ) {
                    viewController.webView.reload()
                },
                WMenuPopup.Item(
                    null,
                    LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_OpenInBrowser)
                ) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setData(Uri.parse(viewController.config.url))
                    viewController.window?.startActivity(intent)
                },
                WMenuPopup.Item(
                    null,
                    LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_CopyUrl)
                ) {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip =
                        ClipData.newPlainText(
                            LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_CopyUrl),
                            viewController.config.url
                        )
                    clipboard.setPrimaryClip(clip)
                },
                WMenuPopup.Item(
                    null,
                    LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_Share)
                ) {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.setType("text/plain")
                    shareIntent.putExtra(Intent.EXTRA_TEXT, viewController.config.url)
                    viewController.window?.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            LocaleController.getString(org.mytonwallet.app_air.walletcontext.R.string.InAppBrowser_Share)
                        )
                    )
                }),
            offset = (-100).dp,
            aboveView = true
        )
    }

    fun updateTitle(newTitle: String, animated: Boolean) {
        if (!animated) {
            titleLabel.text = newTitle
            return
        }
        titleLabel.fadeOut {
            titleLabel.text = newTitle
            titleLabel.fadeIn { }
        }
    }

    fun setIconUrl(url: String) {
        if (tabBarController == null)
            return
        iconView.set(Content.ofUrl(url))
    }

    fun setIconBitmap(bitmap: Bitmap?) {
        if (tabBarController == null)
            return
        iconView.setImageBitmap(bitmap)
    }
}
