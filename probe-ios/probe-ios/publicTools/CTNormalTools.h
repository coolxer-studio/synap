//
//  CTNormalTools.h
//  probe-ios
//
//  Created by Young on 2023/10/17.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTNormalTools : NSObject
/// json转字典
/// - Parameter jsonString: jsonSting
+(id)jsonStringToDic:(NSString *)jsonString;
@end

NS_ASSUME_NONNULL_END
