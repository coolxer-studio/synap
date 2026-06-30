package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5SelfAppFact

H5SelfAppFact h5SelfAppFact = (H5SelfAppFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>()
String domain = h5SelfAppFact.getBase().getDomain();
if(domain.length() == 0 || domain!="www.test.xxx"){
    agendaList.add(new Agenda("域名欺诈",domain, AgendaLevelEnum.HIGH));
}
return agendaList

