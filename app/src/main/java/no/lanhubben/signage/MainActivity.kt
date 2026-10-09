package no.lanhubben.signage

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var container: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorDetail: TextView
    private lateinit var errorCountdown: TextView
    @Volatile private var trustedPage = false
    private var pageHadError = false
    private var secondsLeft = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var menuDialog: AlertDialog? = null
    private val prefs by lazy { getSharedPreferences("signage", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val retryDelayMs = 10_000L
    private val tick = object : Runnable {
        override fun run() {
            if (errorOverlay.visibility != View.VISIBLE) return
            if (secondsLeft <= 0) {
                loadPlayer()
                return
            }
            errorCountdown.text = "Prøver igjen om $secondsLeft sek"
            secondsLeft--
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = false
            isFocusableInTouchMode = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        buildErrorOverlay()
        stage = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(-1, -1))
            addView(errorOverlay, FrameLayout.LayoutParams(-1, -1))
        }
        container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(stage)
            addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
                if (r - l != orr - ol || b - t != ob - ot) applyRotation()
            }
        }
        setContentView(container)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }

            override fun getVideoLoadingProgressView(): View = View(this@MainActivity)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageHadError = false
                trustedPage = isTrustedUrl(url)
            }

            // Hovedsiden kan bare navigere innen lanhubben.no, slik at kontrollgrensesnittet ikke eksponeres andre steder
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request?.isForMainFrame == true && !isTrustedUrl(request.url?.toString())

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                if (!pageHadError) hideError()
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                val online = isOnline()
                showError(
                    if (online) "Får ikke kontakt med Lanhubben" else "Ingen nettverkstilkobling",
                    if (online) "Serveren svarer ikke. Sjekk at lanhubben.no er oppe."
                    else "Sjekk at enheten er koblet til Wi-Fi eller nettverk.",
                    "Feilkode ${error?.errorCode}: ${error?.description}"
                )
            }

            override fun onReceivedHttpError(
                view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
            ) {
                val status = errorResponse?.statusCode ?: 0
                if (request?.isForMainFrame == true && status >= 400) {
                    showError(
                        "Lanhubben svarer med en feil",
                        "Tjenesten er midlertidig utilgjengelig.",
                        "HTTP $status"
                    )
                }
            }

            override fun onReceivedSslError(view: WebView?, h: android.webkit.SslErrorHandler?, e: SslError?) {
                h?.cancel()
                showError(
                    "Sikker tilkobling feilet",
                    "Sjekk at dato og klokkeslett er riktig på enheten.",
                    "SSL-feil"
                )
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                recreate()
                return true
            }
        }

        if (savedInstanceState == null) loadPlayer()
        else webView.restoreState(savedInstanceState)

        if (!prefs.contains(KEY_ROTATION)) showRotationDialog()
        else if (isSettingsLaunch(intent)) showMenu()

        // Tilbake-knappen åpner menyen i stedet for å forlate appen ved et uhell
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showMenu()
        })

        // Langt trykk på berøringsskjerm/mus åpner menyen
        webView.setOnLongClickListener { showMenu(); true }

        webView.addJavascriptInterface(AppBridge(), "LanhubbenApp")
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isSettingsLaunch(intent)) showMenu()
    }

    private fun isSettingsLaunch(intent: Intent?) =
        intent?.component?.className?.endsWith("SettingsAlias") == true

    private fun applyRotation() {
        val rotation = prefs.getInt(KEY_ROTATION, 0)
        val w = container.width
        val h = container.height
        if (w == 0 || h == 0) return
        val sideways = rotation == 90 || rotation == 270
        stage.layoutParams = FrameLayout.LayoutParams(
            if (sideways) h else w,
            if (sideways) w else h,
            Gravity.CENTER
        )
        stage.rotation = rotation.toFloat()
    }

    private fun showMenu() {
        if (menuDialog?.isShowing == true) return
        val items = arrayOf("Last siden på nytt", "Endre skjermrotasjon", "Avslutt appen", "Lukk meny")
        menuDialog = AlertDialog.Builder(this)
            .setTitle("Lanhubben Signage")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> loadPlayer()
                    1 -> showRotationDialog()
                    2 -> finishAndRemoveTask()
                }
            }
            .show()
    }

    private fun showRotationDialog() {
        val labels = arrayOf(
            "Liggende (standard)",
            "Stående – roter 90° med klokken",
            "Opp ned – roter 180°",
            "Stående – roter 270° (90° mot klokken)"
        )
        val values = intArrayOf(0, 90, 180, 270)
        val current = values.indexOf(prefs.getInt(KEY_ROTATION, 0)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Skjermrotasjon")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                prefs.edit().putInt(KEY_ROTATION, values[which]).apply()
                applyRotation()
                dialog.dismiss()
            }
            .setNeutralButton("Last siden på nytt") { _, _ -> loadPlayer() }
            .setOnCancelListener {
                if (!prefs.contains(KEY_ROTATION)) prefs.edit().putInt(KEY_ROTATION, 0).apply()
            }
            .show()
    }

    private fun loadPlayer() {
        handler.removeCallbacks(tick)
        webView.loadUrl(BuildConfig.PLAYER_URL)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showError(title: String, detail: String, technical: String) {
        pageHadError = true
        errorTitle.text = title
        errorDetail.text = "$detail\n$technical"
        errorOverlay.visibility = View.VISIBLE
        secondsLeft = (retryDelayMs / 1000).toInt()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun hideError() {
        handler.removeCallbacks(tick)
        errorOverlay.visibility = View.GONE
    }

    private fun buildErrorOverlay() {
        fun label(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 12, 0, 12)
        }
        errorTitle = label(34f, Color.parseColor("#5CFFF0"), true)
        errorDetail = label(20f, Color.parseColor("#B0B6C0"))
        errorCountdown = label(18f, Color.parseColor("#7A8190"))
        errorOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0C10"))
            setPadding(96, 48, 96, 48)
            visibility = View.GONE
            addView(label(22f, Color.parseColor("#7A8190")).apply { text = "LAN-HUBBEN" })
            addView(errorTitle)
            addView(errorDetail)
            addView(errorCountdown)
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { if (errorOverlay.visibility == View.VISIBLE) loadPlayer() }
            }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb
        )
        networkCallback = cb
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // OK/Select åpner meny, Menytasten åpner rotasjonsvalg, Play/Pause laster siden på nytt
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showMenu()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                loadPlayer()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (networkCallback == null) registerNetworkCallback()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        menuDialog?.dismiss()
        networkCallback?.let {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
        }
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    private fun isTrustedUrl(url: String?): Boolean {
        val uri = Uri.parse(url ?: return false)
        val host = uri.host ?: return false
        return uri.scheme == "https" && (host == "lanhubben.no" || host.endsWith(".lanhubben.no"))
    }

    // Kontrollgrensesnitt for Lanhubben-siden: window.LanhubbenApp.*
    inner class AppBridge {
        @JavascriptInterface
        fun getInfo(): String {
            if (!trustedPage) return "{}"
            val rotation = prefs.getInt(KEY_ROTATION, 0)
            return JSONObject()
                .put("app", "lanhubben-signage")
                .put("version", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("rotation", rotation)
                .put("online", isOnline())
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android", Build.VERSION.SDK_INT)
                .toString()
        }

        @JavascriptInterface
        fun reload() { if (trustedPage) runOnUiThread { loadPlayer() } }

        // Gyldige verdier: 0, 90, 180, 270. Returnerer true hvis verdien ble godtatt.
        @JavascriptInterface
        fun setRotation(degrees: Int): Boolean {
            if (!trustedPage || degrees !in intArrayOf(0, 90, 180, 270)) return false
            prefs.edit().putInt(KEY_ROTATION, degrees).apply()
            runOnUiThread { applyRotation() }
            return true
        }

        // Starter aktiviteten på nytt (nullstiller WebView)
        @JavascriptInterface
        fun restartApp() { if (trustedPage) runOnUiThread { recreate() } }
    }

    companion object {
        private const val KEY_ROTATION = "rotation"
    }
}
