package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidEventFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidEventFact androidEventFact = (AndroidEventFact) fact;
if(androidEventFact.getMemModify().size()>0){
    agendaList.add(new Agenda("内存篡改", androidEventFact.getMemModify().join(","), AgendaLevelEnum.MEDIUM));
}

return agendaList