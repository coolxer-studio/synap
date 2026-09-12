//
//  punishment.h
//  punishment
//
//  Created by yaoqi.li on 2023/5/19.
//

#import <Foundation/Foundation.h>

//! Project version number for punishment.
FOUNDATION_EXPORT double punishmentVersionNumber;

//! Project version string for punishment.
FOUNDATION_EXPORT const unsigned char punishmentVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <punishment/PublicHeader.h>


NS_ASSUME_NONNULL_BEGIN

@interface Reminder : NSObject

+ (void)showAlertDialogWithTitle:(NSString *)title message:(NSString *)message selfView:(id)selfView;
+ (void)showToastWithMessage:(NSString *)message;
/// 本地通知形式展示，需在appdelegate中调用registerLocationNotificationAuth注册开启系统本地通知权限，或者在prob初始化是设置开启。否则此方法无效
/// - Parameters:
///   - title: 通知名称
///   - message: 通知message内容
+ (void)showNotificationWithTitle:(NSString *)title message:(NSString *)message;
/// 注册本地通知系统权限，不开启无法显示通知
+ (void)registerLocationNotificationAuth;
@end

@interface Redirect : NSObject

+ (void)openView:(NSString *)viewName storyboardName:(NSString *)storyboardName selfView:(id)selfView;

+ (void)openBrowserWithUrl:(NSString *)Url;

+ (void)openAppWithUrlScheme:(NSString *)urlScheme;

@end

@interface Interrupt : NSObject

+ (void)exit;

+ (void)exitWithTitle:(NSString *)title message:(NSString *)message selfView:(id)selfView;

@end


NS_ASSUME_NONNULL_END
