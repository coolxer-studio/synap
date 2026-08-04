package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5DeviceFact

H5DeviceFact h5DeviceFact = (H5DeviceFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>();
String userAgent = h5DeviceFact.getNavigator().getOrDefault("userAgent","")
if(userAgent.length() == 0 || userAgent.toLowerCase().matches(".*(headless|phantomjs).*")){
    agendaList.add(new Agenda("异常头",userAgent,AgendaLevelEnum.MEDIUM));
}
if(userAgent.length() > 0 && userAgent.toLowerCase().matches(".*(mumu|android[^)]*x86).*")){
    agendaList.add(new Agenda("模拟器",userAgent, AgendaLevelEnum.MEDIUM));
}
String requestUserAgent = h5DeviceFact.getRequestUserAgent() ?: ""
if (userAgent.length() > 0 && requestUserAgent.length() > 0 && userAgent != requestUserAgent) {
    agendaList.add(new Agenda("H5观察-UA不一致", "reported:" + userAgent + ",header:" + requestUserAgent, AgendaLevelEnum.LOW));
}
return agendaList
