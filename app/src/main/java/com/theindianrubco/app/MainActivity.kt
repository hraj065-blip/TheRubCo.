package com.theindianrubco.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var splash: View
    private lateinit var errorPanel: View
    private lateinit var progress: ProgressBar
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasPageLoaded = false
    private var navigationCommitted = false
    private val loadTimeout = Runnable {
        if (!navigationCommitted) showNetworkError()
    }

    companion object {
        private const val START_URL = "https://indian-rub-co-storefront.vercel.app/"
        private const val SITE_HOST = "indian-rub-co-storefront.vercel.app"
        private const val CREAM = 0xFFF7F1E4.toInt()
        private const val MAROON = 0xFF7A1F2B.toInt()
        private const val INK = 0xFF2C1D1F.toInt()
        private const val MUTED = 0xFF786C68.toInt()
        private const val LOAD_TIMEOUT_MS = 30_000L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = CREAM
        window.navigationBarColor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) CREAM else MAROON
        var systemUiFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            systemUiFlags = systemUiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            systemUiFlags = systemUiFlags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            systemUiFlags = systemUiFlags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        window.decorView.systemUiVisibility = systemUiFlags
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = FrameLayout(this).apply { setBackgroundColor(CREAM) }
        webView = WebView(this).apply {
            setBackgroundColor(CREAM)
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true // Required by the deployed React storefront.
            settings.domStorageEnabled = true // Cart, order history and preferences use localStorage.
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress in 1..99 && hasPageLoaded) View.VISIBLE else View.GONE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleNavigation(request.url)

                @Deprecated("Deprecated in Android API")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    handleNavigation(Uri.parse(url))

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    navigationCommitted = false
                    mainHandler.removeCallbacks(loadTimeout)
                    mainHandler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
                    if (!hasPageLoaded) splash.visibility = View.VISIBLE
                    errorPanel.visibility = View.GONE
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    navigationCommitted = true
                    hasPageLoaded = true
                    mainHandler.removeCallbacks(loadTimeout)
                    splash.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    navigationCommitted = true
                    hasPageLoaded = true
                    mainHandler.removeCallbacks(loadTimeout)
                    splash.visibility = View.GONE
                    errorPanel.visibility = View.GONE
                    progress.visibility = View.GONE
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        mainHandler.removeCallbacks(loadTimeout)
                        showNetworkError()
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.cancel()
                    if (view?.url == error?.url) {
                        mainHandler.removeCallbacks(loadTimeout)
                        showNetworkError()
                    }
                }

                @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    view?.stopLoading()
                    (view?.parent as? ViewGroup)?.removeView(view)
                    recreate()
                    return true
                }
            }
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(MAROON)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE5D8C4.toInt())
            visibility = View.GONE
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))
        splash = createSplash()
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        errorPanel = createErrorPanel()
        errorPanel.visibility = View.GONE
        root.addView(errorPanel, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets
        }
        setContentView(root)

        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        if (!restored) webView.loadUrl(START_URL)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme == "https" && uri.host == SITE_HOST) return false
        if (scheme == "http" && uri.host == SITE_HOST) {
            // Never downgrade the storefront connection.
            return true
        }
        return when (scheme) {
            "https", "http", "tel", "mailto", "sms", "geo", "market" -> openExternal(uri)
            "intent" -> openIntent(uri)
            else -> true
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app is available to open this link", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun openIntent(uri: Uri): Boolean {
        return try {
            val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            startActivity(intent)
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun showNetworkError() {
        navigationCommitted = false
        splash.visibility = View.GONE
        progress.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    private fun createSplash(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(CREAM)
        }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.brand_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        column.addView(mark, LinearLayout.LayoutParams(dp(104), dp(104)).apply { bottomMargin = dp(24) })
        column.addView(label("THE INDIAN RUB CO.", 21f, INK, true).apply { letterSpacing = 0.08f })
        column.addView(label("RUB. COOK. ENJOY.", 11f, MUTED, true).apply {
            letterSpacing = 0.22f
            setPadding(0, dp(9), 0, dp(24))
        })
        val bar = ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(MAROON) }
        column.addView(bar, LinearLayout.LayoutParams(dp(42), dp(42)))
        return column
    }

    private fun createErrorPanel(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(CREAM)
        }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.brand_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }
        column.addView(mark, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(20) })
        column.addView(label("A little pause in the kitchen", 23f, INK, true).apply { gravity = Gravity.CENTER })
        column.addView(label("We couldn’t reach the shop. Check your connection and try again.", 15f, MUTED, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(22))
        })
        val retry = Button(this).apply {
            text = "Try again"
            setTextColor(Color.WHITE)
            textSize = 15f
            isAllCaps = false
            background = rounded(MAROON, dp(24))
            setPadding(dp(30), dp(8), dp(30), dp(8))
            setOnClickListener {
                errorPanel.visibility = View.GONE
                splash.visibility = View.VISIBLE
                webView.reload()
            }
        }
        column.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        column.addView(label("Open the shop in your browser", 14f, MAROON, true).apply {
            setPadding(dp(12), dp(18), dp(12), dp(8))
            setOnClickListener { openExternal(Uri.parse(START_URL)) }
        })
        return column
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.create("serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        gravity = Gravity.CENTER
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(loadTimeout)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

}
