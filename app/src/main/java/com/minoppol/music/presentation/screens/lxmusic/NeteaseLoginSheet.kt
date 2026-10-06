package com.minoppol.music.presentation.screens.lxmusic

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.minoppol.music.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeteaseLoginDialog(
    onDismiss: () -> Unit,
    onValidate: (cookie: String, callback: (ok: Boolean, error: String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginSuccessMsg = stringResource(R.string.lx_msg_login_success)
    var checking by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var firstReadyAt by remember { mutableStateOf(0L) }
    var lastAttemptAt by remember { mutableStateOf(0L) }
    var retryDelayMs by remember { mutableStateOf(0L) }

    fun dispatchCookie(cookie: String) {
        checking = true
        lastAttemptAt = System.currentTimeMillis()
        onValidate(cookie) { ok, _ ->
            checking = false
            if (ok) {
                Toast.makeText(context, loginSuccessMsg, Toast.LENGTH_SHORT).show()
            } else {
                retryDelayMs = when (retryDelayMs) {
                    0L -> 2000L
                    2000L -> 3000L
                    else -> 5000L
                }
            }
        }
    }

    fun tryCapture(currentUrl: String?, now: Long) {
        if (checking) return
        val url = currentUrl ?: run {
            firstReadyAt = 0L
            return
        }
        val ready = url.contains("music.163.com") && !url.contains("/login")
        if (!ready) {
            firstReadyAt = 0L
            return
        }

        val manager = CookieManager.getInstance()
        manager.flush()
        val cmCookie = manager.getCookie("https://music.163.com")
        val hasMarker = cmCookie?.contains(LOGIN_MARKER) == true
        if (!hasMarker) {
            firstReadyAt = 0L
            return
        }
        if (firstReadyAt == 0L) firstReadyAt = now
        if (now - firstReadyAt < STABLE_DELAY_MS) return
        if (lastAttemptAt != 0L && now - lastAttemptAt < retryDelayMs) return

        dispatchCookie(cmCookie)
    }

    DisposableEffect(Unit) {
        val job = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                webViewRef?.let { wv -> tryCapture(wv.url, System.currentTimeMillis()) }
            }
        }
        onDispose { job.cancel() }
    }

    Dialog(
        onDismissRequest = { if (!checking) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack() && !checking) wv.goBack() else onDismiss()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.lx_desc_back))
                }
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.lx_title_netease_login),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    tryCapture(url, System.currentTimeMillis())
                                }
                            }
                            webViewRef = this
                            loadUrl(LOGIN_URL)
                        }
                    },
                )
                if (checking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.lx_msg_verifying_login), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private const val LOGIN_URL = "https://music.163.com/m/login"

private const val LOGIN_MARKER = "S_INFO="

private const val POLL_INTERVAL_MS = 700L

private const val STABLE_DELAY_MS = 2000L

fun clearNeteaseWebViewSession(onDone: (() -> Unit)? = null) {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies {
        manager.flush()
        WebStorage.getInstance().deleteAllData()
        onDone?.invoke()
    }
}
