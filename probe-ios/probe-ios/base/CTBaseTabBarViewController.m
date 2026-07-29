//
//  CTBaseTabBarViewController.m
//  probe-ios
//
//  Created by Young on 2023/6/1.
//

#import "CTBaseTabBarViewController.h"
#import "CTNavigationViewController.h"
#import "CTIdentifyViewController.h"
#import "CTDeviceViewController.h"
#import "CTAnalyzeViewController.h"
#import "CTProbe.h"


@interface CTBaseTabBarViewController ()<UINavigationControllerDelegate>

@end

@implementation CTBaseTabBarViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.tabBar.backgroundColor=gray_200;
    [self setupAllChildViewControllers];
    
    BOOL initialized = [CTProbe initWithViewController:self];
    if (initialized){
        [[CTProbe sharedInstance] start];
        NSLog(@"CTProbe start");
    
    }
}
/**
 *  初始化所有的子控制器
 */
- (void)setupAllChildViewControllers
{
    // 设备信息
    CTDeviceViewController *deviceVC = [[CTDeviceViewController alloc] init];
    [self setupChildViewController:deviceVC title:@"设备信息" imageName:@"ic_device" selectedImageName:@"ic_device"];
    // 评级指数
    CTAnalyzeViewController *analyzeVC = [[CTAnalyzeViewController alloc] init];
    [self setupChildViewController:analyzeVC title:@"评级指数" imageName:@"ic_analyze" selectedImageName:@"ic_analyze"];
    // 设备标签
    CTIdentifyViewController *identifyVC = [[CTIdentifyViewController alloc] init];
    [self setupChildViewController:identifyVC title:@"设备标签" imageName:@"ic_identify" selectedImageName:@"ic_identify"];
    
    if(@available(iOS 15.0, *)) {
        UITabBarAppearance *apperarance = [[UITabBarAppearance alloc] init];
        [apperarance configureWithOpaqueBackground];
        apperarance.backgroundColor = [UIColor whiteColor];
        self.tabBar.standardAppearance = apperarance;
        self.tabBar.scrollEdgeAppearance = apperarance;
    }
    
//    [[UITabBarItem appearance] setTitleTextAttributes:@{ NSForegroundColorAttributeName : RGB(153, 153, 153),NSFontAttributeName:[UIFont fontWithName:@"Marion-Italic" size:8]}            forState:UIControlStateNormal];
//    [[UITabBarItem appearance] setTitleTextAttributes:@{ NSForegroundColorAttributeName :RGB(254, 3, 6),NSFontAttributeName:[UIFont fontWithName:@"Marion-Italic" size:8]}            forState:UIControlStateSelected];
    
}
/**
 *  初始化一个子控制器
 *
 *  @param childVc           需要初始化的子控制器
 *  @param title             标题
 *  @param imageName         图标
 *  @param selectedImageName 选中的图标
 */
- (void)setupChildViewController:(UIViewController *)childVc title:(NSString *)title imageName:(NSString *)imageName selectedImageName:(NSString *)selectedImageName
{
    
    // 设置控制器的属性
    childVc.title = title;
    // 设置图标
    childVc.tabBarItem.image = [UIImage imageNamed:imageName];
    // 设置选中的图标
    UIImage *selectedImage = [UIImage imageNamed:selectedImageName];
    childVc.tabBarItem.selectedImage = [selectedImage imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
    
    // 1.包装一个导航控制器
    CTNavigationViewController *nav = [[CTNavigationViewController alloc] initWithRootViewController:childVc];
    nav.delegate=self;
    //设置导航栏透明
    [nav.navigationBar setTranslucent:false];
    
    [self addChildViewController:nav];
    
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
