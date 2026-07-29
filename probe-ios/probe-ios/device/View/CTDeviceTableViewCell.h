//
//  CTDeviceTableViewCell.h
//  probe-ios
//
//  Created by Young on 2023/10/18.
//

#import <UIKit/UIKit.h>
#import "CTDeviceContentModel.h"
NS_ASSUME_NONNULL_BEGIN

@interface CTDeviceTableViewCell : UITableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView;

/// 设置cell内容,后续数据结构调整后可使用
@property (nonatomic, strong) CTDeviceContentModel *contentModel;

//根据当前数据结构临时使用的方法
-(void)setContentString:(NSString*)contentStr sectionType:(NSString*)sectionType sectionType:(NSString*)sectionIcon;

@end

NS_ASSUME_NONNULL_END
