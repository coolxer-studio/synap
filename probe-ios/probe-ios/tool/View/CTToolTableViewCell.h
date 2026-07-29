//
//  CTToolTableViewCell.h
//  probe-ios
//
//  Created by Young on 2024/2/28.
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTToolTableViewCell : UITableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView;
/// 设置数据
@property (nonatomic, strong) NSDictionary *sourceDic;
@end

NS_ASSUME_NONNULL_END
