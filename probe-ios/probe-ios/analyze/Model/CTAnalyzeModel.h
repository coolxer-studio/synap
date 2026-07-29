//
//  CTAnalyzeModel.h
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTAnalyzeModel : NSObject
/// guid
@property (nonatomic, copy) NSString *guid;
/// appId
@property (nonatomic, copy) NSString *appId;
/// 风险码code
@property (nonatomic, copy) NSString *ratingCode;
/// 打分
@property (nonatomic, copy) NSString *score;
/// 风险等级
@property (nonatomic, copy) NSString *grade;
@end

NS_ASSUME_NONNULL_END
