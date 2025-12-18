package pl.lokaltu.android

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class NativeBridge(private val webView: WebView) {

    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            val message = JSONObject(json)
            val type = message.getString("type")

            Log.d("NativeBridge", "Received: $type")

            when (type) {
                "APP_READY" -> handleAppReady()
                "REQUEST_NFC" -> handleNFCRequest()
                "REGISTER_PUSH_TOKEN" -> registerPushToken()
                "PERMISSION_REQUEST" -> {
                    val permission = message.getString("permission")
                    handlePermission(permission)
                }
                else -> Log.w("NativeBridge", "Unknown message type: $type")
            }
        } catch (e: Exception) {
            Log.e("NativeBridge", "Error parsing message", e)
        }
    }

    private fun handleAppReady() {
        Log.d("NativeBridge", "App ready, sending handshake response")
        // Send acknowledgement back to web
        sendToWeb("APP_READY_ACK")
    }

    private fun handleNFCRequest() {
        // Your NFC handling logic
        // When tag is detected:
        sendToWeb("NFC_RESULT", """{"payload":"tag_data_here"}""")
    }

    private fun registerPushToken() {
        // Your FCM token logic
        val token = "fcm_token_here"
        sendToWeb("REGISTER_PUSH_TOKEN", """{"token":"$token"}""")
    }

    private fun handlePermission(permission: String) {
        // Your permission handling
        sendToWeb("PERMISSION_RESULT",
            """{"permission":"$permission","granted":true}""")
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
                const message = JSON.parse('${json.replace("'", "'''")}');
                if (message.type === 'APP_READY_ACK') {
                    window.__nativeReady?.(true);
                }
                window.__nativeDispatch?.(message);
            })();
        """.trimIndent()

        webView.post {
            webView.evaluateJavascript(script) { result ->
                Log.d("NativeBridge", "Executed: $result")
            }
        }
    }
}
