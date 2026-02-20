package pl.lokaltu.android

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class NativeBridge(
    private val onAction: (action: String, payload: JSONObject?) -> Unit,
    private val sender: (json: String) -> Unit
) {

    /**
     * Entry point for messages from the web side (WebView @JavascriptInterface)
     */
    @JavascriptInterface
    fun postMessage(json: String) {
        handleIncomingMessage(json)
    }

    /**
     * Shared logic for handling messages from either WebView or TWA
     */
    fun handleIncomingMessage(json: String) {
        try {
            val message = JSONObject(json)
            val action = when {
                message.has("type") -> message.getString("type")
                message.has("action") -> message.getString("action")
                else -> ""
            }

            Log.d("NativeBridge", "Received Action: $action")
            onAction(action, message)

        } catch (e: Exception) {
            Log.e("NativeBridge", "Error parsing message: $json", e)
        }
    }

    fun sendNfcResult(tagId: String, content: String? = null) {
        sendToWeb("NFC_TAG_DETECTED", JSONObject().apply {
            put("id", tagId)
            put("content", content ?: tagId)
        })
    }

    fun sendNfcError(error: String) {
        sendToWeb("NFC_ERROR", JSONObject().apply {
            put("message", error)
        })
    }

    fun sendNfcReady() {
        sendToWeb("NFC_READY")
    }

    fun sendCameraResult(base64Image: String) {
        sendToWeb("CAMERA_RESULT", JSONObject().apply {
            put("image", base64Image)
        })
    }

    fun sendCameraError(error: String) {
        sendToWeb("CAMERA_ERROR", JSONObject().apply {
            put("message", error)
        })
    }

    fun sendBridgeReady() {
        sendToWeb("BRIDGE_READY", JSONObject().apply {
            put("status", "connected")
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendToWeb(type: String, payload: JSONObject = JSONObject()) {
        val message = JSONObject()
        message.put("type", type)
        message.put("payload", payload)

        val json = message.toString()
        Log.d("NativeBridge", "Sending to Web: $json")
        sender(json)
    }
}