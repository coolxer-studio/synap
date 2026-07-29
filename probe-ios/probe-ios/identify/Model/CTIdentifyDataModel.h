//
//  CTIdentifyDataModel.h
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTIdentifyDataModel : NSObject
/// 类型：高中低其他
@property (nonatomic, copy) NSString *type;
/// 具体内容list
@property (nonatomic, copy) NSArray *value_list;
@end

NS_ASSUME_NONNULL_END
