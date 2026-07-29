//
//  CTNavigationViewController.m
//  probe-ios
//
//  Created by Young on 2023/6/1.
//

#import "CTNavigationViewController.h"

@interface CTNavigationViewController ()

@end

@implementation CTNavigationViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.view.backgroundColor=[UIColor whiteColor];
}

 //第一次使用这个类的时候会调用(1个类只会调用1次)
+ (void)initialize
{
    // 1.设置导航栏主题
    [self setupNavBarTheme];
}
/**
 *  设置导航栏主题
 */
+ (void)setupNavBarTheme
{
    UINavigationBar *navBar = [UINavigationBar appearance];
    
    //设置状态栏背景
    [UIApplication sharedApplication].statusBarStyle = UIStatusBarStyleLightContent;
    if (@available(iOS 13.0, *)) {
        UINavigationBarAppearance *appearance = [[UINavigationBarAppearance alloc] init];
        [appearance configureWithOpaqueBackground];
        NSMutableDictionary *textAttribute = [NSMutableDictionary dictionary];
        textAttribute[NSForegroundColorAttributeName] = RGB(255, 255, 255);//标题颜色
        textAttribute[NSFontAttributeName] = [UIFont boldSystemFontOfSize:15];//标题大小
        [appearance setTitleTextAttributes:textAttribute];
        
        //去除底部黑线
        [appearance setShadowImage:[UIImage new]];
        
//        UIColor *color = MPMainColor;
        appearance.backgroundColor = blue_500;
        
        navBar.standardAppearance = appearance;
        navBar.scrollEdgeAppearance = appearance;
    } else {
        // Fallback on earlier versions
        //设置导航栏背景颜色
        [navBar setBackgroundColor: blue_500];
        [navBar setBarTintColor:blue_500];
        //导航栏字体颜色和大小
        NSMutableDictionary *textAttrs = [NSMutableDictionary dictionary];
        textAttrs[NSForegroundColorAttributeName] = RGB(255, 255, 255);
        textAttrs[NSFontAttributeName] =[UIFont systemFontOfSize:15];
        [navBar setTitleTextAttributes:textAttrs];
    }
    
}

- (void)pushViewController:(UIViewController *)viewController animated:(BOOL)animated
{
    //如果有二级页面切换隐藏底部tabbar
    if (self.viewControllers.count > 0) {
        viewController.hidesBottomBarWhenPushed = YES;
    }
    [super pushViewController:viewController animated:animated];
    [self setNavigationBarHidden:NO animated:YES];
}

-(void)backVcAction
{
    [self popViewControllerAnimated:YES];
}
/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

@end
