package pl.lokaltu.android

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var splashOverlay: ImageView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var nativeBridge: NativeBridge
    private var nfcAdapter: NfcAdapter? = null
    private var pendingNfcRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        // Wrap WebView in SwipeRefreshLayout
        swipeRefreshLayout = SwipeRefreshLayout(this).apply {
            addView(webView)
            setOnRefreshListener { webView.reload() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(swipeRefreshLayout)

        splashOverlay = ImageView(this).apply {
            setImageResource(R.drawable.logo_black)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.WHITE)

            val padding40dp = (40 * resources.displayMetrics.density).toInt()
            setPadding(padding40dp, 0, padding40dp, 0)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(splashOverlay)

        setContentView(container)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                swipeRefreshLayout.isRefreshing = false

                splashOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { splashOverlay.visibility = View.GONE }
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        nativeBridge = NativeBridge(webView) { type ->
            when (type) {
                "REQUEST_NFC" -> {
                    pendingNfcRequest = true
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
            val data = tagId

            Log.d("NFC", "Tag read: $data")

            nativeBridge.sendNfcResult(data)
            pendingNfcRequest = false

        } catch (e: Exception) {
            Log.e("NFC", "Error reading tag", e)
            nativeBridge.sendNfcError(e.message ?: "Unknown error")
            pendingNfcRequest = false
        }
    }
}
