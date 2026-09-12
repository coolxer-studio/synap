//
//  CTProbe.m
//  probe
//
//  Created by yaoqi.li on 2023/3/7.
//

#import "CTProbe.h"
#import "CTDeviceInfo.h"
#import "CTRunningCheck.h"
#import <probe/probe-Swift.h>
#import "CTIllegalState.h"
#import "CTUserBehavior.h"
#import "CTPunishment.h"
#import "DispatchTask.h"
#import "ThreadSafeQueue.h"

#define PLATFORM @"ios"
#define APP_ID @"1"
#define SDK_VERSION @"1.0.0.220519"
#define MAX_UPLOAD_FACTS 100
#define MAX_UPLOAD_BODY_BYTES (240 * 1024)

@interface CTProbe()

@property id selfView;
@property NSString * code;
@property NSMutableArray * common;
@property NSString *userId;
@property SynapSecureAdapter *secureAdapter;
@property NSMutableDictionary *startMeta;
@property NSString *protocolAppKeyID;
@property NSMutableArray<NSDictionary *> *pendingMessages;
@property NSArray<NSDictionary *> *inflightMessages;
@property NSMutableDictionary *inflightMeta;
@property long long nextMessageSequence;

@property ThreadSafeQueue * checkerDataQueue;

@end




@implementation CTProbe
static CTProbe *instance;
static BOOL instanceStarted = NO;

+ (BOOL)initWithViewController:(id)selfView
                       baseURL:(NSURL *)baseURL
                         appID:(NSString *)appID
                   serverKeyID:(NSString *)serverKeyID
                   trustAnchor:(NSString *)trustAnchor
       enrollmentTokenProvider:(CTEnrollmentTokenProvider)enrollmentTokenProvider{
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
    instance.protocolAppKeyID = appID;
    instance.secureAdapter = [[SynapSecureAdapter alloc]
        initWithBaseURL:baseURL
        appID:appID
        serverKeyID:serverKeyID
        trustAnchor:trustAnchor
        enrollmentTokenProvider:enrollmentTokenProvider];
    if (instance.secureAdapter == nil) {
        return NO;
    }
    instance.checkerDataQueue = [[ThreadSafeQueue alloc] init];
    instance.pendingMessages = [NSMutableArray new];
    instance.inflightMessages = @[];
    instance.nextMessageSequence = 2;
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
    
    NSString *response = [self securePost:@"/cross/info" body:requestBody];
    return response;
}

- (NSString *)labelInfo{
    // 构建启动消息获取配置
    NSMutableDictionary *bodyDic = [NSMutableDictionary new];
    [bodyDic setObject:[self getCommon] forKey:@"common"];
    
    NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
    NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
    
    NSString *response = [self securePost:@"/cross/label_today" body:requestBody];
    return response;
}

- (NSString *)ratingInfo{
    // 构建启动消息获取配置
    NSMutableDictionary *bodyDic = [NSMutableDictionary new];
    [bodyDic setObject:[self getCommon] forKey:@"common"];
    
    NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
    NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
    
    NSString *response = [self securePost:@"/cross/rating" body:requestBody];
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
        [bodyDic setObject:[self nextStartMeta] forKey:@"meta"];
        
        NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
        NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
        
        NSString *response = [self securePost:@"/attach/start" body:requestBody];
        if (response == nil) {
            instanceStarted = NO;
            return;
        }
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
            instanceStarted = NO;
            return;
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
            instanceStarted = NO;
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
        [self prepareNextUploadBatch];
        if(_inflightMessages.count > 0){
            // 封装最终协议内容
            NSMutableDictionary *bodyDic = [NSMutableDictionary new];
            [bodyDic setObject:[self getCommon] forKey:@"common"];
            [bodyDic setObject:_inflightMessages forKey:@"data"];
            NSDictionary *uploadMeta = [self nextUploadMeta];
            [bodyDic setObject:uploadMeta forKey:@"meta"];
            
            NSError *error;
            NSData *jsonData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:&error];

            if (!jsonData) {
                NSLog(@"转化为 JSON 失败：%@", error);
            } else {
                NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
                NSLog(@"iOS upload batch %@ attempt %@ contains %lu messages: %@",
                      uploadMeta[@"batchId"], uploadMeta[@"attempt"],
                      (unsigned long)_inflightMessages.count,
                      [_inflightMessages valueForKey:@"messageId"]);
            }
            
            NSData *requestBodyData = [NSJSONSerialization dataWithJSONObject:bodyDic options:NSJSONWritingPrettyPrinted error:nil];
            NSString *requestBody = [[NSString alloc] initWithData:requestBodyData encoding:NSUTF8StringEncoding];
            
            NSString *response = [self securePost:@"/attach/upload" body:requestBody];
            if (response == nil) {
                NSLog(@"iOS upload batch %@ failed; retaining messages for retry", uploadMeta[@"batchId"]);
                return;
            }
            NSError *responseError = nil;
            NSDictionary *responseDic = [NSJSONSerialization JSONObjectWithData:[response dataUsingEncoding:NSUTF8StringEncoding]
                options:NSJSONReadingMutableContainers error:&responseError];
            if (responseError != nil || ![responseDic isKindOfClass:[NSDictionary class]]) {
                NSLog(@"iOS upload response is invalid; retaining batch %@ for retry", uploadMeta[@"batchId"]);
                return;
            }
            NSInteger status = [[responseDic valueForKey:@"status"] intValue];
            if(status == 1){
                NSArray<NSDictionary *> *retryMessages = [self retryableMessagesFromResponse:responseDic
                    expectedBatchID:uploadMeta[@"batchId"]];
                if (retryMessages == nil) {
                    NSLog(@"iOS upload ACK is invalid; retaining batch %@ for retry", uploadMeta[@"batchId"]);
                    return;
                }
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
                @synchronized(self) {
                    _inflightMessages = retryMessages;
                    if (_inflightMessages.count == 0) {
                        NSLog(@"iOS upload batch %@ acknowledged", uploadMeta[@"batchId"]);
                        _inflightMeta = nil;
                    } else {
                        NSLog(@"iOS upload batch %@ retained %lu retryable messages",
                              uploadMeta[@"batchId"], (unsigned long)_inflightMessages.count);
                    }
                }
            } else {
                NSLog(@"iOS upload batch %@ was rejected; retaining messages for retry", uploadMeta[@"batchId"]);
            }
            
        }
    }];
    [dispatchTask start];
    
    return YES;
}

- (NSString *)getCode{
    if(_code){
        // 属性不为空直接返回
        return _code;
    }
    CTDeviceInfo *deviceInfo = [CTDeviceInfo sharedInstance];
    _code = [[NSString alloc] initWithFormat:@"%@-%@-%@-%@",
        [deviceInfo guid], PLATFORM, APP_ID, SDK_VERSION];
    return _code;
}

- (NSString *)securePost:(NSString *)logicalPath body:(NSString *)body {
    NSData *response = [self.secureAdapter postWithLogicalPath:logicalPath
        code:[self getCode]
        body:[body dataUsingEncoding:NSUTF8StringEncoding]];
    if (response == nil) {
        return nil;
    }
    return [[NSString alloc] initWithData:response encoding:NSUTF8StringEncoding];
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

- (NSDictionary *)nextStartMeta {
    @synchronized(self) {
        if (_startMeta == nil) {
            NSNumber *now = @((NSInteger)round([[NSDate date] timeIntervalSince1970] * 1000));
            _startMeta = [@{
                @"protocolVersion": @1,
                @"messageId": [@"start-msg-" stringByAppendingString:[[NSUUID UUID] UUIDString]],
                @"batchId": [@"start-batch-" stringByAppendingString:[[NSUUID UUID] UUIDString]],
                @"sessionId": [[self getCommon][2] stringValue],
                @"attempt": @0,
                @"createdAt": now,
                @"observedAt": now,
                @"sequence": @1,
                @"appKeyId": _protocolAppKeyID,
                @"detectorVersion": SDK_VERSION
            } mutableCopy];
        }
        _startMeta[@"attempt"] = @([_startMeta[@"attempt"] integerValue] + 1);
        return [_startMeta copy];
    }
}

- (void)prepareNextUploadBatch {
    @synchronized(self) {
        if (_inflightMessages.count > 0) {
            return;
        }

        for (id rawMessage in [_checkerDataQueue dequeueAll]) {
            if (![rawMessage isKindOfClass:[NSDictionary class]]) {
                NSLog(@"discarding non-object iOS fact");
                continue;
            }
            NSMutableDictionary *message = [rawMessage mutableCopy];
            if (![message[@"type"] isKindOfClass:[NSString class]]) {
                NSLog(@"discarding iOS fact without type");
                continue;
            }
            if (![message[@"messageId"] isKindOfClass:[NSString class]]) {
                message[@"messageId"] = [@"ios-msg-" stringByAppendingString:[[NSUUID UUID] UUIDString]];
                message[@"observedAt"] = @((long long)round([[NSDate date] timeIntervalSince1970] * 1000));
                message[@"sequence"] = @(_nextMessageSequence++);
            }
            NSError *serializationError = nil;
            NSData *serialized = [NSJSONSerialization dataWithJSONObject:message options:0 error:&serializationError];
            if (serialized == nil || serializationError != nil) {
                NSLog(@"discarding non-serializable iOS fact: %@", serializationError.localizedDescription);
                continue;
            }
            if (serialized.length > MAX_UPLOAD_BODY_BYTES - 8192) {
                NSLog(@"discarding oversized iOS message %@ (%lu bytes)",
                      message[@"messageId"], (unsigned long)serialized.length);
                continue;
            }
            [_pendingMessages addObject:[message copy]];
        }

        if (_pendingMessages.count == 0) {
            return;
        }

        NSMutableArray<NSDictionary *> *batch = [NSMutableArray new];
        NSUInteger estimatedBytes = 8192;
        while (_pendingMessages.count > 0 && batch.count < MAX_UPLOAD_FACTS) {
            NSDictionary *candidate = _pendingMessages.firstObject;
            NSData *serialized = [NSJSONSerialization dataWithJSONObject:candidate options:0 error:nil];
            if (batch.count > 0 && estimatedBytes + serialized.length > MAX_UPLOAD_BODY_BYTES) {
                break;
            }
            [batch addObject:candidate];
            [_pendingMessages removeObjectAtIndex:0];
            estimatedBytes += serialized.length;
        }

        NSNumber *now = @((long long)round([[NSDate date] timeIntervalSince1970] * 1000));
        _inflightMessages = [batch copy];
        _inflightMeta = [@{
            @"protocolVersion": @1,
            @"batchId": [@"ios-batch-" stringByAppendingString:[[NSUUID UUID] UUIDString]],
            @"sessionId": [[self getCommon][2] stringValue],
            @"attempt": @0,
            @"createdAt": now,
            @"appKeyId": _protocolAppKeyID,
            @"detectorVersion": SDK_VERSION
        } mutableCopy];
    }
}

- (NSDictionary *)nextUploadMeta {
    @synchronized(self) {
        _inflightMeta[@"attempt"] = @([_inflightMeta[@"attempt"] integerValue] + 1);
        return [_inflightMeta copy];
    }
}

- (NSArray<NSDictionary *> *)retryableMessagesFromResponse:(NSDictionary *)response
                                          expectedBatchID:(NSString *)expectedBatchID {
    NSDictionary *ack = response[@"ack"];
    if (![ack isKindOfClass:[NSDictionary class]] || ![ack[@"batchId"] isEqualToString:expectedBatchID]
            || ![ack[@"acceptedMessageIds"] isKindOfClass:[NSArray class]]
            || ![ack[@"duplicateMessageIds"] isKindOfClass:[NSArray class]]
            || ![ack[@"rejectedItems"] isKindOfClass:[NSArray class]]) {
        return nil;
    }

    NSMutableSet<NSString *> *expected = [NSMutableSet new];
    for (NSDictionary *message in _inflightMessages) {
        [expected addObject:message[@"messageId"]];
    }
    NSMutableSet<NSString *> *completed = [NSMutableSet setWithArray:ack[@"acceptedMessageIds"]];
    [completed addObjectsFromArray:ack[@"duplicateMessageIds"]];
    NSMutableSet<NSString *> *retryable = [NSMutableSet new];
    NSMutableSet<NSString *> *permanent = [NSMutableSet new];
    NSMutableSet<NSString *> *classified = [completed mutableCopy];

    for (id rawRejection in ack[@"rejectedItems"]) {
        if (![rawRejection isKindOfClass:[NSDictionary class]]) {
            return nil;
        }
        NSString *messageID = rawRejection[@"messageId"];
        if (![messageID isKindOfClass:[NSString class]] || [classified containsObject:messageID]) {
            return nil;
        }
        [classified addObject:messageID];
        if ([rawRejection[@"retryable"] boolValue]) {
            [retryable addObject:messageID];
        } else {
            [permanent addObject:messageID];
            NSLog(@"server permanently rejected iOS message %@: %@", messageID, rawRejection[@"reason"]);
        }
    }
    for (NSString *messageID in classified) {
        if (![expected containsObject:messageID]) {
            return nil;
        }
    }

    NSMutableArray<NSDictionary *> *messages = [NSMutableArray new];
    for (NSDictionary *message in _inflightMessages) {
        NSString *messageID = message[@"messageId"];
        if ([retryable containsObject:messageID]
                || (![completed containsObject:messageID] && ![permanent containsObject:messageID])) {
            [messages addObject:message];
        }
    }
    return messages;
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
