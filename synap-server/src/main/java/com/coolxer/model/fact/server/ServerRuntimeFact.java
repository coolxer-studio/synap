package com.coolxer.model.fact.server;
import com.coolxer.model.fact.*; import java.util.Map;
public class ServerRuntimeFact extends ServerStructuredFact { public ServerRuntimeFact(String type, Header header, Common common, Map<String,Object> factMap) { super(type, header, common, factMap); } }
