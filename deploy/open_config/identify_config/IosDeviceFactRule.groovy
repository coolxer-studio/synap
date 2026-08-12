package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosDeviceFact

def simulator(IosDeviceFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getDevice().getSimulator()){
        agendaList.add(new Agenda("simulator","宏定义:TARGET_IPHONE_SIMULATOR = 1", AgendaLevelEnum.LOW))
    }
    return agendaList
}

def macosWithDeviceInfo(IosDeviceFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getCpu().getType().startsWith("arm64") && fact.getSysctl().get("hwModel").startsWith("Mac")){
        agendaList.add(new Agenda("macos","Model:"+fact.getSysctl().getHwModel(),AgendaLevelEnum.LOW))
    }
    return agendaList
}

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.addAll(macosWithDeviceInfo(fact))
agendaList.addAll(simulator(fact))

return agendaList