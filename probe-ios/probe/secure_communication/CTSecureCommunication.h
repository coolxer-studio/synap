//
//  sc.h
//  sc
//
//  Created by yaoqi.li on 2022/8/10.
//

#import <Foundation/Foundation.h>

//! Project version number for sc.
FOUNDATION_EXPORT double scVersionNumber;

//! Project version string for sc.
FOUNDATION_EXPORT const unsigned char scVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <sc/PublicHeader.h>

@interface CTSecureCommunication : NSObject

+ (NSString * _Nullable )get:(NSString * _Nonnull)uri withHeader:(NSString * _Nullable)header;
+ (NSString * _Nullable)post:(NSString *_Nonnull)uri withHeader:(NSString * _Nullable)header withBody:(NSString *_Nonnull)body;

@end
