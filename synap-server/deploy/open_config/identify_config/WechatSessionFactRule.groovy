package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatSessionFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatSessionFact) fact).getData() ?: [:]
if (Boolean.TRUE.equals(data.get("clockRollback"))) {
    agendaList.add(new Agenda("微信观察-客户端时钟回拨", "clock:rollback", AgendaLevelEnum.MEDIUM))
}
int transitions = ((Number) data.getOrDefault("showCount", 0)).intValue() + ((Number) data.getOrDefault("hideCount", 0)).intValue()
int networkChanges = ((Number) data.getOrDefault("networkChanges", 0)).intValue()
int memoryWarnings = ((Number) data.getOrDefault("memoryWarnings", 0)).intValue()
if (transitions >= 10 || networkChanges >= 10 || memoryWarnings >= 3) {
    agendaList.add(new Agenda("微信观察-会话状态频繁变化", "transitions:" + transitions + ",network:" + networkChanges + ",memory:" + memoryWarnings, AgendaLevelEnum.LOW))
}
return agendaList
