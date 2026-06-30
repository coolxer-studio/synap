package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5DeviceFact

H5DeviceFact h5DeviceFact = (H5DeviceFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>();
String userAgent = h5DeviceFact.getNavigator().getOrDefault("userAgent","")
if(userAgent.length() == 0 || userAgent.matches(".*headless.*|.*phantimjs.*")){
    agendaList.add(new Agenda("异常头",userAgent,AgendaLevelEnum.MEDIUM));
}
if(userAgent.length() > 0 && userAgent.matches(".*mumu.*|.*x86.*|.*like Mac OS X.*")){
    agendaList.add(new Agenda("模拟器",userAgent, AgendaLevelEnum.MEDIUM));
}
return agendaList