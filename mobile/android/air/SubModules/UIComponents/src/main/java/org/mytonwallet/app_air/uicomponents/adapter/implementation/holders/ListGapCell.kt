package org.mytonwallet.app_air.uicomponents.adapter.implementation.holders

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import org.mytonwallet.app_air.uicomponents.adapter.BaseListHolder
import org.mytonwallet.app_air.uicomponents.adapter.implementation.Item
import org.mytonwallet.app_air.uicomponents.drawable.SeparatorBackgroundDrawable
import org.mytonwallet.app_air.uicomponents.extensions.dp
import org.mytonwallet.app_air.uicomponents.widgets.WThemedView
import org.mytonwallet.app_air.walletcontext.theme.ThemeManager
import org.mytonwallet.app_air.walletcontext.theme.WColor

class ListGapCell(
    context: Context,
) : View(context), WThemedView {
    var showSeparator: Boolean = true
        set(value) {
            field = value
            background = if (!ThemeManager.uiMode.hasRoundedCorners && showSeparator)
                drawable
            else
                null
        }

    val drawable: Drawable by lazy {
        SeparatorBackgroundDrawable().apply {
            backgroundWColor = WColor.SecondaryBackground
            isTop = true
        }
    }

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12.dp)
        showSeparator = true
        updateTheme()
    }

    override fun updateTheme() {
        invalidate()
    }

    class Holder(parent: ViewGroup) :
        BaseListHolder<Item.Gap>(ListGapCell(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12.dp)
        }) {
        override fun onBind(item: Item.Gap) {}
    }
}




