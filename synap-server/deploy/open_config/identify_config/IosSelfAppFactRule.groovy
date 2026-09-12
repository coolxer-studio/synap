package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosSelfAppFact
import org.apache.commons.lang3.StringUtils

def simulatorWithName(IosSelfAppFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    String deviceName = fact.getEnvironment().get("SIMULATOR_DEVICE_NAME");
    if(deviceName.length()>0 && !StringUtils.equals(deviceName,"nil")){
        agendaList.add(new Agenda("simulator","SIMULATOR_DEVICE_NAME:"+fact.getEnvironment().get("SIMULATOR_DEVICE_NAME"), AgendaLevelEnum.MEDIUM))
    }
    return agendaList
}

def injectWithInsertLib(IosSelfAppFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    String insertLib = fact.getEnvironment().get("DYLD_INSERT_LIBRARIES")
    if(StringUtils.isNotEmpty(insertLib) && !StringUtils.equals("nil",insertLib)){
        agendaList.add(new Agenda("inject","DYLD_INSERT_LIBRARIES:"+insertLib,AgendaLevelEnum.HIGH))
    }
    return agendaList
}

def rootWithInsertLib(IosSelfAppFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    String insertLib = fact.getEnvironment().get("DYLD_INSERT_LIBRARIES")
    if(StringUtils.isNotEmpty(insertLib) && !StringUtils.equals("nil",insertLib)){
        agendaList.add(new Agenda("ROOT","DYLD_INSERT_LIBRARIES:"+insertLib,AgendaLevelEnum.HIGH))
    }
    return agendaList
}

def frameForOtherWithInsertLib(IosSelfAppFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    String insertLib = fact.getEnvironment().get("DYLD_INSERT_LIBRARIES")
    if(StringUtils.isNotEmpty(insertLib) && !StringUtils.equals("nil",insertLib)){
        agendaList.addAll(new Agenda("frame","DYLD_INSERT_LIBRARIES:"+insertLib,AgendaLevelEnum.HIGH))
    }
    return agendaList
}

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.addAll(rootWithInsertLib(fact))
agendaList.addAll(simulatorWithName(fact))
agendaList.addAll(injectWithInsertLib(fact))
agendaList.addAll(frameForOtherWithInsertLib(fact))

return agendaList