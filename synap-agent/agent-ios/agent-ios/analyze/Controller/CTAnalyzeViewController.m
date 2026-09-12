//
//  CTAnalyzeViewController.m
//  agent-ios
//
//  Created by Young on 2023/6/2.
//

#import "CTAnalyzeViewController.h"
#import "CTProbe.h"
#import "CTAnalyzeGifView.h"
#import "CTAnalyzeModel.h"
@interface CTAnalyzeViewController ()
/// 动效view
@property (nonatomic, strong) CTAnalyzeGifView *analyzeGifView;
/// 底部imgView
@property (nonatomic, strong) UIImageView *analyzeBottomImgView;
@end

@implementation CTAnalyzeViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    [self setUpSubView];
}

- (void)viewWillAppear:(BOOL)animated{
    [super viewWillAppear:animated];
    //接口请求数据
    NSDictionary*dic=[CTNormalTools jsonStringToDic:[[CTProbe sharedInstance] ratingInfo]];
    //转模型
    CTAnalyzeModel*analyzeModel=[[CTExtension shareExtension] ctObjectWithKeyValues:dic[@"data"] toModel:[CTAnalyzeModel class]];
    //设置模型数据在view显示
    [self.analyzeGifView setAnalyzeModel:analyzeModel];
}

#pragma mark - 设置view和UI
-(void)setUpSubView
{
    [self.view addSubview:self.analyzeGifView];
    [self.view addSubview:self.analyzeBottomImgView];
    
    self.analyzeBottomImgView.frame=CGRectMake(0, screenHeight-navBarHeight-tabBarHeight-layoutWidth(69), screenWidth, layoutWidth(69));
}

#pragma mark - lazy
- (UIView *)analyzeGifView {
    if (!_analyzeGifView) {
        _analyzeGifView = [[CTAnalyzeGifView alloc] initWithFrame:CGRectMake(0, (screenHeight-screenWidth)/2-navBarHeight, screenWidth, screenWidth)];
    }
    return _analyzeGifView;
}
- (UIImageView *)analyzeBottomImgView {
    if (!_analyzeBottomImgView) {
        _analyzeBottomImgView = [[UIImageView alloc] init];
        _analyzeBottomImgView.image = [UIImage imageNamed:@"ic_analyze_background_down"];
    }
    return _analyzeBottomImgView;
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
