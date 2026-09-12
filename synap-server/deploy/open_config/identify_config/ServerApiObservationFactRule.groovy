import com.coolxer.model.agenda.*
def a=new ArrayList<Agenda>()
(fact.getPayload().observations?:[]).each{ o -> if((o.status5xx?:0) as long>=10) a.add(new Agenda('server-api-error-burst',String.valueOf(o.assetId),AgendaLevelEnum.MEDIUM)); if((o.denied?:0) as long>=20) a.add(new Agenda('server-api-auth-denial-burst',String.valueOf(o.assetId),AgendaLevelEnum.MEDIUM)) }
return a
