package pl.lokaltu.android

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var nativeBridge: NativeBridge
    private var nfcAdapter: NfcAdapter? = null
    private var pendingNfcRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // Setup WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // Setup NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Setup Bridge
        nativeBridge = NativeBridge(webView) { type ->
            when (type) {
                "REQUEST_NFC" -> {
                    pendingNfcRequest = true
                    // Bridge automatycznie czeka - user tylko zbliża tag
                }
            }
        }

        webView.addJavascriptInterface(nativeBridge, "AndroidBridge")
        webView.loadUrl("http://192.168.0.174:3000")
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Jeśli ktoś czeka na NFC i przyszedł tag
        if (pendingNfcRequest && NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let { handleNfcTag(it) }
        }
    }

    private fun enableNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE
            )
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun handleNfcTag(tag: Tag) {
        try {
            val tagId = tag.id.joinToString("") { "%02x".format(it) }

//            val ndef = Ndef.get(tag)
//            val ndefMessage = ndef?.cachedNdefMessage
//            val records = ndefMessage?.records

//            val data = if (records != null && records.isNotEmpty()) {
//                String(records[0].payload)
//            } else {
//                tagId
//            }
            val data = tagId;

            Log.d("NFC", "Tag read: $data")

            // Wyślij do webview
            nativeBridge.sendNfcResult(data)
            pendingNfcRequest = false

        } catch (e: Exception) {
            Log.e("NFC", "Error reading tag", e)
            nativeBridge.sendNfcError(e.message ?: "Unknown error")
            pendingNfcRequest = false
        }
    }
}