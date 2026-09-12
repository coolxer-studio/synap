package com.coolxer;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WechatFactContractTest {
    @Test
    void buildsEveryWechatUploadFactFromTheV1Contract() throws Exception {
        Map<String, Object> checker = JacksonUtils.toMap(Files.readString(
                Path.of("deploy/open_config/checker_config/wechat.json")));
        CheckerConfigService.platformConfigMap.put("wechat", checker);
        Common common = FactBuilder.buildCommon(new ArrayList<>(Arrays.asList("user", "a-b-c-d-e", "100", "1.0.0",
                "1", "app", "wx-test", "1.0.0", "wechat", "Apple", "phone", "iOS", "17", "wifi", "", "",
                "0", "0", "", "", "", "", "", "2026-08-05T10:00:00Z")));
        Header header = FactBuilder.buildHeader("a-b-c-d-e-wechat-1-1.0.0");

        List<String> mapTypes = List.of("SelfAppData", "RuntimeData", "IntegrityData", "SessionData",
                "BehaviorData", "ActionData", "NetworkData", "LocationData", "DebugData");
        for (String type : mapTypes) {
            Map<String, Object> value = base(type);
            value.put("data", new LinkedHashMap<String, Object>());
            AbstractFact fact = FactBuilder.buildFact(header, common, value);
            assertThat(fact).as(type).isNotNull();
            assertThat(fact.getClass().getSimpleName()).isEqualTo("Wechat" + type.replace("Data", "Fact"));
        }

        Map<String, Object> error = base("ErrorData");
        error.putAll(Map.of("category", "app_error", "message", "masked", "source", "/page", "fingerprint", "f-1", "count", 5));
        assertThat(FactBuilder.buildFact(header, common, error)).isNotNull();

        Map<String, Object> user = base("UserData");
        user.put("userId", "opaque"); user.put("data", Map.of("scene", "test"));
        assertThat(FactBuilder.buildFact(header, common, user)).isNotNull();

        Map<String, Object> device = base("DeviceData");
        device.put("device", Arrays.asList("", "", 0, "Apple", "phone", "iOS 17", "ios", "", "2048"));
        device.put("screen", Arrays.asList(3, 800, 400, 760, 400, 40, 0));
        device.put("systemSetting", Arrays.asList(false, "portrait", false, true));
        device.put("appBaseInfo", Arrays.asList("2.25.3", false, "zh_CN", "8.0", 1, 16));
        device.put("appAuthorizeSetting", Arrays.asList("", "", "", "", "", "", "", "", "", "", ""));
        device.put("battery", Arrays.asList("80", false));
        device.put("uuid", List.of("a-b-c-d-e"));
        device.put("capabilities", Map.of("permission", "disabled"));
        assertThat(FactBuilder.buildFact(header, common, device)).isNotNull();
    }

    private Map<String, Object> base(String type) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        value.put("buildDate", "2026-08-05T10:00:00Z");
        value.put("messageId", "message-1");
        value.put("sequence", 1);
        value.put("observedAt", System.currentTimeMillis());
        return value;
    }
}
