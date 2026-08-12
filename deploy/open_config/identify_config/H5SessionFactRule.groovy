package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5SessionFact

H5SessionFact sessionFact = (H5SessionFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = sessionFact.getData() ?: [:]
if (Boolean.TRUE.equals(data.get("clockRollback"))) {
    agendaList.add(new Agenda("H5观察-客户端时钟回拨", data.toString(), AgendaLevelEnum.MEDIUM))
}
int concurrentTabs = ((Number) data.getOrDefault("concurrentTabs", 1)).intValue()
if (concurrentTabs > 3) {
    agendaList.add(new Agenda("H5观察-多标签并发", "tabs:" + concurrentTabs, AgendaLevelEnum.LOW))
}
return agendaList
