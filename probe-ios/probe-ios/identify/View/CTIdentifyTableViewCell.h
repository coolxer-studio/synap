//
//  CTIdentifyTableViewCell.h
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import <UIKit/UIKit.h>
#import "CTIdentifyDataModel.h"
NS_ASSUME_NONNULL_BEGIN

@interface CTIdentifyTableViewCell : UITableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView;
/// 设置威胁识别模型数据
@property (nonatomic, strong) CTIdentifyDataModel *identifyDataModel;
@end

NS_ASSUME_NONNULL_END
