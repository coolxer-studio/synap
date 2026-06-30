package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidNativeFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidNativeFact androidNativeFact = (AndroidNativeFact) fact;
agendaList.add(new Agenda("应用崩溃（Native）", androidNativeFact.type, AgendaLevelEnum.LOW));

return agendaList