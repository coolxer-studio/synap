//
//  CTDeviceContentModel.h
//  probe-ios
//
//  Created by Young on 2023/10/18.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTDeviceContentModel : NSObject
/// sectionName
@property (nonatomic, copy) NSString *sectionName;
/// sectionIcon
@property (nonatomic, copy) NSString *sectionIcon;
/// sectionContent
@property (nonatomic, copy) NSString *sectionContent;
@end

NS_ASSUME_NONNULL_END
