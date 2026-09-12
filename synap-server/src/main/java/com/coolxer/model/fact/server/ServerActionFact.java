package com.coolxer.model.fact.server;
import com.coolxer.model.fact.*; import java.util.Map;
public class ServerActionFact extends ServerStructuredFact { public ServerActionFact(String type, Header header, Common common, Map<String,Object> factMap) { super(type, header, common, factMap); } }
