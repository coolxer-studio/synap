package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5BehaviorFact

H5BehaviorFact behaviorFact = (H5BehaviorFact) fact
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = behaviorFact.getData() ?: [:]
int anomalies = 0
int pointerSamples = ((Number) data.getOrDefault("pointerSamples", 0)).intValue()
int clickCount = ((Number) data.getOrDefault("clickCount", 0)).intValue()
int inputCount = ((Number) data.getOrDefault("inputCount", 0)).intValue()
double directionRatio = ((Number) data.getOrDefault("directionChangeRatio", 0)).doubleValue()
double clickInterval = ((Number) data.getOrDefault("averageClickInterval", 0)).doubleValue()
double inputInterval = ((Number) data.getOrDefault("averageInputInterval", 0)).doubleValue()
if (pointerSamples >= 20 && directionRatio == 0) anomalies++
if (clickCount >= 5 && clickInterval > 0 && clickInterval < 80) anomalies++
if (inputCount >= 5 && inputInterval > 0 && inputInterval < 30) anomalies++
if (anomalies >= 2) {
    agendaList.add(new Agenda("H5观察-自动化行为", "anomalies:" + anomalies + ",samples:" + pointerSamples, AgendaLevelEnum.MEDIUM))
}
return agendaList
