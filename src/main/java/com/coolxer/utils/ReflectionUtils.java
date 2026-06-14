package com.coolxer.utils;

import java.lang.reflect.Field;

/**
 * 反射工具类
 */
public class ReflectionUtils {

    /**
     * 通过反射获取对象中的属性值
     *
     * @param obj       目标对象
     * @param fieldName 属性名称
     * @return 属性值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            // 如果当前类找不到字段，尝试从父类中查找
            Class<?> superClass = obj.getClass().getSuperclass();
            while (superClass != null && !superClass.equals(Object.class)) {
                try {
                    Field field = superClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    superClass = superClass.getSuperclass();
                }
            }
            throw new RuntimeException("字段不存在: " + fieldName, e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("无法访问字段: " + fieldName, e);
        }
    }

    /**
     * 通过反射获取对象中的属性值（返回字符串类型）
     *
     * @param obj       目标对象
     * @param fieldName 属性名称
     * @return 属性值的字符串表示
     */
    public static String getFieldValueAsString(Object obj, String fieldName) {
        Object value = getFieldValue(obj, fieldName);
        return value == null ? null : value.toString();
    }
}
