//
//  CTIdentifyViewController.m
//  agent-ios
//
//  Created by Young on 2023/6/1.
//

#import "CTIdentifyViewController.h"
#import "CTProbe.h"
#import "CTIdentifyDataModel.h"
#import "CTIdentifyTableViewCell.h"
@interface CTIdentifyViewController ()
/// 数据数组
@property (nonatomic, strong) NSMutableArray *identifyMutableArray;
@end

@implementation CTIdentifyViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.tableView.frame=CGRectMake(0, 0, screenWidth, screenHeight-navBarHeight-tabBarHeight);
    [self.view addSubview:self.tableView];
}

- (void)viewWillAppear:(BOOL)animated{
    [super viewWillAppear:animated];
    
    NSLog(@"label:%@",[[CTProbe sharedInstance] labelInfo]);
    NSDictionary*dic=[CTNormalTools jsonStringToDic:[[CTProbe sharedInstance] labelInfo]];
    self.identifyMutableArray=[[[CTExtension shareExtension] convertDictionaryArray:dic[@"data"] toModel:[CTIdentifyDataModel class]] mutableCopy];
    [self.tableView reloadData];
}
#pragma mark - 处理数据
-(CTIdentifyDataModel*)checkSourceName:(NSString*)sourceName
{
    
    for (CTIdentifyDataModel*model in self.identifyMutableArray) {
        if ([model.type isEqual:sourceName]) {
            return model;
        }
    }
    
    CTIdentifyDataModel*resultModel=[[CTIdentifyDataModel alloc] init];
    resultModel.type=sourceName;
    return resultModel;
}
#pragma mark - TableView delegate
-(NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    //在这里前端写死数据数量然后渲染，理论上应该接口按顺序返回，没有也返回类别比较合适。
    return 5;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    CTIdentifyTableViewCell*cell=[CTIdentifyTableViewCell cellWithTableView:tableView];
    cell.selectionStyle=UITableViewCellSelectionStyleNone;
    if (indexPath.row==0) {
        [cell setIdentifyDataModel:[self checkSourceName:@"high"]];
    }
    else if (indexPath.row==1) {
        [cell setIdentifyDataModel:[self checkSourceName:@"medium"]];
    }
    else if (indexPath.row==2) {
        [cell setIdentifyDataModel:[self checkSourceName:@"low"]];
    }
    else if (indexPath.row==3) {
        [cell setIdentifyDataModel:[self checkSourceName:@"normal"]];
    }
    else
    {
        [cell setIdentifyDataModel:[self checkSourceName:@"other"]];
    }
    
    return cell;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    CTIdentifyDataModel*model;
    if (indexPath.row==0) {
        model=[self checkSourceName:@"high"];
    }
    else if (indexPath.row==1) {
        model=[self checkSourceName:@"medium"];
    }
    else if (indexPath.row==2) {
        model= [self checkSourceName:@"low"];
    }
    else if (indexPath.row==3) {
        model= [self checkSourceName:@"normal"];
    }
    else
    {
        model=[self checkSourceName:@"other"];
    }
    return [self getCellHeight:model];
}
/// 因为没有用约束布局，计算获取cell的高度
-(CGFloat)getCellHeight:(CTIdentifyDataModel*)model
{
    CGFloat h=layoutWidth(120);
    
    
    CGFloat xPosition = layoutWidth(20);
    CGFloat yPosition = layoutWidth(100);
    CGFloat buttonSpacing = layoutWidth(10);
    
    for (int i=0; i<model.value_list.count; i++) {
        NSString*contentStr=model.value_list[i];
        CGSize textSize = [contentStr sizeWithAttributes:@{NSFontAttributeName: [UIFont boldSystemFontOfSize:layoutFont(24)]}];
        // 更新 xPosition
        xPosition += textSize.width + layoutWidth(20) + buttonSpacing;
        // 如果按钮的右侧超出屏幕，换行
        if (xPosition + textSize.width + buttonSpacing > CGRectGetWidth(self.view.bounds)-layoutWidth(40)) {
            xPosition = layoutWidth(20);
            yPosition += layoutWidth(60); // 假设按钮高度为 30，添加一些额外的间距
        }
    }
    h=yPosition+layoutWidth(80);
    return h;
}
#pragma mark - lazy
-(NSMutableArray *)identifyMutableArray
{
    if (!_identifyMutableArray) {
        _identifyMutableArray=[[NSMutableArray alloc] init];
    }
    return _identifyMutableArray;
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
