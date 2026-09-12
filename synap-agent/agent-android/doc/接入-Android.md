# Android接入

Android 启动请求把 StartData 作为会话第一条逻辑消息：`meta.sequence=1`，并携带稳定的
`messageId/batchId/sessionId/createdAt/observedAt`。同一探针实例重试时只递增
`attempt`，服务端按 `messageId` 去重并重放首次响应。

## SDK说明
- sdk下载：http://sdk.genie.coolxer.com
- sdk 目录说明
```
├── doc [文档]  
│   └── 接入-Android.md  
└── probe.aar [aar库]
```


## 集成步骤

**第一步：**  拷贝aar文件

工程切换到project视图 -> 拷贝aar文件到libs中（没有libs的话手动创建一个）

**第二步：** 修改build.gradle文件，sync后即可引入

```
android {
    packagingOptions{
        doNotStrip"*/*/libprobe.so"
    }
}
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.aar'])
}
```

如何确认引入依赖成功：  

build项目如果没有报错且在External Libraries目录下存在导入的aar包，就说明导入完成

**第三步：** proguard-rules.pro中配置

```
-keep class com.coolxer.** {*;}
-dontwarn com.coolxer.**
```

**第四步：**  AndroidManifest.xml中声明权限

由于Android6.0及以上权限需要在app运行时动态向用户申请，所以请在初始化SDK之前，根据需要将下表中的权限进行动态申请，目前SDK全量检测点需要动态申请的权限如下表所示  

**建议权限：**

| 权限                   | 说明                     | 用途                       | 不授权影响                  |
| ---------------------- | ------------------------ | -------------------------- | --------------------------- |
| ACCESS_FINE_LOCATION   | 位置信息(如GPS)          | 用于位置造假检测           | 位置信息造假检测能力下降50% |
| ACCESS_COARSE_LOCATION |                          |                            |                             |
| READ_PHONE_STATE       | 获取手机识别码等设备信息 | 用于设备造假检测和设备信息 | 设备造假检测能力下降50%     |
| ACCESS_NETWORK_STATE   | 获取网络状态             | 获取网络环境               | 无法获取当前用户网络信息    |
| ACCESS_WIFI_STATE      | 获取wifi状态             |                            |                             |

请在app的AndroidManifest.xml文件中增加（如果app已经申请了，忽略即可）：

```
<!-- 网络状态 -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<!-- wifi状态 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<!-- 手机识别码 -->
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<!-- 位置信息 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
```


**增强权限：**

| 权限                  | 说明                                                         | 用途             | 不授权影响                      |
| --------------------- | ------------------------------------------------------------ | ---------------- | ------------------------------- |
| OPSTR_GET_USAGE_STATS | 有权获取近期运行应用列表情况（此弹窗会跳转到系统设置界面，如果对于程序外挂不敏感，可不申请） | 用于程序外挂检测 | 6.0之后没有权限获取其他进程信息 |

如果需使用此权限请在app的AndroidManifest.xml文件中增加：

```
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
```

**增强权限：**

| 权限                 | 说明                                                         | 用途             | 不授权影响                                                   |
| -------------------- | ------------------------------------------------------------ | ---------------- | ------------------------------------------------------------ |
| `GET_INSTALLED_APPS` | 有权获取近期运行应用列表情况（此弹窗为工信部要求的，国内厂商要增加PERMISSION_GET_INSTALLED_APPS权限控制应用列表的获取，可选择不加，添加后部分手机可能会弹出是否允许获取应用列表的弹窗） | 用于获取应用列表 | 由于工信部下发需求，国内手机厂商往往都要求在获取应用列表时添加该权限，如果不加可能会导致应用无法上架国内部分应用市场。 |

如果需使用此权限请在app的AndroidManifest.xml文件中增加：

```
<uses-permission android:name="android.permission.GET_INSTALLED_APPS"/>
```


**Android 11+设置 **

如果您使用gradle编译项目，而且项目的targetSdkVersion >= 30，请参考此条目，否则忽略即可。targetSdkVersion位置如图所示：

由于android 11及以上版本新特性原因，获取应用列表需要以下设置才能生效。

打开项目的AndroidMainfest.xml，增加以下配置，示例：

内容如下

```
......
xmlns:tools="http://schemas.android.com/tools"
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />
```



**第五步：**  初始化sdk，集成结束

需要在app的主activity或者application的onCreate方法内调用初始化函数：

**代码如下：**

```
// 加载运行探针
boolean initialized = CTProbe.initWithContext(this);
if (initialized) {
    CTProbe.getInstance().start();
    Log.d("demo","start probe");
}
```

## 验证

三种方式验证：

1.初始化成功后：logcat日志输出：is init probe:true

2.通过手机抓包app（抓包精灵等）可以看到发送密文数据到指定的服务器

3.登录web平台查看运行详情可以看到有运行数据上送

## 接口定义

定义了一个名为`CTProbe`的类。下面是该类的方法列表：

- `public static boolean initWithContext(Context context)`：传入一个`Context`对象，用于初始化。
- `public void start()`：开始探针的工作。
- `public boolean userData(String userId, JSONObject data)`：传入一个用户ID字符串`userId`和一个`JSONObject`数据对象`data`，用于提供用户数据给探针。
- `public String deviceInfo()`：返回设备信息字符串。
- `public String labelInfo()`：返回探针的标签。
- `public String ratingInfo()`：返回探针的评级。
