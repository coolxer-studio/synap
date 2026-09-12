package com.coolxer.service.impl;

import com.coolxer.model.cross.APIConfig;
import com.coolxer.model.fact.Common;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossServiceImplTest {

    private RestTemplate restTemplate;
    private ApiConfigLoadServiceImpl configService;
    private CrossServiceImpl crossService;
    private Common common;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        configService = mock(ApiConfigLoadServiceImpl.class);
        crossService = new CrossServiceImpl(restTemplate, configService);
        common = new Common();
        common.setGuid("trace-1");
        common.setPlatform("android");
        common.setAppId(100);
    }

    @Test
    void successfulCallReturnsResponse() {
        APIConfig config = apiConfig("asset", "资产服务", "http://asset.test/device");
        when(configService.getConfigById("asset")).thenReturn(config);
        when(restTemplate.getForObject("http://asset.test/device", String.class))
                .thenReturn("{\"status\":0}");

        assertThat(crossService.rpc("asset", null, common)).isEqualTo("{\"status\":0}");
    }

    @Test
    void missingConfigurationPreservesException() {
        when(configService.getConfigById("missing")).thenReturn(null);

        assertThatThrownBy(() -> crossService.rpc("missing", null, common))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void transportFailurePreservesException() {
        APIConfig config = apiConfig("asset", "资产服务", "http://asset.test/device");
        when(configService.getConfigById("asset")).thenReturn(config);
        when(restTemplate.getForObject("http://asset.test/device", String.class))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> crossService.rpc("asset", null, common))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("timeout");
    }

    private APIConfig apiConfig(String id, String name, String url) {
        APIConfig config = new APIConfig();
        config.setId(id);
        config.setApiName(name);
        config.setApiUrl(url);
        return config;
    }
}
