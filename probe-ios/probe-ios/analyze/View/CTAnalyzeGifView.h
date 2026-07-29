//
//  CTAnalyzeGifView.h
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import "CTBaseView.h"
#import "CTAnalyzeModel.h"
NS_ASSUME_NONNULL_BEGIN

@interface CTAnalyzeGifView : CTBaseView
/// set风险分析系统打分模型
@property (nonatomic, strong) CTAnalyzeModel *analyzeModel;
@end

NS_ASSUME_NONNULL_END
