import com.coolxer.model.agenda.*
def p=fact.getPayload(); def level=String.valueOf(p.action)=='blocked'?AgendaLevelEnum.HIGH:String.valueOf(p.confidence)=='high'?AgendaLevelEnum.MEDIUM:AgendaLevelEnum.LOW
return new ArrayList<Agenda>([new Agenda('server-dangerous-call-'+String.valueOf(p.sinkType).toLowerCase(),String.valueOf(p.ruleId)+':'+String.valueOf(p.assetId),level)])
