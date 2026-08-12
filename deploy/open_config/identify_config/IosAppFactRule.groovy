package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosAppFact

def riskApp(IosAppFact.App app, String appName){
    if(app.getAppName() == appName){
        return new Agenda("riskApp",app.getAppName(), AgendaLevelEnum.LOW)
    }
}


ArrayList<Agenda> agendaList = new ArrayList<>()
for (IosAppFact.App app in ((IosAppFact)fact).getInstalled()){
    agenda = riskApp(app,"AsTools")
    if(agenda !=null){
        agendaList.add(agenda)
    }
}
return agendaList