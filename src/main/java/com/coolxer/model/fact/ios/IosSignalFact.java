package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public class IosSignalFact extends AbstractFact {
    private ExceptionInfo exceptionInfo;

    public IosSignalFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type, (String) factMap.get("buildDate"),header, common);
        Map exceptionInfoMap = FactBuilder.mergeValueAndKey(ExceptionInfo.EXCEPTION_KEYS,(List<Object>) factMap.get("exception"));
        try {
            exceptionInfo = JacksonUtils.toObject(JacksonUtils.toJson(exceptionInfoMap),ExceptionInfo.class);
        } catch (JsonProcessingException e) {
            log.error("解析iOS Signal ExceptionInfo对象失败", e);
        }
    }

    @Data
    public static class ExceptionInfo {
        public static List<String> EXCEPTION_KEYS;

        static {
            try {
                EXCEPTION_KEYS = JacksonUtils.toStringList("[\"signal\",\"frames\",\"stack\"]");
            } catch (JsonProcessingException e) {
                log.error("解析iOS Signal EXCEPTION_KEYS配置失败", e);
            }
        }

        private Integer signal;
        private Integer frames;
        private String stack;
    }
}
