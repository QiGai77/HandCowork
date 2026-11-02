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
        app.xuezhang.engine.GhBackup.appCtx = applicationContext
        runCatching { app.xuezhang.engine.GhBackup.schedule(applicationContext) }
        if (Live.running.value && Nav.stack.lastOrNull() != Route.Live) Nav.push(Route.Live)
        setContent { App() }
    }
}
