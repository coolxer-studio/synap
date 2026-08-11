package com.coolxer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 定义kafka中的topic配置
 *

 */
@Configuration
public class TopicDefine {

    @Value("${kafka.topic.partitions:1}")
    private int partitions;
    @Value("${kafka.topic.replicas:1}")
    private int replicas;

    public static final String TOPIC_IOS_START_FACT = "ios_fact_start";
    @Bean(TOPIC_IOS_START_FACT)
    public NewTopic TopicForIosStart() {
        return TopicBuilder.name(TOPIC_IOS_START_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_DEVICE_FACT = "ios_fact_device";
    @Bean(TOPIC_IOS_DEVICE_FACT)
    public NewTopic TopicForIosDevice() {
        return TopicBuilder.name(TOPIC_IOS_DEVICE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_NET_FACT = "ios_fact_net";
    @Bean(TOPIC_IOS_NET_FACT)
    public NewTopic TopicForIosNet() {
        return TopicBuilder.name(TOPIC_IOS_NET_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_SELF_APP_FACT = "ios_fact_self-app";
    @Bean(TOPIC_IOS_SELF_APP_FACT)
    public NewTopic TopicForIosSelfApp() {
        return TopicBuilder.name(TOPIC_IOS_SELF_APP_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_FILE_FACT = "ios_fact_file";
    @Bean(TOPIC_IOS_FILE_FACT)
    public NewTopic TopicForIosFile() {
        return TopicBuilder.name(TOPIC_IOS_FILE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_RUNTIME_FACT = "ios_fact_runtime";
    @Bean(TOPIC_IOS_RUNTIME_FACT)
    public NewTopic TopicForIosRuntime() {
        return TopicBuilder.name(TOPIC_IOS_RUNTIME_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_APP_FACT = "ios_fact_app";
    @Bean(TOPIC_IOS_APP_FACT)
    public NewTopic TopicForIosApp() {
        return TopicBuilder.name(TOPIC_IOS_APP_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_LOCATION_FACT = "ios_fact_location";
    @Bean(TOPIC_IOS_LOCATION_FACT)
    public NewTopic TopicForIosLocation() {
        return TopicBuilder.name(TOPIC_IOS_LOCATION_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_INJECT_FACT = "ios_fact_inject";
    @Bean(TOPIC_IOS_INJECT_FACT)
    public NewTopic TopicForIosInject() {
        return TopicBuilder.name(TOPIC_IOS_INJECT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_VIEW_FACT = "ios_fact_view";
    @Bean(TOPIC_IOS_VIEW_FACT)
    public NewTopic TopicForIosView() {
        return TopicBuilder.name(TOPIC_IOS_VIEW_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_SIGNAL_FACT = "ios_fact_signal";
    @Bean(TOPIC_IOS_SIGNAL_FACT)
    public NewTopic TopicForIosSignal() {
        return TopicBuilder.name(TOPIC_IOS_SIGNAL_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_OBJC_FACT = "ios_fact_objc";
    @Bean(TOPIC_IOS_OBJC_FACT)
    public NewTopic TopicForIosObjc() {
        return TopicBuilder.name(TOPIC_IOS_OBJC_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_IOS_USER_FACT = "ios_fact_user";
    @Bean(TOPIC_IOS_USER_FACT)
    public NewTopic TopicForIosUser() {
        return TopicBuilder.name(TOPIC_IOS_USER_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_START_FACT = "android_fact_start";
    @Bean(TOPIC_ANDROID_START_FACT)
    public NewTopic TopicForAndroidStart() {
        return TopicBuilder.name(TOPIC_ANDROID_START_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_DEVICE_FACT = "android_fact_device";
    @Bean(TOPIC_ANDROID_DEVICE_FACT)
    public NewTopic TopicForAndroidDevice() {
        return TopicBuilder.name(TOPIC_ANDROID_DEVICE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_NET_FACT = "android_fact_net";
    @Bean(TOPIC_ANDROID_NET_FACT)
    public NewTopic TopicForAndroidNet() {
        return TopicBuilder.name(TOPIC_ANDROID_NET_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_SELF_APP_FACT = "android_fact_self-app";
    @Bean(TOPIC_ANDROID_SELF_APP_FACT)
    public NewTopic TopicForAndroidSelfApp() {
        return TopicBuilder.name(TOPIC_ANDROID_SELF_APP_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_FILE_FACT = "android_fact_file";
    @Bean(TOPIC_ANDROID_FILE_FACT)
    public NewTopic TopicForAndroidFile() {
        return TopicBuilder.name(TOPIC_ANDROID_FILE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_RUNTIME_FACT = "android_fact_runtime";
    @Bean(TOPIC_ANDROID_RUNTIME_FACT)
    public NewTopic TopicForAndroidRuntime() {
        return TopicBuilder.name(TOPIC_ANDROID_RUNTIME_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_APP_FACT = "android_fact_app";
    @Bean(TOPIC_ANDROID_APP_FACT)
    public NewTopic TopicForAndroidApp() {
        return TopicBuilder.name(TOPIC_ANDROID_APP_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_LOCATION_FACT = "android_fact_location";
    @Bean(TOPIC_ANDROID_LOCATION_FACT)
    public NewTopic TopicForAndroidLocation() {
        return TopicBuilder.name(TOPIC_ANDROID_LOCATION_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_INJECT_FACT = "android_fact_inject";
    @Bean(TOPIC_ANDROID_INJECT_FACT)
    public NewTopic TopicForAndroidInject() {
        return TopicBuilder.name(TOPIC_ANDROID_INJECT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_EVENT_FACT = "android_fact_event";
    @Bean(TOPIC_ANDROID_EVENT_FACT)
    public NewTopic TopicForAndroidEvent() {
        return TopicBuilder.name(TOPIC_ANDROID_EVENT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_JAVA_FACT = "android_fact_java";
    @Bean(TOPIC_ANDROID_JAVA_FACT)
    public NewTopic TopicForAndroidJava() {
        return TopicBuilder.name(TOPIC_ANDROID_JAVA_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_NATIVE_FACT = "android_fact_native";
    @Bean(TOPIC_ANDROID_NATIVE_FACT)
    public NewTopic TopicForAndroidNative() {
        return TopicBuilder.name(TOPIC_ANDROID_NATIVE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_ANR_FACT = "android_fact_anr";
    @Bean(TOPIC_ANDROID_ANR_FACT)
    public NewTopic TopicForAndroidAnr() {
        return TopicBuilder.name(TOPIC_ANDROID_ANR_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_ACTIVITY_FACT = "android_fact_activity";
    @Bean(TOPIC_ANDROID_ACTIVITY_FACT)
    public NewTopic TopicForAndroidActivity() {
        return TopicBuilder.name(TOPIC_ANDROID_ACTIVITY_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_PRIVACY_FACT = "android_fact_privacy";
    @Bean(TOPIC_ANDROID_PRIVACY_FACT)
    public NewTopic TopicForAndroidPrivacy() {
        return TopicBuilder.name(TOPIC_ANDROID_PRIVACY_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_URL_FACT = "android_fact_url";
    @Bean(TOPIC_ANDROID_URL_FACT)
    public NewTopic TopicForAndroidUrl() {
        return TopicBuilder.name(TOPIC_ANDROID_URL_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_ANDROID_USER_FACT = "android_fact_user";
    @Bean(TOPIC_ANDROID_USER_FACT)
    public NewTopic TopicForAndroidUser() {
        return TopicBuilder.name(TOPIC_ANDROID_USER_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_START_FACT = "h5_fact_start";
    @Bean(TOPIC_H5_START_FACT)
    public NewTopic TopicForH5Start() {
        return TopicBuilder.name(TOPIC_H5_START_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_DEVICE_FACT = "h5_fact_device";
    @Bean(TOPIC_H5_DEVICE_FACT)
    public NewTopic TopicForH5Device() {
        return TopicBuilder.name(TOPIC_H5_DEVICE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }
    public static final String TOPIC_H5_RUNTIME_FACT = "h5_fact_runtime";
    @Bean(TOPIC_H5_RUNTIME_FACT)
    public NewTopic TopicForH5Runtime() {
        return TopicBuilder.name(TOPIC_H5_RUNTIME_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_LOCATION_FACT = "h5_fact_location";
    @Bean(TOPIC_H5_LOCATION_FACT)
    public NewTopic TopicForH5Location() {
        return TopicBuilder.name(TOPIC_H5_LOCATION_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_SELF_APP_FACT = "h5_fact_self_app";
    @Bean(TOPIC_H5_SELF_APP_FACT)
    public NewTopic TopicForH5SelfApp() {
        return TopicBuilder.name(TOPIC_H5_SELF_APP_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_DEBUG_FACT = "h5_fact_debug";
    @Bean(TOPIC_H5_DEBUG_FACT)
    public NewTopic TopicForH5Debug() {
        return TopicBuilder.name(TOPIC_H5_DEBUG_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_ERROR_FACT = "h5_fact_error";
    @Bean(TOPIC_H5_ERROR_FACT)
    public NewTopic TopicForH5Error() {
        return TopicBuilder.name(TOPIC_H5_ERROR_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_DEV_TOOL_FACT = "h5_fact_dev_tool";
    @Bean(TOPIC_H5_DEV_TOOL_FACT)
    public NewTopic TopicForH5DevTool() {
        return TopicBuilder.name(TOPIC_H5_DEV_TOOL_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_EVENT_FACT = "h5_fact_event";
    @Bean(TOPIC_H5_EVENT_FACT)
    public NewTopic TopicForH5Event() {
        return TopicBuilder.name(TOPIC_H5_EVENT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_USER_FACT = "h5_fact_user";
    @Bean(TOPIC_H5_USER_FACT)
    public NewTopic TopicForH5User() {
        return TopicBuilder.name(TOPIC_H5_USER_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_H5_INTEGRITY_FACT = "h5_fact_integrity";
    @Bean(TOPIC_H5_INTEGRITY_FACT)
    public NewTopic TopicForH5Integrity() {
        return TopicBuilder.name(TOPIC_H5_INTEGRITY_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_H5_BEHAVIOR_FACT = "h5_fact_behavior";
    @Bean(TOPIC_H5_BEHAVIOR_FACT)
    public NewTopic TopicForH5Behavior() {
        return TopicBuilder.name(TOPIC_H5_BEHAVIOR_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_H5_SESSION_FACT = "h5_fact_session";
    @Bean(TOPIC_H5_SESSION_FACT)
    public NewTopic TopicForH5Session() {
        return TopicBuilder.name(TOPIC_H5_SESSION_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_H5_NETWORK_FACT = "h5_fact_network";
    @Bean(TOPIC_H5_NETWORK_FACT)
    public NewTopic TopicForH5Network() {
        return TopicBuilder.name(TOPIC_H5_NETWORK_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_H5_RESOURCE_INTEGRITY_FACT = "h5_fact_resource_integrity";
    @Bean(TOPIC_H5_RESOURCE_INTEGRITY_FACT)
    public NewTopic TopicForH5ResourceIntegrity() {
        return TopicBuilder.name(TOPIC_H5_RESOURCE_INTEGRITY_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_H5_ACTION_FACT = "h5_fact_action";
    @Bean(TOPIC_H5_ACTION_FACT)
    public NewTopic TopicForH5Action() {
        return TopicBuilder.name(TOPIC_H5_ACTION_FACT).partitions(partitions).replicas(replicas).compact().build();
    }

    public static final String TOPIC_HOST_START_FACT = "host_fact_start";
    @Bean(TOPIC_HOST_START_FACT)
    public NewTopic TopicForHostStart() {
        return TopicBuilder.name(TOPIC_HOST_START_FACT)
            .partitions(partitions)
            .replicas(replicas)
            .compact()
            .build();
    }

    public static final String TOPIC_HOST_DEVICE_FACT = "host_fact_device";
    @Bean(TOPIC_HOST_DEVICE_FACT)
    public NewTopic TopicForHostDevice() {
        return TopicBuilder.name(TOPIC_HOST_DEVICE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_HOST_FILE_FACT = "host_fact_file";
    @Bean(TOPIC_HOST_FILE_FACT)
    public NewTopic TopicForHostFile() {
        return TopicBuilder.name(TOPIC_HOST_FILE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_HOST_NET_FACT = "host_fact_net";
    @Bean(TOPIC_HOST_NET_FACT)
    public NewTopic TopicForHostNet() {
        return TopicBuilder.name(TOPIC_HOST_NET_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }


    public static final String TOPIC_HOST_RUNTIME_FACT = "host_fact_runtime";
    @Bean(TOPIC_HOST_RUNTIME_FACT)
    public NewTopic TopicForHostRuntime() {
        return TopicBuilder.name(TOPIC_HOST_RUNTIME_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_HOST_EVENT_FACT = "host_fact_event";
    @Bean(TOPIC_HOST_EVENT_FACT)
    public NewTopic TopicForHostEvent() {
        return TopicBuilder.name(TOPIC_HOST_EVENT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_HOST_AUDIT_FACT = "host_fact_audit";
    @Bean(TOPIC_HOST_AUDIT_FACT)
    public NewTopic TopicForHostAudit() {
        return TopicBuilder.name(TOPIC_HOST_AUDIT_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, "15552000000")
                .build();
    }

    public static final String TOPIC_HOST_USER_FACT = "host_fact_user";
    @Bean(TOPIC_HOST_USER_FACT)
    public NewTopic TopicForHostUser() {
        return TopicBuilder.name(TOPIC_HOST_USER_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_WECHAT_START_FACT = "wechat_fact_start";
    @Bean(TOPIC_WECHAT_START_FACT)
    public NewTopic TopicForWechatStart() {
        return TopicBuilder.name(TOPIC_WECHAT_START_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_WECHAT_DEVICE_FACT = "wechat_fact_device";
    @Bean(TOPIC_WECHAT_DEVICE_FACT)
    public NewTopic TopicForWechatDevice() {
        return TopicBuilder.name(TOPIC_WECHAT_DEVICE_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_WECHAT_USER_FACT = "wechat_fact_user";
    @Bean(TOPIC_WECHAT_USER_FACT)
    public NewTopic TopicForWechatUser() {
        return TopicBuilder.name(TOPIC_WECHAT_USER_FACT)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }

    public static final String TOPIC_WECHAT_SELF_APP_FACT = "wechat_fact_self-app";
    public static final String TOPIC_WECHAT_RUNTIME_FACT = "wechat_fact_runtime";
    public static final String TOPIC_WECHAT_INTEGRITY_FACT = "wechat_fact_integrity";
    public static final String TOPIC_WECHAT_SESSION_FACT = "wechat_fact_session";
    public static final String TOPIC_WECHAT_ERROR_FACT = "wechat_fact_error";
    public static final String TOPIC_WECHAT_BEHAVIOR_FACT = "wechat_fact_behavior";
    public static final String TOPIC_WECHAT_ACTION_FACT = "wechat_fact_action";
    public static final String TOPIC_WECHAT_NETWORK_FACT = "wechat_fact_network";
    public static final String TOPIC_WECHAT_LOCATION_FACT = "wechat_fact_location";
    public static final String TOPIC_WECHAT_DEBUG_FACT = "wechat_fact_debug";

    private NewTopic wechatTopic(String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).compact().build();
    }

    @Bean(TOPIC_WECHAT_SELF_APP_FACT)
    public NewTopic TopicForWechatSelfApp() { return wechatTopic(TOPIC_WECHAT_SELF_APP_FACT); }
    @Bean(TOPIC_WECHAT_RUNTIME_FACT)
    public NewTopic TopicForWechatRuntime() { return wechatTopic(TOPIC_WECHAT_RUNTIME_FACT); }
    @Bean(TOPIC_WECHAT_INTEGRITY_FACT)
    public NewTopic TopicForWechatIntegrity() { return wechatTopic(TOPIC_WECHAT_INTEGRITY_FACT); }
    @Bean(TOPIC_WECHAT_SESSION_FACT)
    public NewTopic TopicForWechatSession() { return wechatTopic(TOPIC_WECHAT_SESSION_FACT); }
    @Bean(TOPIC_WECHAT_ERROR_FACT)
    public NewTopic TopicForWechatError() { return wechatTopic(TOPIC_WECHAT_ERROR_FACT); }
    @Bean(TOPIC_WECHAT_BEHAVIOR_FACT)
    public NewTopic TopicForWechatBehavior() { return wechatTopic(TOPIC_WECHAT_BEHAVIOR_FACT); }
    @Bean(TOPIC_WECHAT_ACTION_FACT)
    public NewTopic TopicForWechatAction() { return wechatTopic(TOPIC_WECHAT_ACTION_FACT); }
    @Bean(TOPIC_WECHAT_NETWORK_FACT)
    public NewTopic TopicForWechatNetwork() { return wechatTopic(TOPIC_WECHAT_NETWORK_FACT); }
    @Bean(TOPIC_WECHAT_LOCATION_FACT)
    public NewTopic TopicForWechatLocation() { return wechatTopic(TOPIC_WECHAT_LOCATION_FACT); }
    @Bean(TOPIC_WECHAT_DEBUG_FACT)
    public NewTopic TopicForWechatDebug() { return wechatTopic(TOPIC_WECHAT_DEBUG_FACT); }
}
