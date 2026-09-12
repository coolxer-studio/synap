package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosRuntimeFact

def debugWithPtrace(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getCustom().getPtrace()){
        agendaList.add(new Agenda("debug","ptrace=true", AgendaLevelEnum.HIGH))
    }
    return agendaList

}

def macosWithiOSAppOnMac(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getCustom().getIsiOSAppOnMac()){
        agendaList.add(new Agenda("isiOSAppOnMac","isiOSAppOnMac=true",AgendaLevelEnum.LOW))
    }
    return agendaList

}

def screenShareWithCaptured(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getCustom().getIsCaptured()){
        agendaList.add(new Agenda("isCaptured","isCaptured=true"),AgendaLevelEnum.LOW)
    }
    return agendaList

}

def rootWithStatHook(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getCustom().getStatHook()){
        agendaList.add(new Agenda("root","statHook=true",AgendaLevelEnum.HIGH))
    }
    return agendaList
}

def rootWithImages(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getImages().contains("/Library/MobileSubstrate/MobileSubstrate.dylib")){
        agendaList.add(new Agenda("root","/Library/MobileSubstrate/MobileSubstrate.dylib",AgendaLevelEnum.HIGH))
    }
    return agendaList
}

def frameForFridaWithThread(IosRuntimeFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String threadName : fact.getThread()){
        if(threadName.contains("gum-js-loop")){
            agendaList.add(new Agenda("frida","thread:"+threadName,AgendaLevelEnum.HIGH))
        }
    }
    return agendaList
}


ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.addAll(rootWithStatHook(fact))
agendaList.addAll(rootWithImages(fact))
agendaList.addAll(macosWithiOSAppOnMac(fact))
agendaList.addAll(screenShareWithCaptured(fact))
agendaList.addAll(frameForFridaWithThread(fact))
agendaList.addAll(debugWithPtrace(fact))
return agendaList