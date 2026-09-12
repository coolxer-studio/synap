package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatNetworkFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatNetworkFact) fact).getData() ?: [:]
List entries = (List) data.getOrDefault("entries", [])
int total = 0; int failures = 0
entries.each { entry ->
    if (entry instanceof Map) {
        total += ((Number) entry.getOrDefault("total", 0)).intValue()
        failures += ((Number) entry.getOrDefault("failures", 0)).intValue()
    }
}
if (total >= 20 && failures * 2 >= total) {
    agendaList.add(new Agenda("微信观察-网络失败突增", "total:" + total + ",failures:" + failures, AgendaLevelEnum.LOW))
}
return agendaList
