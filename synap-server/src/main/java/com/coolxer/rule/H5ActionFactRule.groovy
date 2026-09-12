package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5ActionFact

H5ActionFact actionFact = (H5ActionFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = actionFact.getData() ?: [:]
Map<String, Object> behavior = data.get("behavior") instanceof Map ? (Map<String, Object>) data.get("behavior") : [:]
int untrusted = ((Number) behavior.getOrDefault("untrustedEventCount", 0)).intValue()
int eventCount = ((Number) behavior.getOrDefault("eventCount", 0)).intValue()
if (eventCount >= 2 && untrusted >= 2) {
    agendaList.add(new Agenda("H5观察-业务动作非可信事件", "name:" + data.get("name") + ",untrusted:" + untrusted, AgendaLevelEnum.LOW))
}
return agendaList
