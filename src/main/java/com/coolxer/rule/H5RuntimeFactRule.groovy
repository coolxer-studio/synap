package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5RuntimeFact

// TODO "custom": ["format", "runType", "getSpider", "unknownFunctions", "infoModify"]

H5RuntimeFact h5RuntimeFact = (H5RuntimeFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>()
Map<String,Object> custom = h5RuntimeFact.getCustom()
int format = custom.getOrDefault("format",0)
if(format>0){
    agendaList.add(new Agenda("格式化",format.toString(), AgendaLevelEnum.HIGH));
}
ArrayList<Integer> getSpider = (ArrayList<Integer>)custom.getOrDefault("getSpider",new ArrayList<Integer>())
if(getSpider.contains(1)){
    agendaList.add(new Agenda("爬虫",getSpider.join(","),AgendaLevelEnum.HIGH));
}
return agendaList