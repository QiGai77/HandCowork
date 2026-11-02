package app.xuezhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.ui.App
import app.xuezhang.ui.Nav
import app.xuezhang.ui.Route
import ru.noties.jlatexmath.JLatexMathAndroid

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        JLatexMathAndroid.init(applicationContext)
        app.xuezhang.net.Auth.init(applicationContext)
        Store.init(applicationContext)
        // v0.0.7.8 插件：加载已安装插件、重建主题/封面/技能/服务商注册表
        runCatching { app.xuezhang.engine.plugin.Plugins.init(java.io.File(filesDir, "plugins"), packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.7.8") }
        // Android 12+：系统 SplashScreen 退场时直接交给 Compose 的主题开屏动画（启用了带开屏的主题插件时）
        if (android.os.Build.VERSION.SDK_INT >= 31 && app.xuezhang.engine.plugin.ThemeRegistry.active.value?.splash != null) {
            splashScreen.setOnExitAnimationListener { v -> v.animate().alpha(0f).setDuration(180).withEndAction { v.remove() }.start() }
        }
        app.xuezhang.engine.GhBackup.appCtx = applicationContext
        runCatching { app.xuezhang.engine.GhBackup.schedule(applicationContext) }
        if (Live.running.value && Nav.stack.lastOrNull() != Route.Live) Nav.push(Route.Live)
        setContent { App() }
    }
}
