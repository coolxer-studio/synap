package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatDebugFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatDebugFact) fact).getData() ?: [:]
if (((Number) data.getOrDefault("longPause", 0)).longValue() > 1500) {
    agendaList.add(new Agenda("微信观察-调试长暂停", "pause:" + data.get("longPause"), AgendaLevelEnum.LOW))
}
return agendaList
