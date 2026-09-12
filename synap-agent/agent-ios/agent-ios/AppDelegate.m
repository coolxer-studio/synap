//
//  AppDelegate.m
//  agent-ios
//
//  Created by yaoqi.li on 2023/3/7.
//

#import "AppDelegate.h"
#import "CTBaseTabBarViewController.h"
@interface AppDelegate ()

@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Override point for customization after application launch.
    CGRect bounds = [[UIScreen mainScreen] bounds];
    self.window = [[UIWindow alloc] initWithFrame: bounds];
    self.window.backgroundColor=[UIColor whiteColor];
    CTBaseTabBarViewController*tabBarVC=[[CTBaseTabBarViewController alloc] init];
    self.window.rootViewController = tabBarVC;
    [self.window makeKeyAndVisible];
    
    return YES;
}



@end
