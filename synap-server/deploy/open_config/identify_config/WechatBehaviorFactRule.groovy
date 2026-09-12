package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatBehaviorFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatBehaviorFact) fact).getData() ?: [:]
int samples = ((Number) data.getOrDefault("sampleCount", 0)).intValue()
int anomalies = 0
if (samples >= 20 && ((Number) data.getOrDefault("intervalVariance", 1)).doubleValue() < 2) anomalies++
if (samples >= 20 && ((Number) data.getOrDefault("speedVariance", 1)).doubleValue() < 0.0001) anomalies++
if (((Number) data.getOrDefault("untrustedCount", 0)).intValue() >= 3 || ((Number) data.getOrDefault("impossibleCount", 0)).intValue() >= 2) anomalies++
if (samples >= 20 && ((Number) data.getOrDefault("directionChanges", 1)).intValue() == 0) anomalies++
if (samples >= 20 && anomalies >= 2) {
    agendaList.add(new Agenda("微信观察-自动化行为", "anomalies:" + anomalies + ",samples:" + samples, AgendaLevelEnum.MEDIUM))
}
return agendaList
