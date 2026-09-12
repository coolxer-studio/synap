import com.coolxer.model.agenda.*
def p=fact.getPayload(); def s=String.valueOf(p.severity); def level=s=='critical'||s=='high'?AgendaLevelEnum.HIGH:s=='medium'?AgendaLevelEnum.MEDIUM:AgendaLevelEnum.LOW
return new ArrayList<Agenda>([new Agenda('server-security-event-'+String.valueOf(p.category),String.valueOf(p.name)+':'+String.valueOf(p.assetId),level)])
