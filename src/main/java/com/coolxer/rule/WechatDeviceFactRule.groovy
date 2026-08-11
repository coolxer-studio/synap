package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatDeviceFact

ArrayList<Agenda> agendaList = new ArrayList<>()
WechatDeviceFact deviceFact = (WechatDeviceFact) fact
String brand = deviceFact.getDevice()?.getBrand()?.toLowerCase() ?: ""
if ("devtools" == brand) {
    agendaList.add(new Agenda("微信观察-开发者工具", "brand:devtools", AgendaLevelEnum.LOW))
}
return agendaList
