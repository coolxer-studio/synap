package com.coolxer.service.impl;

import com.coolxer.model.cross.APIConfig;
import com.coolxer.model.fact.Common;
import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossServiceImplTest {

    private RestTemplate restTemplate;
    private ApiConfigLoadServiceImpl configService;
    private BusinessServiceReporter reporter;
    private CrossServiceImpl crossService;
    private Common common;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        configService = mock(ApiConfigLoadServiceImpl.class);
        reporter = mock(BusinessServiceReporter.class);
        crossService = new CrossServiceImpl(restTemplate, configService, reporter);
        common = new Common();
        common.setGuid("trace-1");
        common.setPlatform("android");
        common.setAppId(100);
    }

    @Test
    void successfulCallDoesNotReportFailure() {
        APIConfig config = apiConfig("asset", "资产服务", "http://asset.test/device");
        when(configService.getConfigById("asset")).thenReturn(config);
        when(restTemplate.getForObject("http://asset.test/device", String.class))
                .thenReturn("{\"status\":0}");

        assertThat(crossService.rpc("asset", null, common)).isEqualTo("{\"status\":0}");
        verify(reporter, never()).reportEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingConfigurationReportsFailureAndPreservesException() {
        when(configService.getConfigById("missing")).thenReturn(null);

        assertThatThrownBy(() -> crossService.rpc("missing", null, common))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");

        verifyFailureEvent("missing");
    }

    @Test
    void transportFailureReportsFailureAndPreservesException() {
        APIConfig config = apiConfig("asset", "资产服务", "http://asset.test/device");
        when(configService.getConfigById("asset")).thenReturn(config);
        when(restTemplate.getForObject("http://asset.test/device", String.class))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> crossService.rpc("asset", null, common))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("timeout");

        verifyFailureEvent("asset");
    }

    @SuppressWarnings("unchecked")
    private void verifyFailureEvent(String expectedId) {
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(reporter).reportEvent(
                eq("CROSS_SERVICE_CALL_FAILED"),
                eq(BusinessServiceEventSeverity.ERROR),
                eq("Synap 跨服务调用失败"),
                any(),
                eq("trace-1"),
                dataCaptor.capture());
        assertThat(dataCaptor.getValue()).containsEntry("api_config_id", expectedId);
    }

    private APIConfig apiConfig(String id, String name, String url) {
        APIConfig config = new APIConfig();
        config.setId(id);
        config.setApiName(name);
        config.setApiUrl(url);
        return config;
    }
}
