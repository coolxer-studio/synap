//
//  CTDeviceBaseModel.h
//  probe-ios
//
//  Created by Young on 2023/10/17.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTDeviceBaseModel : NSObject
/// 实际检测的数据（目前模型仅支持单层转化，所以暂时设置为string类型）
@property (nonatomic, copy) NSDictionary *fact;
/// 服务检测时间
@property (nonatomic, copy) NSString *server_time;
@end

NS_ASSUME_NONNULL_END
