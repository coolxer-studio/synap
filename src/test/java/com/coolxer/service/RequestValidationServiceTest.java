package com.coolxer.service;

import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.RequestVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestValidationServiceTest {
    @Test
    void validatesOriginFetchMetadataAndHeaderCommonBinding() {
        RequestValidationService service = new RequestValidationService("https://*.example.test", "", false, 262144, 100, 8192);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getHeader("Origin")).thenReturn("https://app.example.test");
        when(servlet.getHeader("Referer")).thenReturn("https://app.example.test/login?token=secret");
        when(servlet.getHeader("Sec-Fetch-Mode")).thenReturn("cors");
        when(servlet.getHeader("Sec-Fetch-Dest")).thenReturn("empty");
        when(servlet.getHeader("Sec-Fetch-Site")).thenReturn("cross-site");
        assertNull(service.validateEnvelope("a-b-c-d-e-h5-1-1.3.0", request(), servlet, true));
        assertEquals("header and common are inconsistent",
                service.validateEnvelope("x-b-c-d-e-h5-1-1.3.0", request(), servlet, true));
    }

    @Test
    void rejectsExcessiveNestingAsPermanentSchemaError() {
        RequestValidationService service = new RequestValidationService("", "", true, 262144, 100, 8192);
        RequestVO request = request(); Object nested = "value";
        for (int index = 0; index < 8; index++) nested = Map.of("next", nested);
        request.getData().get(0).put("data", nested);
        assertEquals("fact exceeds structural limits", service.validateEnvelope("a-b-c-d-e-h5-1-1.3.0", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void rejectsAnyProtocolVersionOtherThanOne() {
        RequestValidationService service = new RequestValidationService("", "", true, 262144, 100, 8192);
        RequestVO request = request();
        request.getMeta().setProtocolVersion(2);
        assertEquals("invalid H5 batch metadata",
                service.validateEnvelope("a-b-c-d-e-h5-1-1.3.0", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void validatesWechatMetadataWithoutBrowserOriginRules() {
        RequestValidationService service = new RequestValidationService("", "", false, 262144, 100, 8192);
        RequestVO request = request();
        request.getCommon().set(6, "wx-test-app");
        request.getCommon().set(8, "wechat");
        assertNull(service.validateEnvelope("a-b-c-d-e-wechat-1-1.3.0", request, mock(HttpServletRequest.class), true));
        request.setMeta(null);
        assertEquals("WeChat metadata is required",
                service.validateEnvelope("a-b-c-d-e-wechat-1-1.3.0", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void validatesTrackedHostWithoutBrowserOrWechatRules() {
        RequestValidationService service = new RequestValidationService("", "", false, 262144, 100, 8192);
        RequestVO request = request();
        request.getCommon().set(3, "2.0.0");
        request.getCommon().set(6, "agent-host");
        request.getCommon().set(8, "host");
        request.getMeta().setAppKeyId("1");
        request.getMeta().setDetectorVersion("2.0.0");
        request.getData().get(0).put("type", "UserData");
        assertNull(service.validateEnvelope("a-b-c-d-e-host-1-2.0.0", request,
                mock(HttpServletRequest.class), true));
        request.setMeta(null);
        assertEquals("Host metadata is required", service.validateEnvelope(
                "a-b-c-d-e-host-1-2.0.0", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void requiresProtocolMetadataAndAllowsIosFactTypes() {
        RequestValidationService service = new RequestValidationService("", "", false, 262144, 100, 8192);
        RequestVO request = request();
        request.getCommon().set(3, "1.0.0.220519");
        request.getCommon().set(8, "ios");
        request.getMeta().setAppKeyId("synap-ios-dev");
        request.getMeta().setDetectorVersion("1.0.0.220519");
        request.getData().get(0).put("type", "DeviceData");

        assertNull(service.validateEnvelope("a-b-c-d-e-ios-1-1.0.0.220519", request,
                mock(HttpServletRequest.class), true));
        request.setMeta(null);
        assertEquals("iOS metadata is required", service.validateEnvelope(
                "a-b-c-d-e-ios-1-1.0.0.220519", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void enforcesWechatAppIdAllowlistByAppKeyId() {
        RequestValidationService service = new RequestValidationService("", "", true, 262144, 100, 8192);
        ReflectionTestUtils.setField(service, "configuredWechatAppIds",
                "0123456789abcdef0123456789abcdef=wx-allowed|wx-second");
        ReflectionTestUtils.setField(service, "requireWechatAppId", true);
        RequestVO request = request();
        request.getCommon().set(6, "wx-denied");
        request.getCommon().set(8, "wechat");
        assertEquals("WeChat appId is not allowed",
                service.validateEnvelope("a-b-c-d-e-wechat-1-1.3.0", request, mock(HttpServletRequest.class), true));
        request.getCommon().set(6, "wx-allowed");
        assertNull(service.validateEnvelope("a-b-c-d-e-wechat-1-1.3.0", request, mock(HttpServletRequest.class), true));
    }

    @Test
    void validatesCompleteStartTrackingAndRejectsPartialMetadata() {
        RequestValidationService service = new RequestValidationService("", "", true, 262144, 100, 8192);
        RequestVO request = request();
        request.getMeta().setMessageId("start-message-1");
        request.getMeta().setObservedAt(System.currentTimeMillis());
        request.getMeta().setSequence(1L);
        assertNull(service.validateEnvelope("a-b-c-d-e-h5-1-1.3.0", request,
                mock(HttpServletRequest.class), false));

        request.getMeta().setSequence(2L);
        assertEquals("invalid start tracking metadata", service.validateEnvelope(
                "a-b-c-d-e-h5-1-1.3.0", request, mock(HttpServletRequest.class), false));
        request.getMeta().setSequence(1L);
        request.getMeta().setMessageId(null);
        assertEquals("invalid start tracking metadata", service.validateEnvelope(
                "a-b-c-d-e-h5-1-1.3.0", request, mock(HttpServletRequest.class), false));
    }

    @Test
    void canRequireStartTrackingByPlatformAfterCompatibilityRollout() {
        RequestValidationService service = new RequestValidationService("", "", true, 262144, 100, 8192);
        ReflectionTestUtils.setField(service, "requiredStartTrackingPlatforms", "h5,wechat");
        RequestVO request = request();
        request.getMeta().setBatchId(null);
        request.getMeta().setSessionId(null);
        request.getMeta().setAttempt(null);
        request.getMeta().setCreatedAt(null);
        assertEquals("start tracking metadata is required", service.validateEnvelope(
                "a-b-c-d-e-h5-1-1.3.0", request, mock(HttpServletRequest.class), false));
    }

    private RequestVO request() {
        Map<String, Object> item = new LinkedHashMap<>(); item.put("type", "UserData"); item.put("messageId", "message-1");
        item.put("sequence", 1); item.put("observedAt", System.currentTimeMillis()); item.put("data", Map.of("scene", "test"));
        RequestMeta meta = new RequestMeta(); meta.setProtocolVersion(1); meta.setBatchId("batch-1"); meta.setSessionId("100");
        meta.setAttempt(1); meta.setCreatedAt(System.currentTimeMillis()); meta.setDetectorVersion("1.3.0");
        meta.setAppKeyId("0123456789abcdef0123456789abcdef");
        RequestVO request = new RequestVO(); request.setMeta(meta); request.setData(new ArrayList<>(java.util.List.of(item)));
        request.setCommon(new ArrayList<>(Arrays.asList("user", "a-b-c-d-e", "100", "1.3.0", "1", "app", "app.example.test", "-",
                "h5", "", "", "", "", "", "", "", "0", "0", "", "", "", "", "", "2026-08-05 10:00:00")));
        return request;
    }
}
