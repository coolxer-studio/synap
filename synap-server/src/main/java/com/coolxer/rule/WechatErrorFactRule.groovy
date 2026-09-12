package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatErrorFact

ArrayList<Agenda> agendaList = new ArrayList<>()
WechatErrorFact errorFact = (WechatErrorFact) fact
if (errorFact.getCount() >= 5) {
    agendaList.add(new Agenda("微信观察-重复异常", "fingerprint:" + errorFact.getFingerprint() + ",count:" + errorFact.getCount(), AgendaLevelEnum.LOW))
}
return agendaList
