//
//  CTToolViewController.m
//  agent-ios
//
//  Created by yaoqi.li on 2023/10/11.
//

#import "CTToolViewController.h"
#import "CTLoginViewController.h"
#import "CTToolTableViewCell.h"
#import "CrashViewController.h"

#import "agent_ios-Swift.h"
#import "CCrash.h"
@interface CTToolViewController ()
/// 设置数据
@property (nonatomic, strong) NSArray *sourceArray;
@end

@implementation CTToolViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.navigationItem.title=@"工具箱";
    //设置数据
    self.sourceArray=@[@{@"name":@"OC Crash错误",@"iconImg":@"ic_tool_help",@"explain":@"字典插入空数据，触发异常"},@{@"name":@"Swift Crash错误",@"iconImg":@"ic_tool_help",@"explain":@"隐式解包的可选类型的字符串为空使用时，触发异常"},@{@"name":@"C Crash错误",@"iconImg":@"ic_tool_help",@"explain":@"数据类型声明定义不正确未分配内存，触发异常"},@{@"name":@"模拟登录",@"iconImg":@"ic_tool_help",@"explain":@"模拟用户登录行为，验证用户数据接口"}];
    self.tableView.frame=CGRectMake(0, layoutWidth(20), screenWidth, screenHeight-navBarHeight-tabBarHeight);
    [self.view addSubview:self.tableView];
    
}
#pragma mark - TableView delegate
-(NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.sourceArray.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    CTToolTableViewCell*cell=[CTToolTableViewCell cellWithTableView:tableView];
    cell.selectionStyle=UITableViewCellSelectionStyleNone;
    NSDictionary*dic=self.sourceArray[indexPath.row];
    [cell setSourceDic:dic];
    return cell;
}
-(void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    NSDictionary*dic=self.sourceArray[indexPath.row];
    if ([dic[@"name"] isEqual:@"OC Crash错误"]) {
        NSString *test;
        NSMutableDictionary *dic = [NSMutableDictionary new];
        [dic setObject:test forKey:@"value-for-nil"];
    }
    else if ([dic[@"name"] isEqual:@"Swift Crash错误"])
    {
        CrashSwift *cs = [CrashSwift new];
        [cs crash];
    }
    else if ([dic[@"name"] isEqual:@"C Crash错误"])
    {
        crash();
    }
    else if ([dic[@"name"] isEqual:@"模拟登录"])
    {
        CTLoginViewController*logVC=[[CTLoginViewController alloc] init];
        [self.navigationController pushViewController:logVC animated:YES];
    }
    
}
- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
 
    return layoutWidth(100);
}

- (void)loginButtonTapped{
    // 处理按钮点击事件
    
    CTLoginViewController *loginViewController = [[CTLoginViewController alloc]init];
    
    [self.navigationController pushViewController:loginViewController animated:YES];
    
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
