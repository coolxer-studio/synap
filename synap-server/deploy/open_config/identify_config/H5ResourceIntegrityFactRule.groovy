package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5ResourceIntegrityFact

H5ResourceIntegrityFact resourceFact = (H5ResourceIntegrityFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = resourceFact.getData() ?: [:]
Map<String, Object> signals = data.get("signals") instanceof Map ? (Map<String, Object>) data.get("signals") : [:]
int evidence = signals.values().count { Boolean.TRUE.equals(it) }
if (evidence >= 2) {
    agendaList.add(new Agenda("H5观察-资源与宿主完整性", "evidence:" + evidence + ",signals:" + signals, AgendaLevelEnum.MEDIUM))
}
List<Map<String, Object>> reports = data.get("reports") instanceof List ? (List<Map<String, Object>>) data.get("reports") : []
int reportCount = reports.sum { ((Number) it.getOrDefault("count", 0)).intValue() } ?: 0
if (reportCount >= 5) {
    agendaList.add(new Agenda("H5观察-浏览器安全报告聚集", "reports:" + reportCount, AgendaLevelEnum.LOW))
}
return agendaList
