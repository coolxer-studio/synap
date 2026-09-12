package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatLocationFact

ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String, Object> data = ((WechatLocationFact) fact).getData() ?: [:]
double speed = ((Number) data.getOrDefault("speed", 0)).doubleValue()
if (speed > 100) {
    agendaList.add(new Agenda("微信观察-位置速度异常", "speed:" + speed, AgendaLevelEnum.LOW))
}
return agendaList
