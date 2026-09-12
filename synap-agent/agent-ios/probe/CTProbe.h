//
//  probe.h
//  probe
//
//  Created by coolxer on 2024/3/7.
//

#import <Foundation/Foundation.h>

//! Project version number for probe.
FOUNDATION_EXPORT double probeVersionNumber;

//! Project version string for probe.
FOUNDATION_EXPORT const unsigned char probeVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <probe/PublicHeader.h>


NS_ASSUME_NONNULL_BEGIN

typedef NSString * _Nullable (^CTEnrollmentTokenProvider)(void);

@interface CTProbe : NSObject
+ (instancetype)sharedInstance;

+ (BOOL)initWithViewController:(id)selfView
                       baseURL:(NSURL *)baseURL
                         appID:(NSString *)appID
                   serverKeyID:(NSString *)serverKeyID
                   trustAnchor:(NSString *)trustAnchor
       enrollmentTokenProvider:(nullable CTEnrollmentTokenProvider)enrollmentTokenProvider;
- (void) start;
- (BOOL) userData:(NSString *)userId withUserData:(NSDictionary *) data;
- (NSString *) deviceInfo;
- (NSString *) labelInfo;
- (NSString *) ratingInfo;

@end

NS_ASSUME_NONNULL_END
