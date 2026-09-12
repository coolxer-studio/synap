package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5RuntimeFact

H5RuntimeFact h5RuntimeFact = (H5RuntimeFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String,Object> custom = h5RuntimeFact.getCustom() ?: [:]
Object format = custom.get("format")
boolean formatted = Boolean.TRUE.equals(format)
        || format instanceof Number && ((Number) format).intValue() > 0
        || "1" == String.valueOf(format)
if (formatted) {
    agendaList.add(new Agenda("H5观察-代码格式化", "format:1", AgendaLevelEnum.LOW));
}
ArrayList<Integer> getSpider = (ArrayList<Integer>)custom.getOrDefault("getSpider",new ArrayList<Integer>())
int automationSignals = getSpider.count { it == 1 }
List<String> knownGlobals = custom.get("unknownFunctions") instanceof List ? (List<String>) custom.get("unknownFunctions") : []
if (automationSignals + knownGlobals.size() >= 2) {
    agendaList.add(new Agenda("H5观察-运行环境自动化", "signals:" + automationSignals + ",globals:" + knownGlobals, AgendaLevelEnum.MEDIUM));
} else if (automationSignals == 1 || !knownGlobals.isEmpty()) {
    agendaList.add(new Agenda("H5观察-单一自动化特征", "signals:" + automationSignals + ",globals:" + knownGlobals, AgendaLevelEnum.LOW));
}
return agendaList
