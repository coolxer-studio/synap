package com.coolxer;

import com.coolxer.model.punish.Punish;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.*;

import static java.nio.file.Paths.get;

@Slf4j
public class FactTest {

    @Data
    class aa {
        private int testAbbb;
    }
    @Test
    public void exportGetter(){
        String data = "{\"device\":[\"type\",\"name\",\"simulator\",\"systemName\",\"systemVersion\",\"buildVersion\",\"kernelVersion\",\"modelCode\",\"countryCode\",\"language\",\"timeZone\",\"currency\",\"activationTime\",\"startupTime\",\"fileCreationTime\",\"fileModificationTime\",\"wireConnected\"],\"screen\":[\"width\",\"height\",\"brightness\"],\"cpu\":[\"totalCore\",\"usedCore\",\"type\",\"abi\"],\"battery\":[\"capacity\",\"level\",\"charging\",\"charged\"],\"uuid\":[\"CFUUID\",\"IDFA\",\"IDFV\",\"GUID\"],\"network\":[\"simCount\",\"cellType\",\"operators\",\"country\",\"mcc\",\"iso\",\"mnc\",\"allowsVoip\",\"lanIp\",\"mask\",\"broadcast\",\"routing\",\"signal\",\"type\"],\"storage\":[\"totalDisk\",\"usedDisk\",\"totalRam\",\"usedRam\"],\"sysctl\":[\"hw.model\"]}";
        Map<String,Object> dataJson = null;
        try {
            dataJson = JacksonUtils.toMap(data);
        } catch (JsonProcessingException e) {
            log.error("解析data JSON失败", e);
        }
        for (String key:dataJson.keySet()) {
            ArrayList jsonArray = (ArrayList) dataJson.get(key);
            System.out.println("@Data\n" +
                    "public class "+StringUtils.capitalize(key)+"{");
            for (Object field:jsonArray) {
                System.out.println("private String "+ (String)field+";");
            }
            System.out.println("}");
        }

    }


    @Test
    public void testJson(){
        // 创建 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        // 原始 JSON 字符串
        String json = "{\"someKey\":42,\"anotherKey\":\"hello\"}";
        String convertedJson = null;

        Map<String,Object> map1 = new HashMap<>();
        map1.put("testOne",1);
        map1.put("testTwo",2);
        Map<String,Object> map2 = new HashMap<>();
        map2.put("testMap",map1);

        try {
            // 将 JSON 字符串解析为 JsonNode
            ObjectNode jsonNode = objectMapper.readValue(json, ObjectNode.class);

            // 设置自定义命名策略，将驼峰转为蛇形
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            // 将 JsonNode 转换为 JSON 字符串
            convertedJson = objectMapper.writeValueAsString(map2);
        } catch (JsonProcessingException e) {
            log.error("JSON转换失败", e);
        }

        // 打印转换后的 JSON 字符串
        try {
            aa a = new aa();
            a.testAbbb=1;
            System.out.println(JacksonUtils.toJson(a));
        } catch (JsonProcessingException e) {
            log.error("Jackson序列化失败", e);
        }

        // 创建 ObjectMapper
        objectMapper = new ObjectMapper();

        // 原始的 Map 对象
        Map<String, Object> originalMap = new HashMap<>();
        originalMap.put("someKey", 42);
        originalMap.put("anotherKey", "hello");

        // 设置自定义命名策略，将驼峰转为蛇形
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // 将 Map 转换为 JSON 字符串
        try {
            convertedJson = objectMapper.writeValueAsString(originalMap);
        } catch (JsonProcessingException e) {
            log.error("Map转JSON失败", e);
        }

        // 打印转换后的 JSON 字符串
        System.out.println(convertedJson);
    }


    @Test
    public void test2(){
        String punishConfigContent = """
                [
                  {"tag":"android-device","sourceRegex":".*","action":{"type":1,"title":"android-device","message":"android-device"}},
                  {"tag":"macos","sourceRegex":".*","action":{"type":1,"title":"运行环境异常","message":"运行在macos上"}},
                  {"tag":"root","sourceRegex":".*","action":{"type":1,"title":"运行环境异常","message":"运行在root/越狱设备上"}}
                ]
                """;
        List<Punish> punishRule = null;
        try {
            punishRule = JacksonUtils.toList(punishConfigContent, new TypeReference<List<Punish>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析punish配置失败", e);
        }
    }
}
