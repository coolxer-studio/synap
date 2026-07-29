//
//  CTBaseViewController.m
//  probe-ios
//
//  Created by Young on 2023/6/1.
//

#import "CTBaseViewController.h"
#import "CTToolViewController.h"

@interface CTBaseViewController ()<UITableViewDelegate,UITableViewDataSource>

@end

@implementation CTBaseViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.view.backgroundColor=gray_200;
    // 设置navigation标题为app名字
//    NSDictionary *infoDictionary = [[NSBundle mainBundle] infoDictionary];
//    NSString *appName = [infoDictionary objectForKey:@"CFBundleDisplayName"];
//    self.navigationItem.title = appName;
    
    // 添加工具栏按钮
    UIImage *toolImage = [[UIImage imageNamed:@"ic_tool"] imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
    UIBarButtonItem *toolButtonItem = [[UIBarButtonItem alloc] initWithImage:toolImage style:UIBarButtonItemStyleDone target:self action:@selector(toolButtonAction) ];
    self.navigationItem.rightBarButtonItems = @[toolButtonItem];

}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

// 工具箱按钮的点击事件
- (void)toolButtonAction {
    // 处理按钮点击事件的代码
    
    CTToolViewController *toolViewController = [[CTToolViewController alloc]init];
    
    [self.navigationController pushViewController:toolViewController animated:YES];
}
#pragma mark - TableView delegate
-(NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}
- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return 0;
}
-(void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    
}
- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    return nil;
}
-(CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section
{
    return 0;
}
#pragma mark - 懒加载区域
- (UITableView *)tableView {
    if (!_tableView) {
        _tableView = [[UITableView alloc] init];
        _tableView.delegate = self;
        _tableView.dataSource = self;
        _tableView.backgroundColor = [UIColor clearColor];
        _tableView.separatorStyle = UITableViewCellSeparatorStyleNone;
    }
    return _tableView;
}
@end
