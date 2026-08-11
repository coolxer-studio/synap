package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatIntegrityFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatIntegrityFact) fact).getData() ?: [:]
int evidence = ((Number) data.getOrDefault("evidenceCount", 0)).intValue()
int score = ((Number) data.getOrDefault("weightedScore", 0)).intValue()
if (evidence >= 2 && score >= 4) {
    agendaList.add(new Agenda("微信观察-多信号完整性异常", "evidence:" + evidence + ",score:" + score, AgendaLevelEnum.MEDIUM))
} else if (evidence == 1) {
    agendaList.add(new Agenda("微信观察-单信号完整性变化", "score:" + score, AgendaLevelEnum.LOW))
}
return agendaList
