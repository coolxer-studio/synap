import com.coolxer.model.agenda.*
def a=new ArrayList<Agenda>()
(fact.getPayload().assets?:[]).each{ asset -> def route=String.valueOf(asset.route)
  if(route==~/(?i).*\/(actuator|swagger|v3\/api-docs|h2-console)(\/.*)?/) a.add(new Agenda('server-api-sensitive-endpoint-exposed',String.valueOf(asset.assetId),AgendaLevelEnum.HIGH))
  if(route.contains('/dynamic/unmapped')) a.add(new Agenda('server-api-dynamic-unmapped',String.valueOf(asset.assetId),AgendaLevelEnum.MEDIUM))
}
return a
