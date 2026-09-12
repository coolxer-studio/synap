package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5IntegrityFact

H5IntegrityFact integrityFact = (H5IntegrityFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = integrityFact.getData() ?: [:]
int score = ((Number) data.getOrDefault("score", 0)).intValue()
if (score >= 2) {
    agendaList.add(new Agenda("H5观察-环境完整性", "score:" + score + ",signals:" + data.get("signals"), AgendaLevelEnum.MEDIUM))
}
return agendaList
