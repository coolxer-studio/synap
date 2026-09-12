//
//  collectorlog.h
//  collectorlog
//
//  Created by yaoqi.li on 2022/12/31.
//

#import <Foundation/Foundation.h>

//! Project version number for collectorlog.
FOUNDATION_EXPORT double collectorlogVersionNumber;

//! Project version string for collectorlog.
FOUNDATION_EXPORT const unsigned char collectorlogVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <collectorlog/PublicHeader.h>

@interface CTIllegalState :NSObject
+ (instancetype)sharedInstance;

- (BOOL)loadConfig:(NSString *) config;

- (BOOL)startTrack;
- (NSString *)check;


@end
