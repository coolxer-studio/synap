//
//  CTDeviceDetailModel.h
//  probe-ios
//
//  Created by Young on 2023/10/18.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTDeviceDetailModel : NSObject
/// 类型
@property (nonatomic, copy) NSString *type;
/// 检测创建时间
@property (nonatomic, copy) NSString *build_date;
/// 常规参数
@property (nonatomic, copy) NSString *common;
/// 设备信息
@property (nonatomic, copy) NSString *device;
/// 屏幕
@property (nonatomic, copy) NSString *screen;
/// CPU
@property (nonatomic, copy) NSString *cpu;
/// 电池
@property (nonatomic, copy) NSString *battery;
/// 设备唯一标识ID
@property (nonatomic, copy) NSString *uuid;
/// 网络
@property (nonatomic, copy) NSString *network;
/// 存储
@property (nonatomic, copy) NSString *storage;
/// sysctl
@property (nonatomic, copy) NSString *sysctl;
@end

NS_ASSUME_NONNULL_END
