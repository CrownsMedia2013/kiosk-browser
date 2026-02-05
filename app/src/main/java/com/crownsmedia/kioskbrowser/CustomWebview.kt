package com.crownsmedia.kioskbrowser

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

class CustomWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}