package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5EventFact

ArrayList<Agenda> agendaList = new ArrayList<>()
H5EventFact eventFact = (H5EventFact) fact
if (Boolean.FALSE.equals(eventFact.getData()?.get("trusted"))) {
    agendaList.add(new Agenda("H5观察-非可信浏览器事件", eventFact.getData().toString(), AgendaLevelEnum.LOW))
}
return agendaList
