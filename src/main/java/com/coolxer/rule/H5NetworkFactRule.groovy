package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5NetworkFact

H5NetworkFact networkFact = (H5NetworkFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = networkFact.getData() ?: [:]
List<Map<String, Object>> endpoints = (List<Map<String, Object>>) data.getOrDefault("endpoints", [])
int requests = endpoints.sum { ((Number) it.getOrDefault("count", 0)).intValue() } ?: 0
int failures = endpoints.sum { ((Number) it.getOrDefault("failures", 0)).intValue() } ?: 0
if (requests >= 20 && failures * 2 >= requests) {
    agendaList.add(new Agenda("H5观察-接口失败突增", "requests:" + requests + ",failures:" + failures, AgendaLevelEnum.LOW))
}
return agendaList
