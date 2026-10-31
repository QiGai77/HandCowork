package app.xuezhang.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import app.xuezhang.MainActivity
import app.xuezhang.R
import app.xuezhang.engine.Live

class RecordService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "课堂录音", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "rec").setContentTitle("课堂学长正在听课")
            .setContentText("点按回到课堂").setSmallIcon(R.drawable.ic_stat).setContentIntent(pi).setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xuezhang:rec").apply { acquire(4 * 3600_000L) }
        if (thread == null) thread = Thread({ Live.recordLoop(); stopSelf() }, "rec").apply { start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Live.running.value = false
        runCatching { wake?.release() }
        super.onDestroy()
    }
}
