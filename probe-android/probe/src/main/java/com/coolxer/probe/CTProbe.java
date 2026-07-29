package com.coolxer.probe;

import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.coolxer.deviceinfo.CTDeviceInfo;
import com.coolxer.punishment.Reminder;
import com.coolxer.runningcheck.CTRunningCheck;
import com.coolxer.securecommunication.CTSecureCommunication;
import com.coolxer.userbehavior.CTUserBehavior;
import com.coolxer.userbehavior.checker.base.IMonitored;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class CTProbe {

    private static volatile CTProbe INSTANCE = null;
    private static volatile Boolean INSTANCE_STARTED = false;

    private Context context;

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final static String PLATFORM = "android";
    private final static String APP_ID = "1";
    private final static String SDK_VERSION = "1.0.0.220519";
    private static String HEADER;
    private static JSONArray COMMON;

    private CTProbe(){}

    public static boolean initWithContext(Context context){
        if(INSTANCE != null){
            return false;
        }
        synchronized (CTProbe.class){
            if(INSTANCE == null){
                INSTANCE = new CTProbe();
            }
        }
        INSTANCE.context = context;
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
                    String response = CTSecureCommunication.post("/1/1","Content-Type:application/json\r\n"+getHeader(), bodyJson.toString());
                    if(response!=null){
                        JSONObject responseJson = null;
                        responseJson = new JSONObject(response);
                        if(responseJson != null){
                            // 2.1 配置
                            int status = responseJson.optInt("status");
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
                                            ThreadSafeQueue.pushMsg(msg);
                                            return true;
                                        }
                                    });
                                    CTUserBehavior.getInstance().startActivityTrack();
                                    CTUserBehavior.getInstance().startPrivacyTrack();
                                    // run-check 的内存监控
                                    CTRunningCheck.getInstance().registerEventCallBack(new com.coolxer.runningcheck.checker.base.IMonitored.ICallback() {
                                        @Override
                                        public boolean onMsg(String msg) {
                                            ThreadSafeQueue.pushMsg(msg);
                                            return true;
                                        }
                                    });
                                    CTRunningCheck.getInstance().startEventTrack();
                                    // 1.2 周期任务
                                    new DispatchTask("checker",(long)startConfig.optDouble(1)*1000,(long)startConfig.optDouble(2)*1000,checkerRunnable()).start();
                                    // 1.3 根据配置启动sender
                                    new DispatchTask("sender",(long)startConfig.optDouble(3)*1000,(long)startConfig.optDouble(4)*1000,senderRunnable()).start();
                                }
                            }
                        }
                    }else {
                        Log.d("probe-android","start failed ,response is:"+response);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("probe-android", "error:" + e.getMessage());
                }
            }
        }).start();
    }

    private Runnable checkerRunnable(){
        return new Runnable() {
            @Override
            public void run() {
                ThreadSafeQueue.pushMsg(CTDeviceInfo.getInstance().deviceInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getAppInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getFileInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getInjectInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getLocationInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getNetInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getRuntimeInfo());
                ThreadSafeQueue.pushMsg(CTRunningCheck.getInstance().getSelfAppInfo());
            }
        };
    }

    private Runnable senderRunnable(){
        return new Runnable() {
            @Override
            public void run() {
                try {
                    // 上传数据
                    ArrayList<String> queueMsgList = ThreadSafeQueue.pullMsg();
                    if (queueMsgList.size()==0){
                        return;
                    }
                    JSONObject bodyJson = new JSONObject();
                    bodyJson.put("common",getCommon());
                    JSONArray msgArray = new JSONArray();
                    for (String msgString:queueMsgList) {
                        msgArray.put(new JSONObject(msgString));
                    }
                    bodyJson.put("data",msgArray);

                    String response = CTSecureCommunication.post("/1/2","Content-Type:application/json\r\n"+getHeader(), bodyJson.toString());
                    if(response!=null) {
                        JSONObject responseJson = null;
                        try {
                            responseJson = new JSONObject(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d("probe-android", "error:" + e.getMessage());
                        }
                        if(responseJson != null){
                            int status = responseJson.optInt("status");
                            if(status == 1){
                                JSONArray punishArray = responseJson.optJSONArray("punish");
                                if(punishArray != null){
                                    for (int i = 0; i < punishArray.length(); i++) {
                                        JSONArray punish = punishArray.optJSONArray(i);
                                        if(punish.optInt(0) == 1){
                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                            String title = punish.optString(1);
                                            String message = punish.optString(2);
                                            Reminder.showAlertDialog(context,title,message);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private String getHeader(){
        if(HEADER == null){
            HEADER = String.format("Content-Type:application/json\r\ncode:%s-%s-%s-%s\r\n",CTDeviceInfo.getInstance().guid(context),PLATFORM,APP_ID,SDK_VERSION);
        }
        return HEADER;
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
            COMMON.put(ctRunningCheck.wanIp());
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

    private static String USER_ID = "";
    public boolean userData(String userId,JSONObject data){
        USER_ID = userId;
        try {
            JSONObject jsonMsg = new JSONObject();
            jsonMsg.put("type","UserData");
            jsonMsg.put("userId",userId);
            jsonMsg.put("data",data);
            ThreadSafeQueue.pushMsg(jsonMsg.toString());
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
            String response = CTSecureCommunication.post("/device_index/info","Content-Type:application/json\r\n"+getHeader(), bodyJson.toString());
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
            String response = CTSecureCommunication.post("/device_index/label_today","Content-Type:application/json\r\n"+getHeader(), bodyJson.toString());
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
            String response = CTSecureCommunication.post("/device_index/rating","Content-Type:application/json\r\n"+getHeader(), bodyJson.toString());
            if(response!=null) {
                return response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
