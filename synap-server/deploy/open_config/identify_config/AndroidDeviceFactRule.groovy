package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidDeviceFact

ArrayList<Agenda> agendaList = new ArrayList<>()
AndroidDeviceFact androidDeviceFact = (AndroidDeviceFact)fact;
if(androidDeviceFact.getDevice().getModelCode().contains("Pixel")){
    agendaList.add(new Agenda("小众机型",androidDeviceFact.getDevice().getModelCode(), AgendaLevelEnum.LOW));
}
return agendaList