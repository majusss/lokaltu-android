package pl.lokaltu.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private var isAppLaunched = false
    private var webView: WebView? = null

    private lateinit var bridge: NativeBridge
    private lateinit var nfcManager: NfcManager
    private lateinit var cameraManager: CameraManager
    private lateinit var authHandler: AuthHandler

    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        geolocationCallback?.invoke(geolocationOrigin, granted, false)
        geolocationCallback = null
        geolocationOrigin = null
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> cameraManager.handleCameraResult(bitmap) }

    private val appUrl = "https://${BuildConfig.APP_DOMAIN}/"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }

        // Initialize NativeBridge
        bridge = NativeBridge(
            onAction = { action, json -> handleBridgeAction(action, json) },
            sender = { json ->
                webView?.post {
                    webView?.evaluateJavascript(
                        "window.dispatchEvent(new MessageEvent('message', { data: $json }));",
                        null
                    )
                }
            }
        )

        // Initialize Managers
        nfcManager = NfcManager(this, bridge)
        cameraManager = CameraManager(bridge)
        authHandler = AuthHandler(appUrl) { webView }

        // Handle startup intent
        intent?.let { authHandler.handleAuthRedirect(it) }

        setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                AppWebView()

                var showSplash by remember { mutableStateOf(!isAppLaunched) }
                if (showSplash) {
                    SplashScreen()
                    LaunchedEffect(Unit) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            showSplash = false
                            isAppLaunched = true
                        }, 2500)
                    }
                }
            }
        }
    }

    @Composable
    fun SplashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_black),
                contentDescription = "Logo"
            )
        }
    }

    @Composable
    fun AppWebView() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    isFocusable = true
                    isFocusableInTouchMode = true

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setGeolocationEnabled(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    authHandler.pendingReloadUrl?.let { url ->
                        Log.d("WebView", "Executing pending reload for $url")
                        loadUrl(url)
                        authHandler.clearPendingReload()
                    }

                    val defaultUA = settings.userAgentString
                    val customUA = defaultUA
                        .replace("; wv", "")
                        .replace(Regex("Version/\\d+\\.\\d+ "), "")
                    settings.userAgentString = "$customUA Lokaltu-Native-Android"

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            if (url == null) return false

                            Log.d("WebView", "Loading URL: $url")

                            if (url.startsWith("lokaltu://")) {
                                authHandler.handleAuthRedirect(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(url)
                                    )
                                )
                                return true
                            }

                            if (url.startsWith("intent://")) {
                                try {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    if (intent != null) {
                                        if (authHandler.handleAuthRedirect(intent)) return true
                                        startActivity(intent)
                                        return true
                                    }
                                } catch (e: Exception) {
                                    Log.e("WebView", "Error parsing intent URL: $url", e)
                                }
                            }

                            if (url.contains("accounts.google.com") ||
                                url.contains("google.com/accounts") ||
                                (url.contains("clerk") && url.contains("oauth"))
                            ) {
                                val customTabsIntent = CustomTabsIntent.Builder().build()
                                customTabsIntent.launchUrl(context, Uri.parse(url))
                                return true
                            }

                            return !url.startsWith("http://") && !url.startsWith("https://")
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String,
                            callback: GeolocationPermissions.Callback
                        ) {
                            val fineLocationPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            val coarseLocationPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )

                            if (fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
                                coarseLocationPermission == PackageManager.PERMISSION_GRANTED
                            ) {
                                callback.invoke(origin, true, false)
                            } else {
                                geolocationCallback = callback
                                geolocationOrigin = origin
                                locationPermissionRequest.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = view
                            resultMsg?.sendToTarget()
                            return true
                        }
                    }

                    addJavascriptInterface(bridge, "AndroidBridge")
                    loadUrl(appUrl)
                }
            }
        )
    }

    private fun handleBridgeAction(action: String, json: JSONObject?) {
        when (action) {
            "APP_READY" -> {
                bridge.sendBridgeReady()
                bridge.sendNfcReady()
            }

            "START_NFC_SCAN" -> nfcManager.startScan()
            "STOP_NFC_SCAN" -> nfcManager.stopScan()
            "OPEN_CAMERA" -> {
                Log.d("Camera", "Web requested camera")
                runOnUiThread { takePictureLauncher.launch(null) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authHandler.handleAuthRedirect(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        webView = null
    }
}

