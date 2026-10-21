package com.myrizz.assistant.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.myrizz.assistant.AppContainer
import com.myrizz.assistant.AssistantController
import com.myrizz.assistant.IncomingShare
import com.myrizz.assistant.LocaleHelper
import com.myrizz.assistant.MainActivity
import com.myrizz.assistant.R
import com.myrizz.assistant.container
import com.myrizz.assistant.domain.Routes
import com.myrizz.assistant.ui.AssistantSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Hosts the floating bubble and the assistant panel in system overlay windows.
 *
 * - It is a user-started foreground service (visible notification with a "Turn off" action).
 * - It never reads other apps' content: all text arrives through explicit paste/share/picker actions.
 * - The panel is a normal focusable window, so the user's own keyboard (Samsung Keyboard) types into it.
 */
class OverlayService : Service() {

    private lateinit var container: AppContainer
    private lateinit var windowManager: WindowManager
    private lateinit var uiContext: Context
    private lateinit var controller: AssistantController

    private val owner = ViewOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dragStartX = 0
    private var dragStartY = 0

    private var panel: PanelRoot? = null
    private var panelState: MutableTransitionState<Boolean>? = null

    /** True when the panel was opened by a share while the bubble is switched off: stop when it closes. */
    private var transient = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        container = applicationContext.container
        uiContext = ContextThemeWrapper(
            LocaleHelper.wrap(this, container.settings.current.uiLanguage),
            R.style.Theme_MyRizz,
        )
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controller = AssistantController(scope, container)
        owner.start()
        startAsForeground()
        scope.launch {
            container.incoming.collect { share ->
                if (share != null) onIncoming(share)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            container.settings.update { it.copy(bubbleEnabled = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                container.settings.update { it.copy(bubbleEnabled = false) }
                stopSelf()
            }
            ACTION_OPEN_PANEL -> {
                transient = !container.settings.current.bubbleEnabled
                if (!transient) showBubble()
                showPanel()
            }
            else -> {
                transient = false
                showBubble()
            }
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bubble?.let { clampAndApply(it) }
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        removePanelNow()
        removeBubble()
        controller.release()
        owner.stop()
        super.onDestroy()
    }

    // ---- Foreground notification -------------------------------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.overlay_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.overlay_notif_hide), stop)
            .addAction(0, getString(R.string.overlay_notif_open), openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- Bubble ------------------------------------------------------------------------------------

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun showBubble() {
        if (bubble != null) {
            bubble?.visibility = if (panel == null) View.VISIBLE else View.GONE
            return
        }
        val view = BubbleView(
            uiContext,
            onDrag = { dx, dy, first -> onBubbleDrag(dx, dy, first) },
            onDragEnd = { snapToEdge() },
            onTap = { showPanel() },
        )
        view.contentDescription = getString(R.string.bubble_cd)
        val (w, h) = screenSize()
        val saved = container.settings.current
        val params = WindowManager.LayoutParams(
            view.sizePx,
            view.sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (saved.bubbleX >= 0) edgeX(saved.bubbleX, w, view.sizePx) else w - view.sizePx
            y = if (saved.bubbleY >= 0) saved.bubbleY else (h * 0.4f).toInt()
        }
        bubbleParams = params
        bubble = view
        try {
            windowManager.addView(view, params)
            clampAndApply(view)
        } catch (_: Exception) {
            // Permission revoked or the system refused the window: switch the bubble off cleanly.
            bubble = null
            bubbleParams = null
            container.settings.update { it.copy(bubbleEnabled = false) }
            stopSelf()
        }
    }

    private fun edgeX(x: Int, screenW: Int, size: Int): Int = if (x + size / 2 > screenW / 2) screenW - size else 0

    private fun onBubbleDrag(dx: Float, dy: Float, first: Boolean) {
        val params = bubbleParams ?: return
        val view = bubble ?: return
        if (first) {
            dragStartX = params.x
            dragStartY = params.y
            return
        }
        params.x = dragStartX + dx.toInt()
        params.y = dragStartY + dy.toInt()
        clampAndApply(view)
    }

    private fun clampAndApply(view: BubbleView) {
        val params = bubbleParams ?: return
        val (w, h) = screenSize()
        params.x = min(max(params.x, 0), max(0, w - view.sizePx))
        params.y = min(max(params.y, 0), max(0, h - view.sizePx))
        if (view.isAttachedToWindow) runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun snapToEdge() {
        val params = bubbleParams ?: return
        val view = bubble ?: return
        val (w, _) = screenSize()
        val target = edgeX(params.x, w, view.sizePx)
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (view.isAttachedToWindow) runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
        container.settings.update { it.copy(bubbleX = target, bubbleY = params.y) }
    }

    private fun removeBubble() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        bubbleParams = null
    }

    // ---- Panel -------------------------------------------------------------------------------------

    private class PanelRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private fun showPanel() {
        if (panel != null) {
            panelState?.targetState = true
            return
        }
        val state = MutableTransitionState(false)
        val root = PanelRoot(uiContext) { closePanel() }
        // Compose finds its lifecycle owners by walking up the view tree, so set them on the root.
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)

        val compose = ComposeView(uiContext).apply {
            setContent {
                AssistantSheet(
                    controller = controller,
                    visibleState = state,
                    onClose = { closePanel() },
                    onPickScreenshot = { launchPicker() },
                    onOpenSettings = { openApp(Routes.SETTINGS) },
                    settingsLabel = getString(R.string.overlay_open_app_settings),
                    onOpenApp = { openApp(null) },
                    onCopied = { closePanel() },
                    useImePadding = false,
                )
            }
        }
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Focusable (no FLAG_NOT_FOCUSABLE) so the user's keyboard can type into the panel's fields.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        }
        try {
            windowManager.addView(root, params)
        } catch (_: Exception) {
            if (transient) stopSelf()
            return
        }
        panel = root
        panelState = state
        bubble?.visibility = View.GONE
        state.targetState = true
    }

    private fun closePanel() {
        val state = panelState ?: return
        if (!state.targetState) return // already closing
        state.targetState = false
        scope.launch {
            delay(260)
            // Reopened during the exit animation? Then keep the window.
            if (panelState !== state || state.targetState) return@launch
            removePanelNow()
            if (transient) stopSelf() else bubble?.visibility = View.VISIBLE
        }
    }

    private fun removePanelNow() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        panelState = null
    }

    // ---- Actions from the panel --------------------------------------------------------------------

    private fun launchPicker() {
        val started = runCatching {
            startActivity(Intent(this, PickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!started) return
        // Hide the panel once the picker is on screen; it reopens when the picker delivers its result.
        scope.launch {
            delay(300)
            removePanelNow()
        }
    }

    private fun openApp(route: String?) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        runCatching { startActivity(intent) }
        closePanel()
    }

    private fun onIncoming(share: IncomingShare) {
        container.consumeIncoming()
        when {
            share.text != null -> {
                controller.clearShot()
                controller.setInput(share.text)
            }
            share.imagePath != null -> controller.onScreenshotFile(File(share.imagePath))
        }
        // An empty share means the picker was cancelled: just bring the panel back.
        showPanel()
    }

    companion object {
        const val ACTION_SHOW_BUBBLE = "com.myrizz.assistant.action.SHOW_BUBBLE"
        const val ACTION_OPEN_PANEL = "com.myrizz.assistant.action.OPEN_PANEL"
        const val ACTION_STOP = "com.myrizz.assistant.action.STOP"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var running: Boolean = false
            private set
    }
}
