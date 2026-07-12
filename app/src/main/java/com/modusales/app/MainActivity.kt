package com.modusales.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var lastBackPressedAt: Long = 0L

    companion object {
        private const val BACK_PRESS_EXIT_WINDOW_MS = 2000L

        // 이 호스트만 앱 내 WebView에서 직접 이동, 그 외(K-APT/나라장터 등 외부 사이트)는
        // 크롬 커스텀 탭으로 열어 WebView 뒤로가기 히스토리를 SPA 전용으로 유지한다.
        private const val APP_HOST = "modu-sales-7tnu.vercel.app"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark status/navigation-bar icons so both bars stay visible on the
        // light backdrop regardless of the device's light/dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(true)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    if (uri.host == APP_HOST) return false
                    launchExternal(uri)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    // target="_blank"/window.open() 요청: 실제 이동 없이 목표 URL만 가로채는
                    // 임시 WebView를 붙였다가, URL을 확인하는 즉시 커스텀 탭으로 열고 버린다.
                    val transport = WebView(this@MainActivity).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                popupView: WebView,
                                popupRequest: WebResourceRequest,
                            ): Boolean {
                                launchExternal(popupRequest.url)
                                return true
                            }
                        }
                    }
                    (resultMsg.obj as WebView.WebViewTransport).webView = transport
                    resultMsg.sendToTarget()
                    return true
                }
            }
        }

        // Root container fills the whole (edge-to-edge) window; its padding reserves the
        // system-bar regions so the WebView inside fills only the inner content area.
        val root = FrameLayout(this).apply {
            // White backdrop so the padded status/navigation-bar regions render as
            // visible bars (dark icons on white) instead of blank/overlapping areas.
            setBackgroundColor(Color.WHITE)
            addView(webView)
        }
        setContentView(root)

        // Keep the WebView clear of the status/navigation bars under edge-to-edge.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Let the system back gesture walk the web history before leaving the app;
        // once there's no more web history, require a second press within 2s to exit.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt < BACK_PRESS_EXIT_WINDOW_MS) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(
                        this@MainActivity,
                        "뒤로가기를 한 번 더 누르면 종료됩니다",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        })

        webView.loadUrl("https://modu-sales-7tnu.vercel.app/")
    }

    private fun launchExternal(uri: Uri) {
        CustomTabsIntent.Builder().build().launchUrl(this, uri)
    }
}
