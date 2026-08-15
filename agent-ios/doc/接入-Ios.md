# Ios接入

iOS 启动请求把 StartData 作为会话第一条逻辑消息：`meta.sequence=1`，并携带稳定的
`messageId/batchId/sessionId/createdAt/observedAt`。同一探针实例重试时只递增
`attempt`，服务端按 `messageId` 去重并重放首次响应。

## SDK说明
- sdk下载：http://sdk.genie.coolxer.com  
- sdk 目录说明
```
├── doc [文档]  
│   └── 接入-ios.md  
├── framework [framework库]
│   └── probe.framework
└── static [静态库]
    └── probe
        ├── CTProbe.h
        └── probe.a  
```
- 两种形式的SDK文件：probe.framework和libProbe.a：两种形式的SDK提供的功能完全一样。对于APP工程，推荐使用probe.framework形式的SDK进行集成，对于framework项目工程，推荐使用libProbe.a形式进行集成。

## 集成步骤

**第一步：**  拷贝framework文件  

将probe.framework拷贝到项目中，按照xcode提示导入framework，点击Finish完成导入操作。  

**注意：**在Xcode10及以上版本的Xcode创建项目时，可能会出现没有Frameworks目录的情况，出现此类情况并没有影响，用户可以自己创建一个Frameworks文件夹，该Frameworks文件夹放在项目工程文件的同级别目录下即可。

导入成功后在AppDelegate.m文件的#import "AppDelegate.h"的下一行通过#import <probe/CTProbe.h>方式导入SDK头文件，编译不出错即代表集成成功。

**第一步：** 选择合适的时机初始化sdk，集成结束

为AppDelegate.m增加调用方法代码，初始化，编译通过，集成完成（AppDelegate类为iOS 项目的入口类，在与项目工程同名的文件夹目录下）。示例代码如下：

Objc代码工程示例：

```
	BOOL initialized = [CTProbe initWithViewController:self];
    if (initialized){
        [[CTProbe sharedInstance] start];
        NSLog(@"CTProbe start");
    }
```


## 验证

三种方式验证：

1.初始化成功后：logcat日志输出：is init probe:true

2.通过手机抓包app（抓包精灵等）可以看到发送密文数据到指定的服务器

3.登录web平台查看运行详情可以看到有运行数据上送 

 

##  接口定义
定义了一个名为`CTProbe`的类，继承自`NSObject`。下面是该类的方法列表：

##  接口定义

### 1.初始化接口
用于初始化`CTProbe`。  

**接口函数：**  
`+ (BOOL)initWithViewController:(id)selfView`

**参数说明：**  
selfView（必填）：    
传入一个视图控制器`selfView`，用于初始化`CTProbe`。

**返回值说明：**  
类型：boolean
说明：true：成功，false：失败

**示例：**    
```
    BOOL initialized = [CTProbe initWithViewController:self];
    if (initialized){
        [[CTProbe sharedInstance] start];
        NSLog(@"CTProbe start");
    }
```

### 2.获取探针实例对象

**接口函数：**  
`+ (instancetype)sharedInstance`

**参数说明：**  
无

**返回值说明：**  
类型：`CTProbe`类的共享实例  
用于后续接口的调用。 

**示例：**    
```
    BOOL initialized = [CTProbe initWithViewController:self];
    if (initialized){
        [[CTProbe sharedInstance] start];
        NSLog(@"CTProbe start");
    }
```

### 3.启动探针

**接口函数：**  
`- (void)start`

**参数说明：**  
无

**返回值说明：**  
无

**示例：**    
```
    BOOL initialized = [CTProbe initWithViewController:self];
    if (initialized){
        [[CTProbe sharedInstance] start];
        NSLog(@"CTProbe start");
    }
```

### 4.用户数据上传接口

**接口函数：**  
`- (BOOL)userData:(NSString *)userId withUserData:(NSDictionary *)data`

**参数说明：**  
`userId`（必填）：    
一个用户ID字符串
`data`（必填）：    
传入一个字典类型的用户数据  

**返回值说明：**  
类型：boolean
说明：true：成功，false：失败

**示例：**    
```
NSString *userId = @"id";
NSString *userName = @"name";
NSMutableDictionary *data = [NSMutableDictionary new];
[data setValue:userId forKey:@"userId"];
[data setValue:userName forKey:@"userName"];
[CTProbe sharedInstance] userData:userId withUserData:data];
```
### 5.设备信息查询接口

**接口函数：**  
`- (NSString *)deviceInfo`：返回设备信息字符串

**参数说明：**  
无参数

**返回值说明：**  
类型：json对象
code：0成功，其他错误，msg：提示消息，data：返回的数据，示例如下
```
{
	"code": 0,
	"msg": "请求成功",
	"data": {
		"fact": {
			"type": "DeviceData",
			"build_date": "2024-04-30 09:43:58",
			"common": {
				"user_id": "",
				"guid": "1D448D68-117D-414A-BB10-2F1DD4751133",
				"start_id": 1714484616411,
				"sdk_version": "1.0.0.220519",
				"app_id": 1,
				"app_name": "x-genie终端",
				"app_package": "com.coolxer.probe-demo",
				"app_version": "1.0",
				"platform": "ios",
				"manufacturer": "apple",
				"model": "iPhone12,1",
				"system": "iOS",
				"system_version": "14.3",
				"net_type": "Wi-Fi",
				"lan_ip": "192.168.1.18",
				"wan_ip": "0.0.0.0",
				"latitude": 0.0,
				"longitude": 0.0,
				"country": "",
				"province": "",
				"city": "",
				"county": "",
				"thoroughfare": "",
				"client_time": "2024-04-30 21:43:36"
			},
			"device": {
				"type": "iPhone",
				"name": "yaoqi.li的iPhone",
				"simulator": false,
				"system_name": "iOS",
				"system_version": "14.3",
				"build_version": "iPhone12,1",
				"kernel_version": "20.2.0",
				"model_code": "iPhone12,1",
				"country_code": "CN",
				"language": "zh-Hans-CN",
				"time_zone": "GMT+8",
				"currency": "¥",
				"activation_time": null,
				"startup_time": "2024-04-30 21:42:21",
				"file_creation_time": "1970-01-01 00:00:00",
				"file_modification_time": "1970-01-01 00:0"...
```

**示例：**    
```
NSLog(@"deviceInfo:%@",[[CTProbe sharedInstance] deviceInfo]);
```
### 6.评级指数查询接口

**接口函数：**  
`- (NSString *)ratingInfo`

**参数说明：**  
无参数

**返回值说明：**  
类型：json对象
code：0成功，其他错误，msg：提示消息，data：返回的数据，示例如下
```
{
	"code": 0,
	"msg": "请求成功",
	"data": {
		"guid": "1D448D68-117D-414A-BB10-2F1DD4751133",
		"appId": "1",
		"ratingCode": "default",
		"score": 80,
		"grade": "中风险"
	}
}
```

**示例：**    
```
NSLog(@"ratingInfo:%@",[[CTProbe sharedInstance] ratingInfo]);
```
### 7.标签信息查询接口

**接口函数：**  
`- (NSString *)labelInfo`

**参数说明：**  
无参数

**返回值说明：**  
类型：json对象
code：0成功，其他错误，msg：提示消息，data：返回的数据，示例如下
```
{
	"code": 0,
	"msg": "请求成功",
	"data": [{
		"type": "high",
		"value_list": ["swizzling(3)", "frida(3)", "ROOT(3)", "debug(3)"]
	}, {
		"type": "other",
		"value_list": ["ROOT(3)"]
	}, {
		"type": "medium",
		"value_list": ["cydia(3)", "cycript(3)"]
	}]
}
```

**示例：**    
```
NSLog(@"labelInfo:%@",[[CTProbe sharedInstance] labelInfo]);
```


 

 
