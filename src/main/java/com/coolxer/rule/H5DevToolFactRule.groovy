package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5DevToolFact

H5DevToolFact h5DevToolFact = (H5DevToolFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.add(new Agenda("打开开发工具",h5DevToolFact.getData().toMapString(), AgendaLevelEnum.LOW));
return agendaList