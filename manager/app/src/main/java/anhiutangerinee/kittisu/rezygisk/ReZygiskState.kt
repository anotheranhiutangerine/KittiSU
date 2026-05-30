package anhiutangerinee.kittisu.rezygisk

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.util.getRootShell
import org.json.JSONObject
import java.io.File

private const val TAG = "ReZygiskState"

enum class MonitorState(val value: Int) {
    TRACING(0),
    STOPPING(1),
    STOPPED(2),
    EXITING(3),
    UNKNOWN(-1);

    companion object {
        fun fromInt(v: Int) = entries.find { it.value == v } ?: UNKNOWN
    }
}

data class DaemonInfo(
    val running: Boolean = false,
    val errorInfo: String? = null,
    val modules: List<String> = emptyList()
)

data class ReZygiskState(
    val rootImpl: String = "",
    val monitorState: MonitorState = MonitorState.UNKNOWN,
    val monitorReason: String? = null,
    val daemon64: DaemonInfo = DaemonInfo(),
    val daemon32: DaemonInfo = DaemonInfo(),
    val zygote64Injected: Boolean = false,
    val zygote32Injected: Boolean = false
) {
    val hasData: Boolean
        get() = monitorState != MonitorState.UNKNOWN
}

fun getReZygiskState(shell: Shell): ReZygiskState {
    val json = ShellUtils.fastCmd(shell, "cat /data/adb/rezygisk/state.json 2>/dev/null").trim()
    if (json.isEmpty()) return ReZygiskState()
    return try {
        parseStateJson(json)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse ReZygisk state", e)
        ReZygiskState()
    }
}

fun parseStateJson(json: String): ReZygiskState {
    val obj = JSONObject(json)
    val rootImpl = obj.optString("root", "")

    val monitor = obj.optJSONObject("monitor")
    val monitorStateRaw = monitor?.optString("state", "-1")?.toIntOrNull() ?: -1
    val monitorState = MonitorState.fromInt(monitorStateRaw)
    val monitorReason = monitor?.optString("reason", null)

    val rezygiskd = obj.optJSONObject("rezygiskd")

    fun parseDaemon(key: String): DaemonInfo {
        val d = rezygiskd?.optJSONObject(key) ?: return DaemonInfo()
        val running = d.optInt("state", 0) == 1
        val errorInfo = d.optString("reason", null)
        val modules = mutableListOf<String>()
        d.optJSONArray("modules")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotEmpty() }?.let { modules.add(it) }
            }
        }
        return DaemonInfo(running, errorInfo, modules)
    }

    val daemon64 = parseDaemon("64")
    val daemon32 = parseDaemon("32")

    val zygote = obj.optJSONObject("zygote")
    val zygote64Injected = zygote?.optInt("64", 0) == 1
    val zygote32Injected = zygote?.optInt("32", 0) == 1

    return ReZygiskState(
        rootImpl = rootImpl,
        monitorState = monitorState,
        monitorReason = monitorReason,
        daemon64 = daemon64,
        daemon32 = daemon32,
        zygote64Injected = zygote64Injected,
        zygote32Injected = zygote32Injected
    )
}

fun isReZygiskRunning(shell: Shell): Boolean {
    val state = getReZygiskState(shell)
    return state.monitorState == MonitorState.TRACING || state.monitorState == MonitorState.STOPPED
}
