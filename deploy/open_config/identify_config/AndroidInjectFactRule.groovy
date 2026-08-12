package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidInjectFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidInjectFact androidInjectFact = (AndroidInjectFact) fact;
if(androidInjectFact.getLibHook().size()>0){
    agendaList.add(new Agenda("dlopen注入", androidInjectFact.getLibHook().join("\n"), AgendaLevelEnum.HIGH));
}
if(androidInjectFact.getJavaHook().size()>0){
    agendaList.add(new Agenda("java-hook", androidInjectFact.getJavaHook().join("\n"), AgendaLevelEnum.HIGH));
}
if(androidInjectFact.getXposedHook().size()>0){
    agendaList.add(new Agenda("xposed-hook", androidInjectFact.getXposedHook().join("\n"), AgendaLevelEnum.HIGH));
}
if(androidInjectFact.getProxyHook().size()>0){
    agendaList.add(new Agenda("proxy-hook", androidInjectFact.getProxyHook().join("\n"), AgendaLevelEnum.HIGH));
}
return agendaList