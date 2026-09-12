package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidAppFact

ArrayList<Agenda> agendaList = new ArrayList<>()
AndroidAppFact androidAppFact = (AndroidAppFact)fact;


for (i in 0..<androidAppFact.getInstalled().size()) {
    AndroidAppFact.App app = androidAppFact.getInstalled().get(i);
    if(app.getPackageName().startsWith("com.topjohnwu.magisk")){
        agendaList.add(new Agenda("风险应用","magisk", AgendaLevelEnum.LOW));
    }
}

ArrayList<String> xposedEntry = androidAppFact.getEntry().getOrDefault("assets/xposed_init",new ArrayList());
if(xposedEntry.size()>0){
    agendaList.add(new Agenda("xposedModel",xposedEntry.join(","),AgendaLevelEnum.HIGH));
}
return agendaList