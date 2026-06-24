/*
 * WSL REMOTE COMPANION
 * Copyright (C) 2026  Maxime HAMM - NIMBLY CONSULTING - Maxime.HAMM@nimbly-consulting.com
 * Licensed under the GNU General Public License v2 or later.
 */
package io.nimbly.remotedev

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Windows-host integration for an IDE backend running on WSL (typically JetBrains Remote Development).
 *
 * In that setup the backend is Linux, so IntelliJ hides "Open in Explorer" / native-app actions and
 * exposes only Linux paths. We bridge to the Windows host through WSL interop: `wslpath` converts the
 * path, and `explorer.exe` / `cmd.exe` (reachable from WSL) act on the Windows side.
 */
object WslSupport {

    private val LOG = logger<WslSupport>()

    /** True when the backend runs on a WSL distro (Windows interop assumed available). Cached. */
    val isWslBackend: Boolean by lazy {
        if (!SystemInfo.isLinux) return@lazy false
        val version = runCatching { File("/proc/version").readText() }.getOrDefault("")
        val isWsl = System.getenv("WSL_DISTRO_NAME") != null ||
            version.contains("microsoft", ignoreCase = true) ||
            version.contains("WSL", ignoreCase = true)
        isWsl && File("/usr/bin/wslpath").exists()
    }

    /** Convert a Linux path to its Windows form via `wslpath -w` (e.g. `/mnt/c/x` → `C:\x`,
     *  `/home/u` → `\\wsl$\distro\home\u`), or `null` on failure. Runs a process — call off the EDT. */
    fun toWindowsPath(linuxPath: String): String? = runCatching {
        val out = ExecUtil.execAndGetOutput(GeneralCommandLine("wslpath", "-w", linuxPath))
        out.stdout.trim().takeIf { out.exitCode == 0 && it.isNotEmpty() }
    }.onFailure { LOG.warn("wslpath failed for $linuxPath", it) }.getOrNull()

    /** Reveal (select) a file — or open a folder — in Windows Explorer. */
    fun revealInExplorer(linuxPath: String, isDirectory: Boolean) {
        val win = toWindowsPath(linuxPath) ?: return
        // explorer.exe returns a non-zero exit code even on success → fire and forget.
        val cmd = if (isDirectory) GeneralCommandLine("explorer.exe", win)
        else GeneralCommandLine("explorer.exe", "/select,$win")
        runCatching { cmd.createProcess() }.onFailure { LOG.warn("explorer.exe failed", it) }
    }

    /** Open a file with its associated Windows application (`start`). */
    fun openInWindowsApp(linuxPath: String) {
        val win = toWindowsPath(linuxPath) ?: return
        runCatching { GeneralCommandLine("cmd.exe", "/c", "start", "", win).createProcess() }
            .onFailure { LOG.warn("cmd start failed", it) }
    }
}
