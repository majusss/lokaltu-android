package pl.lokaltu.android

import android.content.Intent
import android.util.Log
import android.webkit.WebView

class AuthHandler(
    private val appUrl: String,
    private val webViewProvider: () -> WebView?
) {
    var pendingReloadUrl: String? = null
        private set

    fun handleAuthRedirect(intent: Intent): Boolean {
        val data = intent.data ?: return false
        val isAuthRedirect = (data.scheme == "lokaltu" && data.host == "auth") ||
                             (data.host?.contains(BuildConfig.APP_DOMAIN) == true)
        
        if (isAuthRedirect) {
            val token = data.getQueryParameter("__clerk_handover_token")
            val targetUrl = if (token != null) {
                val baseUrl = appUrl.trimEnd('/')
                "$baseUrl/auth/sync?__clerk_handover_token=$token"
            } else {
                appUrl
            }

            Log.d("AuthHandler", "Auth redirect detected! Target: $targetUrl")
            
            val webView = webViewProvider()
            if (webView == null) {
                Log.d("AuthHandler", "WebView is null, marking pending reload.")
                pendingReloadUrl = targetUrl
            } else {
                webView.post {
                    Log.d("AuthHandler", "Executing loadUrl($targetUrl)")
                    webView.loadUrl(targetUrl)
                }
            }
            return true
        }
        return false
    }

    fun clearPendingReload() {
        pendingReloadUrl = null
    }
}
