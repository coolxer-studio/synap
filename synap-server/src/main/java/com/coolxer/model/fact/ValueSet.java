package com.coolxer.model.fact;

import java.lang.annotation.*;

/**
 *

 * 注解标记给ArrayList用，初始化的时候定制赋值的判断依据
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ValueSet {

    ValueType valueType() default ValueType.NONE;

}

