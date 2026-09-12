package com.coolxer.synap.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

final class JsonSupport {
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private JsonSupport() {}
    static byte[] bytes(Object value) throws JsonProcessingException { return MAPPER.writeValueAsBytes(value); }
    static String string(Object value) throws JsonProcessingException { return MAPPER.writeValueAsString(value); }
    static <T> T read(byte[] value, Class<T> type) throws IOException { return MAPPER.readValue(value, type); }
    static Map<String, Object> map(byte[] value) throws IOException { return MAPPER.readValue(value, new TypeReference<>() {}); }
}
