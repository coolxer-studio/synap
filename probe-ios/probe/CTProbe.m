//
//  CTProbe.m
//  probe
//
//  Created by yaoqi.li on 2023/3/7.
//

#import "CTProbe.h"
#import "CTDeviceInfo.h"
#import "CTRunningCheck.h"
#import "CTSecureCommunication.h"
#import "CTIllegalState.h"
#import "CTUserBehavior.h"
#import "CTPunishment.h"
#import "DispatchTask.h"
#import "ThreadSafeQueue.h"

#define PLATFORM @"ios"
#define APP_ID @"1"
#define SDK_VERSION @"1.0.0.220519"

@interface CTProbe()

@property id selfView;
@property NSString * header;
@property NSMutableArray * common;
@property NSString *userId;

@property ThreadSafeQueue * checkerDataQueue;

@end




@implementation CTProbe
static CTProbe *instance;
static BOOL instanceStarted = NO;

+ (BOOL)initWithViewController:(id)selfView{
    if(instance != nil){
        // 防止多次调用
        return false;
    }
    // TODO: key暂时没使用,留作以后做权限控制
//    NSString *reversedString = [self reverseFirst10Characters:key];
//    NSString *decodedString = [self decodeBase64:reversedString];
//    NSLog(@"init key: %@", decodedString);
    // 创建单例对象
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[CTProbe alloc] init];
    });
    
    instance.selfView = selfView;
    instance.checkerDataQueue = [[ThreadSafeQueue alloc] init];
    // TODO: 需要执行必要的检查，任何问题需要返回false，就不用调用start了
    return YES;
}

- (BOOL)userData:(NSString *)userId withUserData:(NSDictionary *) data{
    _userId = userId;
    NSMutableDictionary *userDataDic = [NSMutableDictionary new];
    [userDataDic setValue:@"UserData" forKey:@"type"];
    [userDataDic setValue:userId forKey:@"userId"];
    [userDataDic setValue:data forKey:@"data"];
    [_checkerDataQueue enqueue:userDataDic];
    return YES;
}

- (NSString *)deviceInfo{
    // 构建启动消息获取配置
    NSMutableDictionary *bodyDic = [NSMutableDictionary new];
    [bodyDic setObject:[self getCommon] forKey:@"common"];
    
    NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
    NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
    
    NSString *response = [CTSecureCommunication post:@"/device_index/info" withHeader:[self getHeader] withBody:requestBody];
    return response;
}

- (NSString *)labelInfo{
    // 构建启动消息获取配置
    NSMutableDictionary *bodyDic = [NSMutableDictionary new];
    [bodyDic setObject:[self getCommon] forKey:@"common"];
    
    NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
    NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
    
    NSString *response = [CTSecureCommunication post:@"/device_index/label_today" withHeader:[self getHeader] withBody:requestBody];
    return response;
}

- (NSString *)ratingInfo{
    // 构建启动消息获取配置
    NSMutableDictionary *bodyDic = [NSMutableDictionary new];
    [bodyDic setObject:[self getCommon] forKey:@"common"];
    
    NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
    NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
    
    NSString *response = [CTSecureCommunication post:@"/device_index/rating" withHeader:[self getHeader] withBody:requestBody];
    return response;
}

- (void)start{
    if(instanceStarted){
        // 防止多次调用
        return;
    }
    instanceStarted = YES;
    // 在后台线程中异步执行任务
    DispatchTask *dispatchTask = [[DispatchTask alloc] initWithQueueName:@"com.abc.dispatchChecker" taskBlock:^{
        // 构建启动消息获取配置
        NSMutableDictionary *bodyDic = [NSMutableDictionary new];
        [bodyDic setObject:[self getCommon] forKey:@"common"];
        
        NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
        NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
        
        NSString *response = [CTSecureCommunication post:@"/1/1" withHeader:[self getHeader] withBody:requestBody];
        // 有时候response最后有三个ETX字符（0x03）导致json失败，做一下过滤
        NSLog(@"response.length:%d",response.length);
        NSMutableCharacterSet *customCharacterSet = [NSMutableCharacterSet characterSetWithCharactersInString:@"\x03"];
        response = [response stringByTrimmingCharactersInSet:customCharacterSet];
        NSLog(@"response.length:%d",response.length);
        // TODO: 上面的过滤逻辑是不是得放在所有对json的转化的逻辑之气？？？以后在加吧
        NSError *error;
        NSDictionary *responseDic = [NSJSONSerialization JSONObjectWithData:[response dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:&error];
        if (error) {
            NSLog(@"JSON 解析错误：%@", [error localizedDescription]);
        }
        NSInteger status = [[responseDic valueForKey:@"status"] intValue];
        if(status == 1){
            // 加载配置
            NSDictionary *configDic = responseDic[@"config"];
            NSArray *startConfig = configDic[@"startConfig"];
            if([startConfig[0] boolValue]){
                // 1允许开始检测
                // 1.1配置检测项
                NSData *configData = [NSJSONSerialization dataWithJSONObject:configDic options:NSJSONWritingPrettyPrinted error:nil];
                NSString *allCheckerConfig = [[NSString alloc] initWithData:configData encoding:NSUTF8StringEncoding];
                [[CTIllegalState sharedInstance] loadConfig:allCheckerConfig];
                [[CTUserBehavior sharedInstance] loadConfig:allCheckerConfig];
                [[CTRunningCheck sharedInstance] loadConfig:allCheckerConfig];
                [[CTDeviceInfo sharedInstance] loadConfig:allCheckerConfig];
                // 1.2 根据配置启动checker
                [self dispatchCheckerWithInterval:[startConfig[1] doubleValue] WithCancelTime:[startConfig[2] doubleValue]];
                // 1.3 根据配置启动sender
                [self dispatchSenderWithInterval:[startConfig[3] doubleValue] WithCancelTime:[startConfig[4] doubleValue]];
                NSLog(@"start succeed");
            }
        }else{
            NSLog(@"start failed ,response is:%@",[responseDic description]);
        }
    }];
    [dispatchTask start];
    
}

- (BOOL)dispatchCheckerWithInterval:(NSTimeInterval)interval WithCancelTime:(NSTimeInterval)cancelTime{
    // 回调任务
    // 开启崩溃日志收集
    [[CTIllegalState sharedInstance] startTrack];
    // 收集上次奔溃日志信息
    NSString *illegalState = [[CTIllegalState sharedInstance] check];
    if(illegalState){
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[illegalState dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
    }
    // 行为检测
    [[CTUserBehavior sharedInstance] registerCallBackWithDataBlock:^(NSString * _Nullable message) {
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[message dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
    }];
    [[CTUserBehavior sharedInstance] startTrack];
    
    // 周期任务
    DispatchTask *dispatchTask = [[DispatchTask alloc] initWithQueueName:@"com.abc.dispatchChecker" interval:interval cancelTime:cancelTime taskBlock:^{
        // 周期任务的代码块
        NSLog(@"checker ......");
        // 设备信息采集
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[[CTDeviceInfo sharedInstance] deviceInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        
        // 运行检查
        CTRunningCheck *runningCheck = [CTRunningCheck sharedInstance];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getNetInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getSelfAppInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getFileInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getRunTimeInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getAppInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getLocationInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
        [_checkerDataQueue enqueue:[NSJSONSerialization JSONObjectWithData:[[runningCheck getInjectInfo] dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil]];
    }];
    [dispatchTask start];

    return YES;
}

- (BOOL)dispatchSenderWithInterval:(NSTimeInterval)interval WithCancelTime:(NSTimeInterval)cancelTime{
    // 周期任务
    DispatchTask *dispatchTask = [[DispatchTask alloc] initWithQueueName:@"com.abc.dispatchSender" interval:interval cancelTime:cancelTime taskBlock:^{
        // 周期任务的代码块
        NSLog(@"sender ......");
        if([_checkerDataQueue count] > 0){
            // 封装最终协议内容
            NSMutableDictionary *bodyDic = [NSMutableDictionary new];
            [bodyDic setObject:[self getCommon] forKey:@"common"];
            [bodyDic setObject:[_checkerDataQueue dequeueAll] forKey:@"data"];
            
            NSError *error;
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:&error];

            if (!jsonData) {
                NSLog(@"转化为 JSON 失败：%@", error);
            } else {
                NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
                NSLog(@"JSON 字符串：%@", jsonString);
            }
            
            NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
            NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
            
            NSString *response = [CTSecureCommunication post:@"/1/2" withHeader:[self getHeader] withBody:requestBody];
            
            NSDictionary *responseDic = [NSJSONSerialization JSONObjectWithData:[response dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableContainers error:nil];
            NSInteger status = [[responseDic valueForKey:@"status"] intValue];
            if(status == 1){
                NSArray *punishes = responseDic[@"punishes"];
                for(NSArray *punish in punishes){
                    if([punish[0] intValue] == 1){
                        // 弹窗提醒
                        dispatch_async(dispatch_get_main_queue(), ^{
                            // 在主线程上执行与布局引擎相关的操作 修改界面布局、更新视图等
                            NSString *title = punish[1];
                            NSString *message = punish[2];
                            // TODO: 这里中文乱码，需要解决一下
                            [Reminder showAlertDialogWithTitle:title message:message selfView:self.selfView];
                        });
                    }
                }
            }
            
        }
    }];
    [dispatchTask start];
    
    return YES;
}

- (NSString *)getHeader{
    if(_header){
        // 属性不为空直接返回
        return _header;
    }
    CTDeviceInfo *deviceInfo = [CTDeviceInfo sharedInstance];
    _header = [[NSString alloc] initWithFormat:@"Content-Type:application/json\r\ncode:%@-%@-%@-%@\r\n",[deviceInfo guid],PLATFORM,APP_ID,SDK_VERSION];
    return _header;
}

- (NSMutableArray *)getCommon{
    @synchronized(self) {
        if(_common){
            // 属性不为空直接返回,需要考虑多线程时候的数组正在添加，有人访问后数据不全的问题
            return _common;
        }
        _common = [NSMutableArray new];
        CTDeviceInfo *deviceInfo = [CTDeviceInfo sharedInstance];
        // TODO: user_id
        [_common addObject:_userId==nil?@"":_userId];
        // guid
        [_common addObject:[deviceInfo guid]];
        // startId 当前时间的毫秒数
        NSDate *currentDate = [NSDate date];
        NSTimeInterval timeInterval = [currentDate timeIntervalSince1970];
        NSTimeInterval millisecond = timeInterval * 1000;
        NSInteger milliseconds = (NSInteger)round(millisecond);
        [_common addObject:@(milliseconds)];
        // sdkVersion
        [_common addObject:SDK_VERSION];
        // appId
        [_common addObject:APP_ID];
        // appName,appPackage,appVersion
        NSArray *appInfo = [[CTRunningCheck sharedInstance] baseAppInfo];
        [_common addObjectsFromArray:[appInfo subarrayWithRange:NSMakeRange(0, 3)]];
        // platform
        [_common addObject:PLATFORM];
        // manufacturer
        [_common addObject:[[CTDeviceInfo sharedInstance] manufacturer]];
        // model
        [_common addObject:[[CTDeviceInfo sharedInstance] model]];
        // system
        [_common addObject:[[CTDeviceInfo sharedInstance] system]];
        // systemVersion
        [_common addObject:[[CTDeviceInfo sharedInstance] systemVersion]];
        // netType
        [_common addObject:[[CTDeviceInfo sharedInstance] netType]];
        // lanIp
        [_common addObject:[[CTDeviceInfo sharedInstance] lanIp]];
        // wlanIp
        [_common addObject:[[CTRunningCheck sharedInstance] wanIp]];
        // latitude,longitude,location
        NSDictionary *locationDic = [[CTRunningCheck sharedInstance] queryLatestLocation];
        [_common addObject:locationDic[@"latitude"]];
        [_common addObject:locationDic[@"longitude"]];
        [_common addObject:locationDic[@"country"]];
        [_common addObject:locationDic[@"province"]];
        [_common addObject:locationDic[@"city"]];
        [_common addObject:locationDic[@"county"]];
        [_common addObject:locationDic[@"thoroughfare"]];
        // clientTime
        NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
        [dateFormatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
        NSString *currentTime = [dateFormatter stringFromDate:currentDate];
        [_common addObject:currentTime];
        return _common;
    }
}

- (NSString *)reverseFirst10Characters:(NSString *)input {
    if (input.length < 10) {
        return input;
    }
    
    NSMutableString *reversedString = [NSMutableString stringWithString:input];
    NSInteger left = 0;
    NSInteger right = 9;
    
    while (left < right) {
        NSString *leftChar = [input substringWithRange:NSMakeRange(left, 1)];
        NSString *rightChar = [input substringWithRange:NSMakeRange(right, 1)];
        
        [reversedString replaceCharactersInRange:NSMakeRange(left, 1) withString:rightChar];
        [reversedString replaceCharactersInRange:NSMakeRange(right, 1) withString:leftChar];
        
        left++;
        right--;
    }
    
    return reversedString;
}

- (NSString *)decodeBase64:(NSString *)base64String {
    NSData *base64Data = [[NSData alloc] initWithBase64EncodedString:base64String options:NSDataBase64DecodingIgnoreUnknownCharacters];
    NSString *decodedString = [[NSString alloc] initWithData:base64Data encoding:NSUTF8StringEncoding];
    return decodedString;
}

+ (instancetype)sharedInstance {
    // 初始化工作在init中做
    return instance;
}

@end
