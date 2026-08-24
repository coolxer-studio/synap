package com.coolxer.probe;

import android.content.Context;
import android.util.Log;

import com.coolxer.deviceinfo.CTDeviceInfo;
import com.coolxer.punishment.Reminder;
import com.coolxer.runningcheck.CTRunningCheck;
import com.coolxer.securecommunication.SecureClient;
import com.coolxer.securecommunication.SecureClientConfig;
import com.coolxer.securecommunication.SecureError;
import com.coolxer.securecommunication.SecureRequest;
import com.coolxer.securecommunication.SecureResponse;
import com.coolxer.userbehavior.CTUserBehavior;
import com.coolxer.userbehavior.checker.base.IMonitored;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CTProbe {

    private static final int MAX_UPLOAD_BODY_BYTES = 240 * 1024;

    private static volatile CTProbe INSTANCE = null;
    private static volatile Boolean INSTANCE_STARTED = false;

    private Context context;

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final static String PLATFORM = "android";
    private final static String APP_ID = "1";
    private final static String SDK_VERSION = "1.0.0.220519";
    private static SecureClient secureClient;
    private static JSONArray COMMON;
    private JSONObject startMeta;
    private String appKeyId;
    private long nextMessageSequence = 2;

    private CTProbe(){}

    public static boolean initWithContext(Context context,
            SecureClientConfig securityConfig,
            EnrollmentTokenProvider enrollmentTokenProvider){
        if(INSTANCE != null){
            return false;
        }
        synchronized (CTProbe.class){
            if(INSTANCE == null){
                INSTANCE = new CTProbe();
            }
        }
        INSTANCE.context = context;
        INSTANCE.appKeyId = normalizeAppKeyId(securityConfig.getAppId());
        try {
            secureClient = new SecureClient(context, securityConfig);
            if (enrollmentTokenProvider != null) {
                String token = enrollmentTokenProvider.enrollmentToken();
                if (token != null && !token.isEmpty()) {
                    secureClient.enroll(token);
                }
            }
        } catch (Exception exception) {
            INSTANCE = null;
            Log.e("agent-android", "secure communication initialization failed", exception);
            return false;
        }
        CTUserBehavior.init(context);
//        CTIllegalState.init(context);
        CTDeviceInfo.init(context);
        CTRunningCheck.init(context);
        return true;
    }

    public static CTProbe getInstance(){
        return INSTANCE;
    }

    public void start(){
        // 防止多次start
        if(INSTANCE_STARTED){
            return;
        }
        INSTANCE_STARTED = true;

        new DispatchTask("ct-probe",new Runnable() {
            @Override
            public void run() {
                try {
                    // 启动获取配置
                    JSONObject bodyJson = new JSONObject();
                    bodyJson.put("common",getCommon());
                    bodyJson.put("meta", nextStartMeta());
                    String response = securePost("/attach/start", bodyJson.toString());
                    if(response!=null){
                        JSONObject responseJson = null;
                        responseJson = new JSONObject(response);
                        if(responseJson != null){
                            // 2.1 配置
                            int status = responseJson.optInt("status");
                            Log.i("agent-android", "probe start response status=" + status);
                            if(status == 1){
                                // 2.2 checker
                                JSONObject configjson = responseJson.optJSONObject("config");
                                JSONArray startConfig = configjson.optJSONArray("startConfig");
                                int runFlg = startConfig.optInt(0);
                                if(runFlg == 1){
                                    // 1允许开始检测
                                    // 1.1配置检测项
                                    // 加载配置
                                    CTDeviceInfo.getInstance().loadConfig(configjson.toString());
                                    CTRunningCheck.getInstance().loadConfig(configjson.toString());
                                    CTUserBehavior.getInstance().loadConfig(configjson.toString());
//                                    CTIllegalState.getInstance().loadConfig(configjson.toString());
                                    // 1.2 根据配置启动checker
                                    // 打开日志监测,收集上一次日志
//                                    CTIllegalState.getInstance().startTrack();
//                                    String crashInfo = CTIllegalState.getInstance().check();
//                                    JSONArray crashArray = new JSONArray(crashInfo);
//                                    for (int i = 0; i < crashArray.length(); i++) {
//                                        ThreadSafeQueue.pushMsg(crashArray.optString(i));
//                                    }
                                    // 行为检测
                                    CTUserBehavior.getInstance().registerCallBack(new IMonitored.ICallback() {
                                        @Override
                                        public boolean onMsg(String msg) {
                                            enqueueMessage(msg);
                                            return true;
                                        }
                                    });
                                    CTUserBehavior.getInstance().startActivityTrack();
                                    CTUserBehavior.getInstance().startPrivacyTrack();
                                    // run-check 的内存监控
                                    CTRunningCheck.getInstance().registerEventCallBack(new com.coolxer.runningcheck.checker.base.IMonitored.ICallback() {
                                        @Override
                                        public boolean onMsg(String msg) {
                                            enqueueMessage(msg);
                                            return true;
                                        }
                                    });
                                    CTRunningCheck.getInstance().startEventTrack();
                                    // 1.2 周期任务
                                    new DispatchTask("checker",(long)startConfig.optDouble(1)*1000,(long)startConfig.optDouble(2)*1000,checkerRunnable()).start();
                                    // 1.3 根据配置启动sender
                                    new DispatchTask("sender",(long)startConfig.optDouble(3)*1000,(long)startConfig.optDouble(4)*1000,senderRunnable()).start();
                                }
                            } else {
                                INSTANCE_STARTED = false;
                            }
                        }
                    }else {
                        INSTANCE_STARTED = false;
                        Log.d("agent-android","start failed ,response is:"+response);
                    }

                } catch (Exception e) {
                    INSTANCE_STARTED = false;
                    if (e instanceof SecureError) {
                        SecureError secureError = (SecureError) e;
                        Log.e("agent-android", "secure request failed: code="
                                + secureError.getCode() + ", httpStatus="
                                + secureError.getHttpStatus() + ", traceId="
                                + secureError.getTraceId(), secureError);
                    } else {
                        Log.e("agent-android", "probe start failed", e);
                    }
                }
            }
        }).start();
    }

    private Runnable checkerRunnable(){
        return new Runnable() {
            @Override
            public void run() {
                enqueueMessage(CTDeviceInfo.getInstance().deviceInfo());
                enqueueMessage(CTRunningCheck.getInstance().getAppInfo());
                enqueueMessage(CTRunningCheck.getInstance().getFileInfo());
                enqueueMessage(CTRunningCheck.getInstance().getInjectInfo());
                enqueueMessage(CTRunningCheck.getInstance().getLocationInfo());
                enqueueMessage(CTRunningCheck.getInstance().getNetInfo());
                enqueueMessage(CTRunningCheck.getInstance().getRuntimeInfo());
                enqueueMessage(CTRunningCheck.getInstance().getSelfAppInfo());
            }
        };
    }

    private Runnable senderRunnable(){
        return new Runnable() {
            @Override
            public void run() {
                ArrayList<String> drainedMessages = ThreadSafeQueue.pullMsg();
                if (drainedMessages.isEmpty()) {
                    return;
                }

                ArrayList<String> validMessages = new ArrayList<>();
                for (String message : drainedMessages) {
                    try {
                        validMessages.add(new JSONObject(message).toString());
                    } catch (JSONException exception) {
                        Log.e("agent-android", "discarding invalid queued JSON", exception);
                    }
                }
                UploadBatcher.Result result = UploadBatcher.split(
                        getCommon().toString(), validMessages, MAX_UPLOAD_BODY_BYTES,
                        index -> nextUploadMeta());
                for (String ignored : result.getOversizedMessages()) {
                    Log.e("agent-android", "discarding a single upload item larger than "
                            + MAX_UPLOAD_BODY_BYTES + " bytes");
                }

                ArrayList<String> retryMessages = new ArrayList<>();
                for (int index = 0; index < result.getBatches().size(); index++) {
                    UploadBatcher.Batch batch = result.getBatches().get(index);
                    try {
                        String response = securePost("/attach/upload", batch.getBody());
                        JSONObject responseJson = new JSONObject(response);
                        if (responseJson.optInt("status") != 1) {
                            retryMessages.addAll(messagesFrom(result.getBatches(), index));
                            ThreadSafeQueue.pushFront(retryMessages);
                            Log.e("agent-android", "upload batch was rejected by the server");
                            return;
                        }
                        retryMessages.addAll(retryableMessages(responseJson, batch));
                        handlePunishments(responseJson.optJSONArray("punish"));
                        Log.i("agent-android", "uploaded batch " + (index + 1) + "/"
                                + result.getBatches().size() + " with "
                                + batch.getMessages().size() + " items");
                    } catch (Exception exception) {
                        retryMessages.addAll(messagesFrom(result.getBatches(), index));
                        ThreadSafeQueue.pushFront(retryMessages);
                        logSecureFailure("upload batch failed", exception);
                        return;
                    }
                }
                ThreadSafeQueue.pushFront(retryMessages);
            }
        };
    }

    private ArrayList<String> messagesFrom(List<UploadBatcher.Batch> batches, int startIndex) {
        ArrayList<String> retryMessages = new ArrayList<>();
        for (int index = startIndex; index < batches.size(); index++) {
            retryMessages.addAll(batches.get(index).getMessages());
        }
        return retryMessages;
    }

    private List<String> retryableMessages(JSONObject responseJson, UploadBatcher.Batch batch)
            throws JSONException {
        JSONObject ack = responseJson.optJSONObject("ack");
        // Keep compatibility with an older server during a rolling upgrade.
        if (ack == null) {
            return Collections.emptyList();
        }
        if (!batch.getBatchId().equals(ack.optString("batchId"))) {
            throw new JSONException("upload ACK batchId does not match request");
        }

        Set<String> completed = new HashSet<>();
        addStrings(completed, ack.optJSONArray("acceptedMessageIds"));
        addStrings(completed, ack.optJSONArray("duplicateMessageIds"));
        Set<String> retryable = new HashSet<>();
        Set<String> permanentlyRejected = new HashSet<>();
        JSONArray rejected = ack.optJSONArray("rejectedItems");
        if (rejected != null) {
            for (int index = 0; index < rejected.length(); index++) {
                JSONObject item = rejected.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String messageId = item.optString("messageId");
                if (item.optBoolean("retryable")) {
                    retryable.add(messageId);
                } else {
                    permanentlyRejected.add(messageId);
                    Log.e("agent-android", "server permanently rejected message " + messageId
                            + ": " + item.optString("reason"));
                }
            }
        }

        ArrayList<String> messages = new ArrayList<>();
        for (String message : batch.getMessages()) {
            String messageId = new JSONObject(message).optString("messageId");
            if (retryable.contains(messageId)
                    || (!completed.contains(messageId) && !permanentlyRejected.contains(messageId))) {
                messages.add(message);
            }
        }
        return messages;
    }

    private void addStrings(Set<String> target, JSONArray values) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index);
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private void handlePunishments(JSONArray punishArray) {
        if (punishArray == null) {
            return;
        }
        for (int index = 0; index < punishArray.length(); index++) {
            JSONArray punish = punishArray.optJSONArray(index);
            if (punish != null && punish.optInt(0) == 1) {
                String title = punish.optString(1);
                String message = punish.optString(2);
                Reminder.showAlertDialog(context, title, message);
            }
        }
    }

    private void logSecureFailure(String message, Exception exception) {
        if (exception instanceof SecureError) {
            SecureError secureError = (SecureError) exception;
            Log.e("agent-android", message + ": code=" + secureError.getCode()
                    + ", httpStatus=" + secureError.getHttpStatus() + ", traceId="
                    + secureError.getTraceId(), secureError);
        } else {
            Log.e("agent-android", message, exception);
        }
    }

    private String getCode(){
        return String.format("%s-%s-%s-%s", CTDeviceInfo.getInstance().guid(context),
                PLATFORM, APP_ID, SDK_VERSION);
    }

    private String securePost(String logicalPath, String body) throws Exception {
        SecureRequest request = new SecureRequest(
                "POST",
                logicalPath,
                "application/json",
                Collections.singletonMap("code", getCode()),
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                UUID.randomUUID().toString());
        SecureResponse response = secureClient.request(request);
        return response.bodyAsUtf8();
    }

    private JSONArray getCommon(){
        if(COMMON == null){
            COMMON = new JSONArray();
            CTDeviceInfo ctDeviceInfo = CTDeviceInfo.getInstance();
            CTRunningCheck ctRunningCheck = CTRunningCheck.getInstance();
            COMMON.put(USER_ID);
            // guid
            COMMON.put(ctDeviceInfo.guid(context));
            // startId 当前时间的毫秒数
            COMMON.put(System.currentTimeMillis());
            // sdkVersion
            COMMON.put(SDK_VERSION);
            // appId
            COMMON.put(APP_ID);
            // appName,appPackage,appVersion
            ArrayList<Object> baseApp = ctRunningCheck.baseAppInfo();
            COMMON.put(baseApp.size()>0?baseApp.get(0):"");
            COMMON.put(baseApp.size()>1?baseApp.get(1):"");
            COMMON.put(baseApp.size()>2?baseApp.get(2):"");
            // platform
            COMMON.put(PLATFORM);
            // manufacturer
            COMMON.put(ctDeviceInfo.manufacturer());
            // model
            COMMON.put(ctDeviceInfo.model());
            // system
            COMMON.put(ctDeviceInfo.system());
            // systemVersion
            COMMON.put(ctDeviceInfo.systemVersion());
            // netType
            COMMON.put(ctDeviceInfo.netType());
            // lanIp
            COMMON.put(ctDeviceInfo.lanIp());
            // wlanIp
            String publicIp = PublicIpResolver.resolve();
            COMMON.put(publicIp == null ? "0.0.0.0" : publicIp);
            // latitude,longitude,location
            ArrayList<Object> lastLocation = ctRunningCheck.queryLatestLocation();
            COMMON.put(lastLocation.size()>0?lastLocation.get(0):"");
            COMMON.put(lastLocation.size()>1?lastLocation.get(1):"");
            COMMON.put(lastLocation.size()>2?lastLocation.get(2):"");
            COMMON.put(lastLocation.size()>3?lastLocation.get(3):"");
            COMMON.put(lastLocation.size()>4?lastLocation.get(4):"");
            COMMON.put(lastLocation.size()>5?lastLocation.get(5):"");
            COMMON.put(lastLocation.size()>6?lastLocation.get(6):"");
            // clientTime
            COMMON.put(DATE_FORMAT.format(System.currentTimeMillis()));
        }
        return COMMON;

    }

    private synchronized JSONObject nextStartMeta() throws JSONException {
        if (startMeta == null) {
            long now = System.currentTimeMillis();
            startMeta = new JSONObject();
            startMeta.put("messageId", "start-msg-" + UUID.randomUUID());
            startMeta.put("batchId", "start-batch-" + UUID.randomUUID());
            startMeta.put("sessionId", String.valueOf(getCommon().get(2)));
            startMeta.put("attempt", 0);
            startMeta.put("createdAt", now);
            startMeta.put("observedAt", now);
            startMeta.put("sequence", 1);
            startMeta.put("protocolVersion", 1);
            startMeta.put("appKeyId", appKeyId);
            startMeta.put("detectorVersion", SDK_VERSION);
        }
        startMeta.put("attempt", startMeta.optInt("attempt", 0) + 1);
        return startMeta;
    }

    private UploadBatcher.BatchMeta nextUploadMeta() {
        String batchId = "batch-" + UUID.randomUUID();
        try {
            JSONObject meta = new JSONObject();
            meta.put("protocolVersion", 1);
            meta.put("batchId", batchId);
            meta.put("sessionId", String.valueOf(getCommon().get(2)));
            meta.put("attempt", 1);
            meta.put("createdAt", System.currentTimeMillis());
            meta.put("appKeyId", appKeyId);
            meta.put("detectorVersion", SDK_VERSION);
            return new UploadBatcher.BatchMeta(batchId, meta.toString());
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not create upload metadata", exception);
        }
    }

    private synchronized void enqueueMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String messageId = json.optString("messageId").trim();
            if (messageId.isEmpty() || messageId.length() > 128) {
                json.put("messageId", "msg-" + UUID.randomUUID());
            }
            long sequence = json.optLong("sequence", 0);
            if (sequence <= 1) {
                json.put("sequence", nextMessageSequence++);
            } else {
                nextMessageSequence = Math.max(nextMessageSequence, sequence + 1);
            }
            if (json.optLong("observedAt", 0) <= 0) {
                json.put("observedAt", System.currentTimeMillis());
            }
            ThreadSafeQueue.pushMsg(json.toString());
        } catch (JSONException exception) {
            Log.e("agent-android", "discarding invalid checker JSON", exception);
        }
    }

    private static String normalizeAppKeyId(String appId) {
        String normalized = appId == null ? "android" : appId.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (normalized.isEmpty()) {
            normalized = "android";
        }
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }

    private static String USER_ID = "";
    public boolean userData(String userId,JSONObject data){
        USER_ID = userId;
        try {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("type","UserData");
            jsonMsg.put("userId",userId);
            jsonMsg.put("data",data);
            enqueueMessage(jsonMsg.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public String deviceInfo(){

        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("common",getCommon());
            String response = securePost("/cross/info", bodyJson.toString());
            if(response!=null) {
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String labelInfo(){
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("common",getCommon());
            String response = securePost("/cross/label_today", bodyJson.toString());
            if(response!=null) {
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String ratingInfo(){
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("common",getCommon());
            String response = securePost("/cross/rating", bodyJson.toString());
            if(response!=null) {
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface EnrollmentTokenProvider {
        String enrollmentToken();
    }
}
