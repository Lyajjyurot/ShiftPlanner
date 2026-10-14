package com.shiftplanner.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 未捕获异常落盘（debug 包专用）：
 * 下次启动时由 MainActivity 读取并整屏显示，提供「复制堆栈」按钮。
 */
object CrashRecorder {

    private const val PREFS = "crash_recorder"
    private const val KEY = "last_crash"

    fun install(app: Application) {
        // debug 包才安装
        if (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                prefs(app).edit()
                    .putString(KEY, "${throwable.javaClass.name}: ${throwable.message ?: ""}\n\n$sw")
                    .commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读取并清除上次崩溃信息；无则返回 null */
    fun takeCrash(context: Context): String? {
        return runCatching {
            val p = prefs(context)
            val s = p.getString(KEY, null)
            if (s != null) p.edit().remove(KEY).apply()
            s
        }.getOrNull()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
