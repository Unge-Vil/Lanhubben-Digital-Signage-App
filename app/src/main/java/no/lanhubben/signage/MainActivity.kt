package no.lanhubben.signage

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var container: FrameLayout
    private var menuDialog: AlertDialog? = null
    private val prefs by lazy { getSharedPreferences("signage", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val retryDelayMs = 10_000L
    private val reload = Runnable { webView.loadUrl(BuildConfig.PLAYER_URL) }

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
        container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView)
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
    // OK/Select åpner meny, Menytasten åpner rotasjonsvalg, Play/Pause laster siden på nytt
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }

            override fun getVideoLoadingProgressView(): View = View(this@MainActivity)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                handler.removeCallbacks(reload)
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) scheduleReload()
            }

            override fun onReceivedSslError(view: WebView?, h: android.webkit.SslErrorHandler?, e: SslError?) {
                h?.cancel()
                scheduleReload()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                recreate()
                return true
            }
        }

        if (savedInstanceState == null) webView.loadUrl(BuildConfig.PLAYER_URL)
        else webView.restoreState(savedInstanceState)

        if (!prefs.contains(KEY_ROTATION)) showRotationDialog()
        else if (isSettingsLaunch(intent)) showMenu()

        // Tilbake-knappen åpner menyen i stedet for å forlate appen ved et uhell
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showMenu()
        })

        // Langt trykk på berøringsskjerm/mus åpner menyen
        webView.setOnLongClickListener { showMenu(); true }
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
        webView.layoutParams = FrameLayout.LayoutParams(
            if (sideways) h else w,
            if (sideways) w else h,
            Gravity.CENTER
        )
        webView.rotation = rotation.toFloat()
    }

    private fun showMenu() {
        if (menuDialog?.isShowing == true) return
        val items = arrayOf("Last siden på nytt", "Endre skjermrotasjon", "Avslutt appen", "Lukk meny")
        menuDialog = AlertDialog.Builder(this)
            .setTitle("Lanhubben Signage")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> webView.loadUrl(BuildConfig.PLAYER_URL)
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
            .setNeutralButton("Last siden på nytt") { _, _ -> webView.loadUrl(BuildConfig.PLAYER_URL) }
            .setOnCancelListener {
                if (!prefs.contains(KEY_ROTATION)) prefs.edit().putInt(KEY_ROTATION, 0).apply()
            }
            .show()
    }

    private fun scheduleReload() {
        handler.removeCallbacks(reload)
        handler.postDelayed(reload, retryDelayMs)
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
                webView.loadUrl(BuildConfig.PLAYER_URL)
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
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        menuDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val KEY_ROTATION = "rotation"
    }
}
