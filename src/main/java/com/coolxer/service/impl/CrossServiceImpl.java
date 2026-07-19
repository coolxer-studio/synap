package com.coolxer.service.impl;

import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.cross.APIConfig;
import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import com.coolxer.service.CrossService;
import com.coolxer.utils.ReflectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class CrossServiceImpl implements CrossService {

    private final RestTemplate restTemplate;
    private final ApiConfigLoadServiceImpl apiConfigLoadServiceImpl;
    private final BusinessServiceReporter businessServiceReporter;
    
    public CrossServiceImpl(RestTemplate restTemplate,
                            ApiConfigLoadServiceImpl apiConfigLoadServiceImpl,
                            BusinessServiceReporter businessServiceReporter) {
        this.restTemplate = restTemplate;
        this.apiConfigLoadServiceImpl = apiConfigLoadServiceImpl;
        this.businessServiceReporter = businessServiceReporter;
    }
    
    /**
     * 通过反射将模式字符串中的占位符替换为实际值
     *
     * @param pattern 模式字符串，如 "{guid}/device/info/{appId}"
     * @param header Header对象，包含可能的字段值
     * @param common Common对象，包含可能的字段值
     * @return 替换后的完整URL字符串
     */
    private String resolvePattern(String pattern, Header header, Common common) {
        if (pattern == null || pattern.isEmpty()) {
            return pattern;
        }
        
        String result = pattern;
        
        // 查找所有 {xxx} 格式的占位符
        java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = placeholderPattern.matcher(pattern);
        
        while (matcher.find()) {
            String fieldName = matcher.group(1); // 获取占位符内的字段名
            String fieldValue = null;
            
            // 首先尝试从Common对象中获取字段值
            if (common != null) {
                try {
                    fieldValue = ReflectionUtils.getFieldValueAsString(common, fieldName);
                } catch (Exception e) {
                    log.debug("从Common对象获取字段 {} 失败: {}", fieldName, e.getMessage());
                }
            }
            
            // 如果Common中没有找到，尝试从Header对象中获取
            if (fieldValue == null && header != null) {
                try {
                    fieldValue = ReflectionUtils.getFieldValueAsString(header, fieldName);
                } catch (Exception e) {
                    log.debug("从Header对象获取字段 {} 失败: {}", fieldName, e.getMessage());
                }
            }
            
            // 如果找到了对应的字段值，则进行替换
            if (fieldValue != null) {
                result = result.replace("{" + fieldName + "}", fieldValue);
            } else {
                log.warn("未找到字段 {} 的值，保持原样", fieldName);
            }
        }
        
        return result;
    }
    
    @Override
    public Object rpc(String id, Header header, Common common) {
        APIConfig apiConfig = null;
        try {
            // 获取所有配置
            apiConfig = apiConfigLoadServiceImpl.getConfigById(id);

            // 空值检查
            if (apiConfig == null) {
                String errorMsg = String.format("未找到ID为 '%s' 的API配置，请检查配置是否正确加载", id);
                throw new IllegalArgumentException(errorMsg);
            }

            String urlPattern = apiConfig.getApiUrl();
            String requestUrl = resolvePattern(urlPattern, header, common);
            String response = restTemplate.getForObject(requestUrl, String.class);
            log.info("system_log:{},{},{},{},{},{}",common.getAppId(),common.getGuid(),common.getStartId(),common.getPlatform(),common.getUserId(),"查询设备信息");
            return response;
        } catch (RuntimeException e) {
            log.error("跨服务调用失败, apiConfigId:{}", id, e);
            reportCrossServiceFailure(id, apiConfig, common, e);
            throw e;
        }
    }

    private void reportCrossServiceFailure(String id,
                                           APIConfig apiConfig,
                                           Common common,
                                           RuntimeException exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("api_config_id", id);
        if (apiConfig != null && apiConfig.getApiName() != null) {
            data.put("api_name", apiConfig.getApiName());
        }
        if (common != null) {
            data.put("platform", common.getPlatform());
            data.put("app_id", common.getAppId());
        }
        String traceId = common == null ? null : common.getGuid();
        String message = exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
        try {
            businessServiceReporter.reportEvent(
                    "CROSS_SERVICE_CALL_FAILED",
                    BusinessServiceEventSeverity.ERROR,
                    "Synap 跨服务调用失败",
                    message,
                    traceId,
                    data);
        } catch (RuntimeException reporterException) {
            log.warn("提交 Zenvis 跨服务失败事件时发生异常: {}", reporterException.getMessage());
        }
    }
    
}
