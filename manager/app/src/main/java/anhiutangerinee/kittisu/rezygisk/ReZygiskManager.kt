package anhiutangerinee.kittisu.rezygisk

import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.ui.util.getKsuDaemonPath
import java.io.File

private const val TAG = "ReZygiskManager"
private const val REZYGISK_WORK_DIR = "/data/adb/rezygisk"
private const val REZYGISK_BOOT_SCRIPT = "/data/adb/post-fs-data.d/rezygisk-builtin.sh"

private const val SEPOLICY_RULES = """type zygisk_file file_type
typeattribute zygisk_file mlstrustedobject
allow zygote zygisk_file sock_file {read write}
allow zygote magisk lnk_file read
allow zygote unlabeled file {read open}
allow zygote zygote capability sys_chroot
allow zygote su dir search
allow zygote su {lnk_file file} read
allow zygote ksu dir search
allow zygote ksu {lnk_file file} read
allow zygote adb_data_file dir search
allow zygote adb_data_file file *
allow zygote proc file {read open}
allow zygote nsfs file {read open}
allow zygote zygote process execmem
allow system_server system_server process execmem
allow zygote tmpfs file *
allow zygote appdomain_tmpfs file *
allow zygote system_lib_file file execmod
"""

fun deployReZygiskBinaries(shell: Shell): Boolean {
    val nativeLibDir = ksuApp.applicationInfo.nativeLibraryDir
    val has64Bit = Build.SUPPORTED_ABIS.any { it.contains("64") }
    val has32Bit = Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" || it == "x86" }

    val result = shell.newJob().add(
        "rm -rf $REZYGISK_WORK_DIR && mkdir -p $REZYGISK_WORK_DIR && chmod 555 $REZYGISK_WORK_DIR && chcon u:object_r:system_file:s0 $REZYGISK_WORK_DIR"
    ).to(ArrayList(), null).exec()
    if (!result.isSuccess) {
        Log.e(TAG, "Failed to create ReZygisk work directory")
        return false
    }

    val cmds = mutableListOf<String>()

    if (has64Bit) {
        val srcPtrace64 = "$nativeLibDir/libzygisk-ptrace64.so"
        val srcZygiskd64 = "$nativeLibDir/libzygiskd64.so"
        val srcLibzygisk = "$nativeLibDir/libzygisk.so"
        if (File(srcPtrace64).exists() && File(srcZygiskd64).exists()) {
            cmds.add("cp '$srcPtrace64' $REZYGISK_WORK_DIR/zygisk-ptrace64")
            cmds.add("cp '$srcZygiskd64' $REZYGISK_WORK_DIR/zygiskd64")
            cmds.add("cp '$srcLibzygisk' $REZYGISK_WORK_DIR/libzygisk.so")
            cmds.add("chmod 755 $REZYGISK_WORK_DIR/zygisk-ptrace64 $REZYGISK_WORK_DIR/zygiskd64")
        }
    }

    val ptrace32In64 = "$nativeLibDir/libzygisk-ptrace32.so"
    val zygiskd32In64 = "$nativeLibDir/libzygiskd32.so"
    if (has32Bit && File(ptrace32In64).exists() && File(zygiskd32In64).exists()) {
        cmds.add("cp '$ptrace32In64' $REZYGISK_WORK_DIR/zygisk-ptrace32")
        cmds.add("cp '$zygiskd32In64' $REZYGISK_WORK_DIR/zygiskd32")
        cmds.add("chmod 755 $REZYGISK_WORK_DIR/zygisk-ptrace32 $REZYGISK_WORK_DIR/zygiskd32")
    }

    if (cmds.isEmpty()) {
        Log.e(TAG, "No ReZygisk binaries found in APK")
        return false
    }

    val deployResult = shell.newJob().add(cmds).to(ArrayList(), null).exec()
    if (!deployResult.isSuccess) {
        Log.e(TAG, "Failed to deploy ReZygisk binaries: ${deployResult.err.joinToString()}")
        return false
    }

    Log.i(TAG, "ReZygisk binaries deployed successfully")
    return true
}

fun startReZygisk(shell: Shell): Boolean {
    val ptrace = detectPtraceBinary(shell) ?: return false
    val result = shell.newJob().add("$ptrace monitor &").to(ArrayList(), null).exec()
    val ok = result.isSuccess
    Log.i(TAG, "ReZygisk start result: $ok")
    return ok
}

fun stopReZygisk(shell: Shell): Boolean {
    val ptrace = detectPtraceBinary(shell)
    if (ptrace != null) {
        val result = shell.newJob().add("$ptrace ctl exit").to(ArrayList(), null).exec()
        val ok = result.isSuccess
        Log.i(TAG, "ReZygisk stop result: $ok")
        return ok
    }
    val killResult = ShellUtils.fastCmdResult(shell, "pkill -f zygisk-ptrace")
    Log.i(TAG, "ReZygisk stop (pkill) result: $killResult")
    return killResult
}

fun applyReZygiskSepolicy(shell: Shell): Boolean {
    val ksuPath = getKsuDaemonPath()
    val tmpFile = "/data/adb/rezygisk_sepolicy_tmp"
    val rulesEscaped = SEPOLICY_RULES.replace("'", "'\\''")

    val writeResult = shell.newJob().add("echo '$rulesEscaped' > $tmpFile").to(ArrayList(), null).exec()
    if (!writeResult.isSuccess) return false

    val applyResult = shell.newJob().add("$ksuPath sepolicy patch '$rulesEscaped'").to(ArrayList(), null).exec()
    shell.newJob().add("rm -f $tmpFile").to(ArrayList(), null).exec()

    val ok = applyResult.isSuccess
    Log.i(TAG, "ReZygisk sepolicy apply result: $ok")
    return ok
}

fun installReZygiskBootScript(shell: Shell): Boolean {
    val script = """#!/system/bin/sh
set -e
create_sys_perm() {
  mkdir -p \$1
  chmod 555 \$1
  chcon u:object_r:system_file:s0 \$1
}
export TMP_PATH=/data/adb/rezygisk
create_sys_perm \$TMP_PATH
CPU_ABIS=\$(getprop ro.product.cpu.abilist)
if [[ "\$CPU_ABIS" == *"arm64-v8a"* || "\$CPU_ABIS" == *"x86_64"* ]]; then
  /data/adb/rezygisk/zygisk-ptrace64 monitor &
else
  /data/adb/rezygisk/zygisk-ptrace32 monitor &
fi
exit 0
"""

    val result = shell.newJob().add(
        "mkdir -p /data/adb/post-fs-data.d && echo '$script' > $REZYGISK_BOOT_SCRIPT && chmod 755 $REZYGISK_BOOT_SCRIPT"
    ).to(ArrayList(), null).exec()
    val ok = result.isSuccess
    Log.i(TAG, "ReZygisk boot script install result: $ok")
    return ok
}

fun uninstallReZygiskBootScript(shell: Shell): Boolean {
    val result = shell.newJob().add("rm -f $REZYGISK_BOOT_SCRIPT").to(ArrayList(), null).exec()
    return result.isSuccess
}

fun cleanupReZygisk(shell: Shell): Boolean {
    stopReZygisk(shell)
    uninstallReZygiskBootScript(shell)
    val result = shell.newJob().add("rm -rf $REZYGISK_WORK_DIR").to(ArrayList(), null).exec()
    Log.i(TAG, "ReZygisk cleanup result: ${result.isSuccess}")
    return result.isSuccess
}

private fun detectPtraceBinary(shell: Shell): String? {
    listOf(
        "$REZYGISK_WORK_DIR/zygisk-ptrace64",
        "$REZYGISK_WORK_DIR/zygisk-ptrace32"
    ).forEach { path ->
        if (ShellUtils.fastCmdResult(shell, "test -x $path")) return path
    }
    return null
}

fun isReZygiskModuleInstalledOnDevice(): Boolean {
    val moduleProp = File("/data/adb/modules/rezygisk/module.prop")
    val disable = File("/data/adb/modules/rezygisk/disable")
    val remove = File("/data/adb/modules/rezygisk/remove")
    return moduleProp.exists() && !disable.exists() && !remove.exists()
}

fun disableReZygiskModule(shell: Shell): Boolean {
    val disableFile = "/data/adb/modules/rezygisk/disable"
    val result = shell.newJob().add("touch $disableFile").to(ArrayList(), null).exec()
    Log.i(TAG, "Disable ReZygisk module result: ${result.isSuccess}")
    return result.isSuccess
}
