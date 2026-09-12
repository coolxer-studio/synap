package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatRuntimeFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatRuntimeFact) fact).getData() ?: [:]
int independentSignals = 0
if (Boolean.TRUE.equals(data.get("devtools"))) {
    agendaList.add(new Agenda("微信观察-开发者工具", "runtime:devtools", AgendaLevelEnum.LOW)); independentSignals++
}
if (Boolean.TRUE.equals(data.get("enableDebug"))) {
    agendaList.add(new Agenda("微信观察-enableDebug", "enableDebug:true", AgendaLevelEnum.LOW)); independentSignals++
}
if (Boolean.TRUE.equals(data.get("nonReleaseEnv"))) {
    agendaList.add(new Agenda("微信观察-非Release环境", "env:not-release", AgendaLevelEnum.LOW)); independentSignals++
}
if (Boolean.TRUE.equals(data.get("automationScene")) || ((Number) data.getOrDefault("scene", 0)).intValue() == 1030) {
    agendaList.add(new Agenda("微信观察-自动化启动场景", "scene:1030", AgendaLevelEnum.LOW)); independentSignals++
}
if (((Number) data.getOrDefault("codeFormatting", 0)).intValue() == 1) {
    agendaList.add(new Agenda("微信观察-代码格式化", "formatted:true", AgendaLevelEnum.LOW))
}
if (independentSignals >= 2) {
    agendaList.add(new Agenda("微信观察-多信号运行环境异常", "signals:" + independentSignals, AgendaLevelEnum.MEDIUM))
}
return agendaList
