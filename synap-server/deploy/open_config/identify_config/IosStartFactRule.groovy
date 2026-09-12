package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.fact.ios.IosStartFact

ArrayList<Agenda> agendaList = new ArrayList<>()
String lanIp = ((IosStartFact)fact).getCommon().getLanIp();
if("127.0.0.1".equals(lanIp)){
agendaList.add(new Agenda("localIp",lanIp));
}
return agendaList