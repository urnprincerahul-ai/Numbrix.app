package com.example.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SMSFirebaseMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d(TAG, "New FCM Registration Token: $token")
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)
    Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

    var title: String? = null
    var body: String? = null
    var otpCode: String? = null

    if (remoteMessage.data.isNotEmpty()) {
      Log.d(TAG, "Message data payload: ${remoteMessage.data}")
      title = remoteMessage.data["title"] ?: remoteMessage.data["platform"]?.let { "📩 New OTP for $it" }
      body = remoteMessage.data["body"] ?: remoteMessage.data["message"]
      otpCode = remoteMessage.data["otpCode"] ?: remoteMessage.data["otp"]
    }

    remoteMessage.notification?.let {
      if (title.isNullOrEmpty()) title = it.title
      if (body.isNullOrEmpty()) body = it.body
    }

    if (title.isNullOrEmpty()) {
      title = "📩 New SMS / OTP Received"
    }

    if (body.isNullOrEmpty()) {
      body = if (!otpCode.isNullOrEmpty()) "Your verification code is: $otpCode" else "A new SMS has arrived for your virtual line."
    }

    showNotification(applicationContext, title!!, body!!, otpCode)
  }

  companion object {
    private const val TAG = "SMSFirebaseMessaging"
    const val CHANNEL_ID = "sms_notifications_channel"
    const val CHANNEL_NAME = "SMS & OTP Alerts"
    private var notificationId = 1001

    fun showNotification(context: Context, title: String, body: String, otpCode: String? = null) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Notifications for incoming SMS messages and OTP codes"
          enableLights(true)
          enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
      }

      val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (!otpCode.isNullOrEmpty()) {
          putExtra("EXTRA_OTP_CODE", otpCode)
        }
      }

      val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

      notificationManager.notify(notificationId++, notificationBuilder.build())
    }
  }
}
