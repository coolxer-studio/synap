# Web接入

## SDK说明
- sdk下载：http://sdk.genie.coolxer.com
- sdk 目录说明
```
├── doc [文档]  
│   └── 接入-Web.md  
└── js [依赖库]  
    └── probe.js   
```

## 集成步骤

**第一步：**  拷贝js文件到项目工程  
拷贝probe.js文件到任意目录下，示例如：./js/probe.js

**第二步：**  引入sdk，集成结束  
在需要监控的页面引用js标签，如果多个页面需要每个页面都引入，只需要引入探针中的probe.js文件即可，示例如下：  

```
<script type="text/javascript" src="./js/probe.js" ></script>
```

## 验证

三种方式验证：

1.初始化成功后：console日志输出：synap running:true

2.通过控制台网络分析器可以看到发送密文数据到指定的服务器

3.登录web平台查看运行详情可以看到有运行数据上送 

 

##  接口定义

### 1.用户数据上传接口

**接口函数：**  
window.userData(userId,userData)

**参数说明：**  
userId（必填）：    
一个字符串，默认是 "utf-8"。可以唯一标识用户的id，建议用脱敏后的字符串。   
userData（必填）：    
一个具有属性的对象，用来传递用户的更多信息，具体属性可以用户自定义构造。

**返回值说明：**  
类型：boolean
说明：true：成功，false：失败

**示例：**    
```
var userId = MD5(userName);
var userData = { userName: userName, idCard: MD5(idCard) };
window.userData(userId, userData);
```
### 2.设备信息查询接口

**接口函数：**  
window.deviceInfo()

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
            "build_date": null,
            "common": {
                "user_id": null,
                "guid": "ae3b74f3-f843-4575-a3b3-dfc3571b3127",
                "start_id": 1713920372652,
                "sdk_version": "1.0.0.240501",
                "app_id": 1,
                "app_name": "风险感知",
                "app_package": "",
                "app_version": "-",
                "platform": "h5",
                "manufacturer": "unknown",
                "model": "Windows 2",
                "system": "Windows",
                "system_version": "10.0",
                "net_type": "4G",
                "lan_ip": "0.0.0.0",
                "wan_ip": "0.0.0.0",
                "latitude": 0,
                "longitude": 0,
                "country": "",
                "province": "",
                "city": "",
                "county": "",
                "thoroughfare": "",
                "client_time": "2024-04-24 09:00:12"
            },
            "device": {
                "system_name": "Windows",
                "system_version": "10.0",
                "model_code": "Windows 2",
                "country_code": null,
                "language": null,
                "time_zone": "Asia/Shanghai(-480)"
            },
            "screen": {
                "width": 1536,
                "height": 960,
                "avail_width": 1536,
                "avail_height": 912,
                "screen_size": 19
            },
            "cpu": {
                "total_core": 12,
                "type": null,
                "abi": null
            },
            "battery": {
                "info": ""
            },
            "uuid": {
                "guid": "ae3b74f3-f843-4575-a3b3-dfc3571b3127"
            },
            "network": {
                "lan_ip": null,
                "type": "4G",
                "info": "unknown|unknown|100|false"
            },
            "storage": {
                "total_disk": null,
                "total_ram": 8
            },
            "browser": {
                "type": [0,0,1,0,0,0,1,1],
                "playType": [
                    "",
                    "probably",
                    "probably"
                ]
            },
            "base_info": {
                "getPixelRatio": "1.25",
                "colorDepth": "24",
                "getPlugins": [
                    [
                        "PDF Viewer",
                        "internal-pdf-viewer",
                        "Portable Document Format"
                    ],
                    [
                        "Chrome PDF Viewer",
                        "internal-pdf-viewer",
                        "Portable Document Format"
                    ]
                ],
                "getAcceptMimeType": [
                    {
                        "type": "application/pdf",
                        "description": "Portable Document Format",
                        "suffixes": "pdf"
                    },
                    {
                        "type": "text/pdf",
                        "description": "Portable Document Format",
                        "suffixes": "pdf"
                    }
                ],
                "getCanvasFpCrc": "d4a66257",
                "getWebglVendorAndRenderer": "Google Inc. (Intel)~ANGLE (Intel, Intel(R) UHD Graphics (0x0000A7A8) Direct3D11 vs_5_0 ps_5_0, D3D11)",
                "getNavigatorCpuClass": "",
                "getNavigatorPlatform": "Win32",
                "getFonts": [
                    "Agency FB",
                    "Segoe UI Light",
                    "SimHei"
                ],
                "getTouchSupport": [
                    0,
                    false,
                    false
                ],
                "getDoNotTrack": "",
                "getAdBlock": 0,
                "hasSessionStorage": 1,
                "hasLocalStorage": 1,
                "hasIndexedDB": 1,
                "getHasLiedLanguages": 0,
                "getHasLiedResolution": 0,
                "getHasLiedOs": 0,
                "getHasLiedBrowser": 0,
                "getWebglFp": "data:image/png;base64,iVBORw0KGgoA ...(数据过长隐藏) rangeMax:30",
                "getEmptyEvalLength": 33,
                "getProductSub": "20030107",
                "getErrorFF": 0
            },
            "document": {
                "characterSet": "UTF-8"
            },
            "window": {
                "initWithKey": null
            },
            "navigator": {
                "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "oscpu": null,
                "vendor": "Google Inc."
            }
        },
        "server_time": "2024-04-24 09:00:11"
    }
}
```

**示例：**    
```
var data = window.deviceInfo();
if (data.code === 0) {
	const deviceInfoMap = {
		"hardware": ["device"],
		"cpu": ["cpu"],
		"storage": ["storage"],
		"network": ["network"],
		"battery": ["battery"],
		"screen": ["screen"],
		"app": ["app"],
		"other": ["browser", "uuid", "document", "window", "navigator", "baseInfo"]
	};
	const detail = data.data.fact;
	for (const deviceInfoKey in deviceInfoMap) {
		if (Object.hasOwnProperty.call(deviceInfoMap, deviceInfoKey)) {
			const deviceInfoMapValueArray = deviceInfoMap[deviceInfoKey];
			var htmlContent = "";
			for (const detailKey of deviceInfoMapValueArray) {
				for (var contextKey in detail[detailKey]) {
					htmlContent += contextKey + ":" + detail[detailKey][contextKey] + "<br>"
				}
			}
			document.getElementById("device-info-div" + deviceInfoKey)
				.innerHTML = htmlContent;
		}
	}

}
```
### 3.评级指数查询接口

**接口函数：**  
window.ratingInfo()

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
        "guid": "ae3b74f3-f843-4575-a3b3-dfc3571b3127",
        "appId": "1",
        "ratingCode": "default",
        "score": 20,
        "grade": "低风险"
    }
}
```

**示例：**    
```
var data = window.ratingInfo();
if (data.code === 0) {
	var grade = data.data.grade;
	var score = data.data.score;
	var gradeColor = "#20C997";
	if (grade.indexOf("高") !== -1) {
		gradeColor = "#FA5252";
	} else if (grade.indexOf("中") !== -1) {
		gradeColor = "#FCC419";
	}
	var htmlContent = `
                    <span style="color: ${gradeColor};">${grade}(${score})</span>
                `;
	var detail = data.data.detail;
	document.getElementById("rating-info-div")
		.innerHTML = htmlContent;
}
```
### 4.标签信息查询接口

**接口函数：**  
window.labelInfo()

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
			"value_list": [
				"域名欺诈(4)"
			]
		},
		{
			"type": "low",
			"value_list": [
				"打开开发工具(58)"
			]
		}
	]
}
```

**示例：**    
```
var data = window.labelInfo();
if (data.code === 0) {
	for (var labelData of data.data) {
		var labelColor = "#20C997";
		if (labelData.type.indexOf("high") !== -1) {
			labelColor = "#FA5252";
		} else if (labelData.type.indexOf("medium") !== -1) {
			labelColor = "#FCC419";
		}
		var htmlContent = "";
		for (var labelTag of labelData.value_list) {
			htmlContent += `<span class="tag" style="background-color: ${labelColor};">${labelTag}</span>`;
		}
		document.getElementById(labelData.type + "Label")
			.innerHTML = htmlContent;
	}
}
```

 

