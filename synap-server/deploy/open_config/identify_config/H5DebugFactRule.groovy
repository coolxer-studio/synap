package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5DebugFact

H5DebugFact h5DebugFact = (H5DebugFact)fact;

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.add(new Agenda("H5观察-调试暂停","type:"+h5DebugFact.getDebugType()+",diff:"+h5DebugFact.getDiff(), AgendaLevelEnum.LOW));
return agendaList
