//
//  CTRunningCheck.h
//  running-check
//
//  Created by yaoqi.li on 2022/12/11.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

//! Project version number for safety.
FOUNDATION_EXPORT double ctRunningCheckVersionNumber;

//! Project version string for safety.
FOUNDATION_EXPORT const unsigned char ctRunningCheckVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <safety/PublicHeader.h>

@interface CTRunningCheck : NSObject
+ (instancetype)sharedInstance;

- (BOOL)loadConfig:(NSString *) config;

- (NSString *)getDeviceInfo;
- (NSString *)getNetInfo;
- (NSString *)getSelfAppInfo;
- (NSString *)getFileInfo;
- (NSString *)getRunTimeInfo;
- (NSString *)getAppInfo;
- (NSString *)getLocationInfo;
- (NSString *)getInjectInfo;

- (NSArray *)baseAppInfo;
- (NSDictionary *)queryLatestLocation;
- (NSString *) wanIp;
@end
