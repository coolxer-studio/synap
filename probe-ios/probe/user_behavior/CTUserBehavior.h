//
//  CTUserBehavior.h
//
//  Created by yaoqi.li on 2023/2/26.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
//! Project version number for device_info.
FOUNDATION_EXPORT double CTDeviceInfoVersionNumber;

//! Project version string for device_info.
FOUNDATION_EXPORT const unsigned char CTDeviceInfoVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <device_info/PublicHeader.h>

typedef void (^UBCallBack)(NSString * _Nullable message);

@interface CTUserBehavior : NSObject
+ (instancetype)sharedInstance;

- (BOOL)loadConfig:(NSString *) config;

- (BOOL)registerCallBackWithDataBlock:(UBCallBack _Nullable )listener;
- (BOOL)startTrack;


@end
