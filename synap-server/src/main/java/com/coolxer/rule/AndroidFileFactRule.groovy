package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidFileFact

ArrayList<Agenda> agendaList = new ArrayList<>()
AndroidFileFact androidFileFact = (AndroidFileFact)fact;

//"/x8/plugins/touch.apk",
//"/system/etc/init.dundi.sh",
//"ueventd.dundi.rc",
//"/data/local/tmp/com.cyjh.ddy.id",
//"/storage/emulated/0/.bluestacks.prop",
//"fstab.andy",
//"/system/bin/windroyed",
//"ueventd.andy.rc",
//"fstab.nox",
//"init.nox.rc",
//"ueventd.nox.rc",
//"/dev/qemu_pipe",
//"/dev/socket/qemud",
//"/dev/socket/genyd",
//"/dev/socket/baseband_genyd",
//"ueventd.android_x86.rc",
//"x86.prop",
//"ueventd.ttVM_x86.rc",
//"init.ttVM_x86.rc",
//"fstab.ttVM_x86",
//"fstab.vbox86",
//"init.vbox86.rc",
//"ueventd.vbox86.rc",
//"/mnt/apkinstallshare",
//"/mnt/apkinstallshareicon",
//"/sdcard/ldsdk",
//"/sdcard/Android/data/com.android.flysilkworm",
//"/system/xbin/ku.sud",
//"/data/local/su",
//"/data/local/bin/su",
//"/data/local/xbin/su",
//"/sbin/su",
//"/su/bin/su",
//"/system/bin/su",
//"/system/bin/.ext/su",
//"/system/bin/failsafe/su",
//"/system/sd/xbin/su",
//"/system/usr/we-need-root/su",
//"/system/xbin/su",
//"/system/sbin/su/su",
//"/system/app/Superuser.apk",
//"/system/lib/libxposed_art.so",
//"/system/lib64/libxposed_art.so",
//"/sbin/.magisk/modules/riru_lsposed",
//"/system/lib64/libSubstrateRun.so",
//"/system/lib64/libsubstrate-dvm.so",
//"/system/lib64/libSubstrateJNI.so",
//"/system/lib64/libsubstrate.so",
//"/system/lib/libsubstrate-dvm.so",
//"/system/lib/libsubstrate.so",
//"/system/lib/libSubstrateJNI.so",
//"/system/lib/libSubstrateRun.so",
//"/sbin/magisk",
//"/data/adb/magisk",
//"/sbin/.magisk",
//"/cache/.disable_magisk",
//"/dev/.magisk.unblock",
//"/cache/magisk.log",
//"/data/adb/magisk.img",
//"/data/adb/magisk.db",
//"/data/adb/.boot_count",
//"/data/adb/magisk_simple",
//"/init.magisk.rc",
//"/data/dalvik-cache/profiles/com.topjohn"

for(String fileName: androidFileFact.getExist()){
    switch (fileName){
        case "/system/bin/su":
            agendaList.add(new Agenda("ROOT",fileName, AgendaLevelEnum.HIGH));
            break
        default:
            agendaList.add(new Agenda("异常文件",fileName, AgendaLevelEnum.MEDIUM));
    }

}

if(androidFileFact.getAccess().size()>0){
    agendaList.add(new Agenda("文件提权风险",androidFileFact.getAccess().entrySet().join("\n"), AgendaLevelEnum.MEDIUM));
}
return agendaList