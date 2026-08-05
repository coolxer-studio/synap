package com.coolxer.model.fact;

import com.coolxer.config.CheckerConfig;
import com.coolxer.utils.JacksonUtils;
import com.coolxer.utils.StringConvertUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**

 */

@Slf4j
public class FactBuilder {

    public static AbstractFact buildFact(Header header, Common common, Map<String,Object> factMap){
        String factType = (String)factMap.get("type");
        String factPlatform = common.getPlatform();
        String factClassName = "com.coolxer.model.fact."+factPlatform+"."+StringUtils.capitalize(factPlatform)+StringUtils.replaceOnce(factType,"Data","Fact");
        try {
            return (AbstractFact) Class.forName(factClassName).getDeclaredConstructor(String.class,Header.class,Common.class, Map.class).newInstance(factType,header,common,factMap);
        } catch (Exception e) {
            log.error("构建fact失败, factClassName:{}, factMap:{}", factClassName, factMap, e);
        }
        return null;
    }

    public static Header buildHeader(String headerCode){
        return new Header(headerCode);
    }

    public static Common buildCommon(List<String> commonList){
        if (commonList == null || commonList.size() != CheckerConfig.getInstance().getCommonConfig().size()) {
            throw new IllegalArgumentException("common must contain exactly 24 fields");
        }
        // 创建一个新的 List<Object> 并将字符串元素添加到其中
        List<Object> commonObjectList = new ArrayList<>();
        for (String str : commonList) {
            commonObjectList.add(str);
        }
        Map<String,Object> commonMap = mergeValueAndKey(CheckerConfig.getInstance().getCommonConfig(),commonObjectList);
        Common common = null;
        try {
            common = JacksonUtils.toObject(JacksonUtils.toJson(commonMap), Common.class);
        } catch (JsonProcessingException e) {
            log.error("解析common对象失败", e);
        }
        return common;
    }

    /**
     * 将两个JsonArray合并成一个JsonObject，其中一个为key，一个为value
     *
     * @param keyList   key对象数组
     * @param valueArray value对象数组
     * @return 合并后的JsonObject
     */
    public static Map<String,Object> mergeValueAndKey(List<String> keyList, List<Object> valueArray) {
        if (keyList == null || valueArray == null || valueArray.size() < keyList.size()) {
            throw new IllegalArgumentException("fact values do not match checker configuration");
        }
        Map<String,Object> mergedData = new LinkedHashMap<String,Object>();
        for (int i = 0; i < keyList.size(); i++) {
            String key = keyList.get(i);
            if(key.contains(".")){
                key = StringConvertUtils.underscoreToCamel(key.replaceAll("\\.","_"),true);
            }
            mergedData.put(key, valueArray.get(i));
        }
        return mergedData;
    }

}
