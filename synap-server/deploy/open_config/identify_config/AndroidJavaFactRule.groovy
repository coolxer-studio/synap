package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidJavaFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidJavaFact androidJavaFact = (AndroidJavaFact) fact;
agendaList.add(new Agenda("应用崩溃（java）", androidJavaFact.type, AgendaLevelEnum.LOW));

return agendaList