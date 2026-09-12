package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidRuntimeFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidRuntimeFact androidRuntimeFact = (AndroidRuntimeFact) fact;

if (androidRuntimeFact.getCmd() != null) {
    String suCmd = androidRuntimeFact.getCmd().containsKey("which su");
    if (suCmd != null && suCmd.length() > 0) {
        agendaList.add(new Agenda("su命令可用", suCmd, AgendaLevelEnum.HIGH));
    }
}

for (String process : androidRuntimeFact.getProcess()) {
    if (process.contains("(ldinit)")) {
        agendaList.add(new Agenda("ldinit注入（调试）", process, AgendaLevelEnum.HIGH));
    }else if (process.startsWith("root")) {
        agendaList.add(new Agenda("root库注入", process, AgendaLevelEnum.HIGH));
    }else if (process.contains("frida")) {
        agendaList.add(new Agenda("frida注入", process, AgendaLevelEnum.HIGH));
    }
}

if (androidRuntimeFact.getCustom().usbDebug) {
    agendaList.add(new Agenda("usb调试模式", "usbDebug", AgendaLevelEnum.LOW));
}
if (androidRuntimeFact.getCustom().tcpDebug) {
    agendaList.add(new Agenda("tcp调试", "tcpDebug", AgendaLevelEnum.LOW));
}
if (androidRuntimeFact.getCustom().debugConnect) {
    agendaList.add(new Agenda("调试连接", "debugConnect", AgendaLevelEnum.MEDIUM));
}
if (androidRuntimeFact.getCustom().mockLocation) {
    agendaList.add(new Agenda("模拟位置", "mock_location", AgendaLevelEnum.MEDIUM));
}
if (androidRuntimeFact.getCustom().frida) {
    agendaList.add(new Agenda("frida调试", "frida", AgendaLevelEnum.HIGH));
}
if (androidRuntimeFact.getCustom().ptrace != null) {
    Integer pid = Integer.parseInt(androidRuntimeFact.getCustom().ptrace);
    if (pid > 0) {
        agendaList.add(new Agenda("ptrace调试", pid, AgendaLevelEnum.HIGH));
    }
}

for (String className : androidRuntimeFact.getHasClass()) {
    if ("me.weishu.exposed.ExposedApplication" == className) {
        agendaList.add(new Agenda("Exposed实例化", className, AgendaLevelEnum.HIGH));
    }
}


for (String instance : androidRuntimeFact.getHasInstance()) {
    if ("de.robv.android.xposed.XposedHelpers" == instance) {
        agendaList.add(new Agenda("XposedHelpers实例化", instance, AgendaLevelEnum.HIGH));
    }
    if ("de.robv.android.xposed.XposedBridge" == instance) {
        agendaList.add(new Agenda("XposedBridge实例化", instance, AgendaLevelEnum.HIGH));
    }
}

for (String serviceName : androidRuntimeFact.getHasService()) {
    if ("user.xposed.system" == serviceName) {
        agendaList.add(new Agenda("xposed服务启用", serviceName, AgendaLevelEnum.HIGH));
    }
}

for (String displayName : androidRuntimeFact.getDisplay()) {
    if (displayName.contains("virtual")) {
        agendaList.add(new Agenda("虚拟屏幕", displayName, AgendaLevelEnum.MEDIUM));
    } else {
        agendaList.add(new Agenda("扩展屏幕", displayName, AgendaLevelEnum.LOW));
    }
}

return agendaList