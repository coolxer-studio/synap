package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosInjectFact
import org.apache.commons.lang3.StringUtils


def swizzling(ArrayList injectList){
    if(injectList.size() > 0){
        return new Agenda("swizzling", StringUtils.join(injectList), AgendaLevelEnum.HIGH)
    }
}

def inlineHook(ArrayList injectList){
    if(injectList.size() > 0){
        return new Agenda("inlineHook", StringUtils.join(injectList),AgendaLevelEnum.HIGH)
    }
}

def fishhook(ArrayList injectList){
    if(injectList.size() > 0){
        return new Agenda("fishhook", StringUtils.join(injectList),AgendaLevelEnum.HIGH)
    }
}

ArrayList<Agenda> agendaList = new ArrayList<>()
agenda = swizzling(((IosInjectFact)fact).getSwizzling())
if(null != agenda){
    agendaList.add(agenda)
}
agenda = inlineHook(((IosInjectFact)fact).getInlineHook())
if(null != agenda){
    agendaList.add(agenda)
}
agenda = fishhook(((IosInjectFact)fact).getFishhook())
if(null != agenda){
    agendaList.add(agenda)
}

return agendaList