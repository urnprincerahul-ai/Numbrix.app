package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.fcm.SMSFirebaseMessagingService
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

  private var fcmToken by mutableStateOf("Fetching token...")
  private var webViewRef: WebView? = null

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    if (isGranted) {
      Log.d(TAG, "Notification permission granted")
      notifyWebPermissionState(true)
    } else {
      Log.w(TAG, "Notification permission denied")
      notifyWebPermissionState(false)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    createNotificationChannel()
    fetchFcmToken()
    checkNotificationPermission()

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
          color = MaterialTheme.colorScheme.background
        ) {
          VirtualNumberWebView(
            fcmToken = fcmToken,
            onRequestPermission = { requestNotificationPermission() },
            onTriggerLocalNotification = { title, body, otpCode ->
              SMSFirebaseMessagingService.showNotification(this, title, body, otpCode)
            },
            onWebViewCreated = { webView -> webViewRef = webView }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val otpCode = intent.getStringExtra("EXTRA_OTP_CODE")
    if (!otpCode.isNullOrEmpty()) {
      webViewRef?.evaluateJavascript("if (typeof onFcmNotificationTapped === 'function') onFcmNotificationTapped('$otpCode');", null)
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        SMSFirebaseMessagingService.CHANNEL_ID,
        SMSFirebaseMessagingService.CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Notifications for incoming SMS messages and OTP codes"
        enableLights(true)
        enableVibration(true)
      }
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun fetchFcmToken() {
    try {
      if (FirebaseApp.getApps(this).isEmpty()) {
        try {
          FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to initialize FirebaseApp", e)
        }
      }

      if (FirebaseApp.getApps(this).isNotEmpty()) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
          if (!task.isSuccessful) {
            Log.w(TAG, "Fetching FCM registration token failed", task.exception)
            fcmToken = "fcm_token_ready_${System.currentTimeMillis()}"
            return@addOnCompleteListener
          }
          val token = task.result
          Log.d(TAG, "FCM Token: $token")
          fcmToken = token ?: "fcm_token_ready_${System.currentTimeMillis()}"
          webViewRef?.evaluateJavascript("if (typeof onFcmTokenUpdated === 'function') onFcmTokenUpdated('$fcmToken');", null)
        }
      } else {
        Log.w(TAG, "FirebaseApp is not initialized. Using fallback token.")
        fcmToken = "fcm_token_ready_${System.currentTimeMillis()}"
      }
    } catch (e: Exception) {
      Log.e(TAG, "FCM initialization error", e)
      fcmToken = "fcm_token_ready_${System.currentTimeMillis()}"
    }
  }

  private fun checkNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permission = Manifest.permission.POST_NOTIFICATIONS
      if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(permission)
      }
    }
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      Toast.makeText(this, "Notification permission is active", Toast.LENGTH_SHORT).show()
      notifyWebPermissionState(true)
    }
  }

  private fun notifyWebPermissionState(granted: Boolean) {
    webViewRef?.evaluateJavascript("if (typeof onNotificationPermissionChanged === 'function') onNotificationPermissionChanged($granted);", null)
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}

@Composable
fun VirtualNumberWebView(
  fcmToken: String,
  onRequestPermission: () -> Unit,
  onTriggerLocalNotification: (String, String, String?) -> Unit,
  onWebViewCreated: (WebView) -> Unit
) {
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { context ->
      val webContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext("default")
      } else {
        context
      }
      WebView(webContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        addJavascriptInterface(
          object {
            @JavascriptInterface
            fun getFcmToken(): String = fcmToken

            @JavascriptInterface
            fun requestPermission() {
              onRequestPermission()
            }

            @JavascriptInterface
            fun triggerLocalNotification(title: String, body: String, otpCode: String?) {
              onTriggerLocalNotification(title, body, otpCode)
            }

            @JavascriptInterface
            fun getBatteryStatus(): String {
              return try {
                val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                "{\"level\": $pct, \"isCharging\": $isCharging}"
              } catch (e: Exception) {
                "{\"level\": 100, \"isCharging\": false}"
              }
            }
          },
          "AndroidFCM"
        )

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val activity = context as? ComponentActivity
            val otpCode = activity?.intent?.getStringExtra("EXTRA_OTP_CODE")
            if (!otpCode.isNullOrEmpty()) {
              view?.evaluateJavascript("if (typeof onFcmNotificationTapped === 'function') onFcmNotificationTapped('$otpCode');", null)
              activity.intent.removeExtra("EXTRA_OTP_CODE")
            }
          }

          override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url != null && (url.startsWith("upi://") || url.startsWith("https://t.me/"))) {
              try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
              } catch (e: Exception) {
                Toast.makeText(context, "No app available to handle this link", Toast.LENGTH_SHORT).show()
              }
              return true
            }
            return false
          }
        }
        loadUrl("file:///android_asset/index.html")
        onWebViewCreated(this)
      }
    }
  )
}
