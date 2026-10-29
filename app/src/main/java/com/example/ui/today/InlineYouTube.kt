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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** The video id in a YouTube link (watch?v=, youtu.be/, shorts/, embed/, live/), or null. */
fun youTubeId(link: String): String? =
    Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([\w-]{11})""")
        .find(link)?.groupValues?.get(1)

/**
 * YouTube's own embedded player, inside a Today card (2026-10-28: Devin wanted videos to play in their cards, not
 * in a separate player or the YouTube app). The page has an https origin named after the app because YouTube refuses
 * embeds with no referrer (error 152/153). [onCantPlay] runs when the channel doesn't allow embedding (101/150) or
 * the player fails: the card then opens the video in YouTube instead. Fullscreen takes over the window in landscape.
 *
 * Never put this inside a Compose clip to a rounded shape: in build 99 the card's clip played the sound over a black
 * picture (a WebView's video can't be composited through a non-rectangular clip). The page rounds its own corners
 * ([cornerDp]) over a transparent WebView instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InlineYouTube(videoId: String, modifier: Modifier = Modifier, cornerDp: Int = 0, onCantPlay: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cantPlay by rememberUpdatedState(onCantPlay)
    val fullscreen = remember { Fullscreen(context.findActivity()) }

    val webView = remember(videoId) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)       // the page's rounded corners show the card behind
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
                    if (!request.isForMainFrame) return false
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                    }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) = fullscreen.show(view, callback)
                override fun onHideCustomView() = fullscreen.hide()
            }
            loadDataWithBaseURL(ORIGIN, playerPage(videoId, cornerDp), "text/html", "utf-8", null)
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

private fun playerPage(id: String, corner: Int) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}
#w{position:absolute;inset:0;border-radius:${corner}px;overflow:hidden;background:#000;isolation:isolate}
#p{position:absolute;inset:0;width:100%;height:100%;border:0}</style>
</head><body><div id="w"><div id="p"></div></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady(){
  new YT.Player('p',{videoId:'$id',host:'https://www.youtube.com',
    playerVars:{autoplay:1,playsinline:1,rel:0,fs:1,origin:'$ORIGIN'},
    events:{onReady:function(e){e.target.playVideo();},onError:function(e){DeskAI.error(e.data);}}});
}
</script></body></html>
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
