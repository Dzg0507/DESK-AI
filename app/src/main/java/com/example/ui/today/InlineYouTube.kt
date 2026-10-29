package com.example.ui.today

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnAttach
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** The video id in a YouTube link (watch?v=, youtu.be/, shorts/, embed/, live/), or null. */
fun youTubeId(link: String): String? =
    Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([\w-]{11})""")
        .find(link)?.groupValues?.get(1)

/**
 * YouTube's own embed page (youtube.com/embed/ID) as the WebView's page, inside a Today card (2026-10-28: Devin
 * wanted videos to play in their cards, not in a separate player or the YouTube app). The request carries a Referer
 * named after the app because YouTube refuses embeds with none (error 152/153). [onCantPlay] runs when the player
 * shows an error (e.g. the channel doesn't allow embedding): the card then opens the video in YouTube instead.
 * Fullscreen takes over the window in landscape.
 *
 * In builds 99-100 only the sound played: the WebView had AndroidView's default WRAP_CONTENT height (see below).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineYouTube(videoId: String, modifier: Modifier = Modifier, onCantPlay: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cantPlay by rememberUpdatedState(onCantPlay)
    val fullscreen = remember { Fullscreen(context.findActivity()) }

    val webView = remember(videoId) {
        WebView(context).apply {
            // MATCH_PARENT, not AndroidView's default WRAP_CONTENT: with a wrap_content height a WebView tells the page
            // its viewport is 0 px tall, so YouTube sized the player 0 px high and only the sound came through
            // (builds 99-100; found over DevTools on Devin's S22)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false      // the tap on the card was the gesture
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(object {
                @JavascriptInterface
                fun error(code: Int) {
                    Handler(Looper.getMainLooper()).post { cantPlay() }
                }
            }, "DeskAI")
            webViewClient = object : WebViewClient() {
                // Taps on the title or the YouTube logo leave the player: open those in YouTube, not in the card
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame || request.url.path.orEmpty().startsWith("/embed/")) return false
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                    }
                    return true
                }

                // The embed page shows its errors itself (no API to ask): watch for one and let the card open YouTube
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.contains("/embed/")) view.evaluateJavascript(WATCH_ERRORS, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) = fullscreen.show(view, callback)
                override fun onHideCustomView() = fullscreen.hide()
            }
            // Loaded once on screen: a page started while the view is detached (0x0, no hardware drawing yet) played
            // its sound with nothing drawn in build 100
            doOnAttach { loadUrl(embedUrl(videoId), mapOf("Referer" to "$ORIGIN/")) }
        }
    }

    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            fullscreen.hide()
            webView.stopLoading()
            webView.loadUrl("about:blank")                       // stops the sound at once (scrolled away, closed)
            webView.destroy()
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

private const val ORIGIN = "https://com.aistudio.deskai.kzpwqm"

private fun embedUrl(id: String) =
    "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0&fs=1&origin=" + android.net.Uri.encode(ORIGIN)

private const val WATCH_ERRORS = """
(function(){var n=0,t=setInterval(function(){
  var e=document.querySelector('.ytp-error');
  if(e&&e.offsetParent!==null){clearInterval(t);DeskAI.error(0);}
  if(++n>40)clearInterval(t);
},500);})();
"""

/** The player's fullscreen view, over the whole window in landscape; back to the card when it ends. */
private class Fullscreen(private val activity: Activity?) {
    private var view: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun show(v: View, cb: WebChromeClient.CustomViewCallback) {
        val a = activity ?: return cb.onCustomViewHidden()
        if (view != null) return cb.onCustomViewHidden()
        view = v
        callback = cb
        orientation = a.requestedOrientation
        v.setBackgroundColor(AndroidColor.BLACK)
        (a.window.decorView as FrameLayout).addView(
            v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        WindowCompat.getInsetsController(a.window, a.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun hide() {
        val a = activity ?: return
        val v = view ?: return
        (a.window.decorView as FrameLayout).removeView(v)
        WindowCompat.getInsetsController(a.window, a.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        a.requestedOrientation = orientation
        view = null
        callback?.onCustomViewHidden()
        callback = null
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
