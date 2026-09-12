package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidSelfAppFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidSelfAppFact androidSelfAppFact = (AndroidSelfAppFact)fact;

if("123c5efb69c00003c0d9bcb4f6aabf91" != androidSelfAppFact.getBase().getCertMd5()){
    agendaList.add(new Agenda("签名证书异常",androidSelfAppFact.getBase().getCertMd5(), AgendaLevelEnum.HIGH));
}

for (String procString: androidSelfAppFact.procMaps) {
    if(procString.contains("xposed")){
        agendaList.add(new Agenda("xposed注入",procString, AgendaLevelEnum.HIGH));
    }
}

return agendaList