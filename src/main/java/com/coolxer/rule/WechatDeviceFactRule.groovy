package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.wechat.WechatDeviceFact


ArrayList<Agenda> agendaList = new ArrayList<>()
String brand = ((WechatDeviceFact)fact).getDevice().getBrand()
if("devtools" == brand){
    agendaList.add(new Agenda("开发模式","devtools", AgendaLevelEnum.HIGH))

}
return agendaList