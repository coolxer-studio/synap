package com.coolxer.model.fact.server;
import com.coolxer.model.fact.*; import java.util.Map;
public class ServerApiObservationFact extends ServerStructuredFact { public ServerApiObservationFact(String type, Header header, Common common, Map<String,Object> factMap) { super(type, header, common, factMap); } }
