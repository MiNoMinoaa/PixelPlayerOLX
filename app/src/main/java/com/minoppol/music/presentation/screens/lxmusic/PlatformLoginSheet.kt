package com.minoppol.music.presentation.screens.lxmusic

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.minoppol.music.data.lxmusic.LxTxQrPoll
import com.minoppol.music.data.lxmusic.LxTxQrType

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PlatformLoginDialog(
    loginUrl: String,
    hostMatch: String,
    loginMarkers: List<String>,
    loginPagePath: String,
    @StringRes titleRes: Int,
    onDismiss: () -> Unit,
    onValidate: (cookie: String, callback: (ok: Boolean, error: String?) -> Unit) -> Unit,
    userAgentOverride: String? = null,
    detectByMarkers: Boolean = false,
    autoClickSelector: String? = null,
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
        val leftLoginPage = detectByMarkers || !url.contains(loginPagePath)
        if (!url.contains(hostMatch) || !leftLoginPage) {
            firstReadyAt = 0L
            return
        }

        val manager = CookieManager.getInstance()
        manager.flush()
        val cookieUrls = listOf(url, loginUrl, "https://www.$hostMatch", "https://m.$hostMatch").distinct()
        val cmCookie = cookieUrls.mapNotNull { manager.getCookie(it) }
            .filter { it.isNotBlank() }
            .joinToString("; ")
        if (cmCookie.isBlank()) {
            firstReadyAt = 0L
            return
        }
        if (detectByMarkers && loginMarkers.any { it !in cmCookie }) {
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
                    text = stringResource(titleRes),
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
                            settings.userAgentString = userAgentOverride
                                ?: ("Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/124.0.0 Mobile Safari/537.36")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    tryCapture(url, System.currentTimeMillis())
                                    val sel = autoClickSelector
                                    if (sel != null && view != null) {
                                        view.postDelayed({
                                            view.evaluateJavascript(
                                                "try{document.querySelector('$sel')?.click()}catch(e){}",
                                                null,
                                            )
                                        }, AUTO_CLICK_DELAY_MS)
                                    }
                                }
                            }
                            webViewRef = this
                            loadUrl(loginUrl)
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

@Composable
fun TencentQrLoginDialog(
    onDismiss: () -> Unit,
    onCreateQr: (LxTxQrType, (ByteArray?, String?) -> Unit) -> Unit,
    onPoll: (LxTxQrType, String, (LxTxQrPoll) -> Unit) -> Unit,
    onLogin: (String, (ok: Boolean, error: String?) -> Unit) -> Unit,
) {
    var qrType by remember { mutableStateOf(LxTxQrType.QQ) }
    var qrBytes by remember { mutableStateOf<ByteArray?>(null) }
    var qrSession by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(LxTxQrPoll.Status.PENDING) }
    var creating by remember { mutableStateOf(false) }
    var loggingIn by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }

    fun requestQr(type: LxTxQrType) {
        creating = true
        qrBytes = null
        qrSession = null
        status = LxTxQrPoll.Status.PENDING
        generation++
        val gen = generation
        onCreateQr(type) { bytes, session ->
            if (gen != generation) return@onCreateQr
            creating = false
            if (bytes == null || session == null) {
                status = LxTxQrPoll.Status.ERROR
            } else {
                qrBytes = bytes
                qrSession = session
            }
        }
    }

    LaunchedEffect(qrType) { requestQr(qrType) }

    LaunchedEffect(qrSession) {
        val session = qrSession ?: return@LaunchedEffect
        val gen = generation
        while (isActive && gen == generation) {
            delay(2000)
            if (loggingIn) continue
            onPoll(qrType, session) { result ->
                if (gen != generation) return@onPoll
                when (result.status) {
                    LxTxQrPoll.Status.SUCCESS -> {
                        val cookie = result.cookie
                        if (cookie.isNullOrBlank()) {
                            status = LxTxQrPoll.Status.ERROR
                        } else {
                            loggingIn = true
                            onLogin(cookie) { ok, _ ->
                                loggingIn = false
                                if (!ok) status = LxTxQrPoll.Status.ERROR
                            }
                        }
                    }
                    LxTxQrPoll.Status.EXPIRED -> status = LxTxQrPoll.Status.EXPIRED
                    LxTxQrPoll.Status.ERROR -> status = LxTxQrPoll.Status.ERROR
                    else -> status = result.status
                }
            }
        }
    }

    val qrBitmap = remember(qrBytes) {
        qrBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    Dialog(
        onDismissRequest = { if (!loggingIn) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
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
                IconButton(onClick = { if (!loggingIn) onDismiss() }) {
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
                    text = stringResource(R.string.lx_title_qq_login),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (loggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 16.dp).size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                LxTxQrType.entries.forEach { type ->
                    val selected = qrType == type
                    TextButton(onClick = { if (!loggingIn && !creating) { qrType = type } }) {
                        Text(
                            text = stringResource(
                                if (type == LxTxQrType.QQ) R.string.lx_qr_tab_qq else R.string.lx_source_wechat
                            ),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        creating -> CircularProgressIndicator()
                        qrBitmap != null -> Image(
                            bitmap = qrBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text(
                            text = stringResource(R.string.lx_msg_login_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        when (status) {
                            LxTxQrPoll.Status.SCANNED -> R.string.lx_qr_status_scanned
                            LxTxQrPoll.Status.EXPIRED -> R.string.lx_qr_status_expired
                            LxTxQrPoll.Status.ERROR -> R.string.lx_msg_login_failed
                            else -> if (qrType == LxTxQrType.QQ) R.string.lx_qr_scan_hint_qq
                                else R.string.lx_qr_scan_hint_wechat
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (status) {
                        LxTxQrPoll.Status.EXPIRED, LxTxQrPoll.Status.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (status == LxTxQrPoll.Status.EXPIRED || status == LxTxQrPoll.Status.ERROR) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { if (!creating) requestQr(qrType) }) {
                        Text(stringResource(R.string.lx_qr_refresh))
                    }
                }
            }
        }
    }
}

@Composable
fun KugouSmsLoginDialog(
    onDismiss: () -> Unit,
    onSendCode: (mobile: String, callback: (ok: Boolean, error: String?) -> Unit) -> Unit,
    onLogin: (mobile: String, code: String, callback: (ok: Boolean, error: String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val loginSuccessMsg = stringResource(R.string.lx_msg_login_success)
    var mobile by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000L); countdown -= 1 }
    }

    val busy = sending || logging

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
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
                IconButton(onClick = { if (!busy) onDismiss() }) {
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
                    text = stringResource(R.string.lx_title_kugou_login),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.lx_msg_sms_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }
                        if (digits.length <= 11) mobile = digits
                        error = null
                    },
                    label = { Text(stringResource(R.string.lx_field_mobile)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v ->
                            val digits = v.filter { it.isDigit() }
                            if (digits.length <= 6) code = digits
                            error = null
                        },
                        label = { Text(stringResource(R.string.lx_field_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            error = null
                            sending = true
                            onSendCode(mobile) { ok, e ->
                                sending = false
                                if (ok) countdown = 60 else error = e
                            }
                        },
                        enabled = !busy && countdown == 0 && mobile.length == 11,
                    ) {
                        Text(if (countdown > 0) "${countdown}s" else stringResource(R.string.lx_action_get_code))
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        error = null
                        logging = true
                        onLogin(mobile, code) { ok, e ->
                            if (ok) {
                                Toast.makeText(context, loginSuccessMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                logging = false
                                error = e
                            }
                        }
                    },
                    enabled = !busy && mobile.length == 11 && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (logging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.lx_action_login))
                    }
                }
            }
        }
    }
}

@Composable
fun KuwoLoginDialog(
    onDismiss: () -> Unit,
    onValidate: (cookie: String, callback: (ok: Boolean, error: String?) -> Unit) -> Unit,
) = PlatformLoginDialog(
    loginUrl = "https://www.kuwo.cn/",
    hostMatch = "kuwo.cn",
    loginMarkers = listOf("userid=", "sid="),
    loginPagePath = "",
    titleRes = R.string.lx_title_kuwo_login,
    onDismiss = onDismiss,
    onValidate = onValidate,
    userAgentOverride = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    detectByMarkers = true,
    autoClickSelector = ".reg_text",
)

private const val POLL_INTERVAL_MS = 700L
private const val STABLE_DELAY_MS = 2000L
private const val AUTO_CLICK_DELAY_MS = 1200L

fun clearAllWebViewSession(onDone: (() -> Unit)? = null) {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies {
        manager.flush()
        WebStorage.getInstance().deleteAllData()
        onDone?.invoke()
    }
}
