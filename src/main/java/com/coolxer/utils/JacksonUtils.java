package com.coolxer.utils;

import com.coolxer.config.JacksonConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * jackson 工具类
 * 提供JSON字符串与Java对象之间的转换功能
 * 使用JacksonConfig中配置的两个ObjectMapper实例：
 * - DESERIALIZATION_OBJECT_MAPPER: 用于反序列化，采用驼峰命名策略
 * - SERIALIZATION_OBJECT_MAPPER: 用于序列化，采用蛇形命名策略
 */
@Slf4j
public class JacksonUtils {

    /**
     * 私有构造函数，防止外部实例化
     * 此类只包含静态方法，不需要创建实例
     */
    private JacksonUtils() {

    }

    /**
     * 将JSON字符串转换为指定类型的Java对象
     * 
     * @param content JSON字符串内容
     * @param type 目标对象类型
     * @param <T> 泛型类型参数
     * @return 转换后的Java对象，如果输入为空则返回null
     * @throws JsonProcessingException JSON处理异常
     */
    public static <T> T toObject(String content, Class<T> type) throws JsonProcessingException {
        if (!StringUtils.hasLength(content)) {
            return null;
        }
        return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, type);
    }

    /**
     * 将JSON字符串转换为指定类型的List集合
     * 
     * @param content JSON字符串内容
     * @param typeReference 类型引用，用于指定List中元素的具体类型
     * @param <T> 泛型类型参数
     * @return 转换后的List集合，如果输入为空则返回空列表
     * @throws JsonProcessingException JSON处理异常
     */
    public static <T> List<T> toList(String content, TypeReference<List<T>> typeReference) throws JsonProcessingException {
        if (!StringUtils.hasLength(content)) {
            return Collections.emptyList();
        }
        return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, typeReference);
    }

    /**
     * 将JSON字符串转换为Object类型的List集合
     * 
     * @param content JSON字符串内容
     * @return 转换后的List<Object>集合
     * @throws JsonProcessingException JSON处理异常
     */
    public static List<Object> toList(String content) throws JsonProcessingException {
        return toList(content, new TypeReference<List<Object>>() {});
    }

    /**
     * 将JSON字符串转换为String类型的List集合
     * 
     * @param content JSON字符串内容
     * @return 转换后的List<String>集合
     * @throws JsonProcessingException JSON处理异常
     */
    public static List<String> toStringList(String content) throws JsonProcessingException {
        return toList(content, new TypeReference<List<String>>() {});
    }

    /**
     * 将JSON字符串转换为指定类型的Map集合
     * 
     * @param content JSON字符串内容
     * @param typeReference 类型引用，用于指定Map的键值对类型
     * @param <K> Map键的泛型类型
     * @param <V> Map值的泛型类型
     * @return 转换后的Map集合，如果输入为空则返回空Map
     * @throws JsonProcessingException JSON处理异常
     */
    public static <K, V> Map<K, V> toMap(String content, TypeReference<Map<K, V>> typeReference) throws JsonProcessingException {
        if (!StringUtils.hasLength(content)) {
            return Collections.emptyMap();
        }
        return JacksonConfig.DESERIALIZATION_OBJECT_MAPPER.readValue(content, typeReference);
    }

    /**
     * 将JSON字符串转换为String-Object类型的Map集合
     * 
     * @param context JSON字符串内容
     * @return 转换后的Map<String, Object>集合
     * @throws JsonProcessingException JSON处理异常
     */
    public static Map<String,Object> toMap(String context) throws JsonProcessingException {
        return toMap(context,new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将Java对象转换为JSON字符串
     * 使用SERIALIZATION_OBJECT_MAPPER进行序列化，采用蛇形命名策略
     * 
     * @param object 要转换的Java对象
     * @return 转换后的JSON字符串，如果对象为null则返回null
     * @throws JsonProcessingException JSON处理异常
     */
    public static String toJson(Object object) throws JsonProcessingException {
        if (Objects.isNull(object)) {
            return null;
        }
        return JacksonConfig.SERIALIZATION_OBJECT_MAPPER.writeValueAsString(object);
    }


}
