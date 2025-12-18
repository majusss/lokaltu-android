package pl.lokaltu.android

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val webView = WebView(it).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            // Disable dangerous features
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                // Safer check to prevent crashes
                                return request?.url?.host?.endsWith("192.168.0.174") != true
                            }
                        }
                        val bridge = NativeBridge(this)
                        addJavascriptInterface(bridge, "AndroidBridge")
                        loadUrl("http://192.168.0.174:3000")
                    }
                    SwipeRefreshLayout(it).apply {
                        setOnRefreshListener { 
                            webView.reload()
                            isRefreshing = false
                        }
                        addView(webView)
                    }
                }
            )
        }
    }
}
