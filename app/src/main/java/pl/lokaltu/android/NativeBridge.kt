package pl.lokaltu.android

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class NativeBridge(
    private val webView: WebView,
    private val onMessage: (type: String) -> Unit
) {

    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            val message = JSONObject(json)
            val type = message.getString("type")

            Log.d("NativeBridge", "Received: $type")
            onMessage(type)

        } catch (e: Exception) {
            Log.e("NativeBridge", "Error parsing message", e)
        }
    }

    fun sendNfcResult(data: String) {
        sendToWeb("NFC_RESULT", """{"payload":"$data"}""")
    }

    fun sendNfcError(error: String) {
        sendToWeb("NFC_ERROR", """{"error":"$error"}""")
    }

    private fun sendToWeb(type: String, payload: String = "{}") {
        val message = JSONObject()
        message.put("type", type)

        if (payload != "{}") {
            val payloadJson = JSONObject(payload)
            payloadJson.keys().forEach { key ->
                message.put(key, payloadJson.get(key))
            }
        }

        val json = message.toString()
        val script = """
            (function() {
                try {
                    const message = $json;
                    window.__nativeDispatch?.(message);
                } catch(e) {
                    console.error('[Bridge]', e);
                }
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }
}