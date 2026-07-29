//
//  CTDeviceInfoViewController.m
//  probe-ios
//
//  Created by Young on 2023/6/2.
//

#import "CTDeviceViewController.h"
#import "CTProbe.h"
#import "CTDeviceBaseModel.h"
#import "CTDeviceDetailModel.h"
#import "CTDeviceTableViewCell.h"
@interface CTDeviceViewController ()
/// 不是Young写的不知道做啥用了，目测没啥用
@property (nonatomic, strong) UIScrollView *scrollView;

/// detail模型
@property (nonatomic, strong) CTDeviceDetailModel *detailModel;
@end

@implementation CTDeviceViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.tableView.frame=CGRectMake(0, 0, screenWidth, screenHeight-navBarHeight-tabBarHeight);
    [self.view addSubview:self.tableView];
}

- (void)viewWillAppear:(BOOL)animated{
    [super viewWillAppear:animated];
    
    NSLog(@"deviceInfo:%@",[[CTProbe sharedInstance] deviceInfo]);
    NSDictionary*dic=[CTNormalTools jsonStringToDic:[[CTProbe sharedInstance] deviceInfo]];
    CTDeviceBaseModel*baseModel=[[CTExtension shareExtension] ctObjectWithKeyValues:dic[@"data"] toModel:[CTDeviceBaseModel class]];

    self.detailModel=[[CTExtension shareExtension] ctObjectWithKeyValues:baseModel.fact toModel:[CTDeviceDetailModel class]];
    [self.tableView reloadData];
}




#pragma mark - TableView delegate
-(NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return 8;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    CTDeviceTableViewCell*cell=[CTDeviceTableViewCell cellWithTableView:tableView];
    cell.selectionStyle=UITableViewCellSelectionStyleNone;
    if (indexPath.row==0) {
        [cell setContentString:self.detailModel.device sectionType:@"设备信息" sectionType:@"ic_device_hardware"];
    }
    else if (indexPath.row==1)
    {
        [cell setContentString:self.detailModel.cpu sectionType:@"CPU" sectionType:@"ic_device_system"];
    }
    else if (indexPath.row==2)
    {
        [cell setContentString:self.detailModel.storage sectionType:@"存储" sectionType:@"ic_device_storage"];
    }
    else if (indexPath.row==3)
    {
        [cell setContentString:self.detailModel.network sectionType:@"网络" sectionType:@"ic_device_network"];
    }
    else if (indexPath.row==4)
    {
        [cell setContentString:self.detailModel.battery sectionType:@"电池" sectionType:@"ic_device_battery"];
    }
    else if (indexPath.row==5)
    {
        [cell setContentString:self.detailModel.screen sectionType:@"屏幕" sectionType:@"ic_device_screen"];
    }
    else if (indexPath.row==6)
    {
        [cell setContentString:self.detailModel.common sectionType:@"基础信息" sectionType:@"ic_device_screen"];
    }
    else if (indexPath.row==7)
    {
        [cell setContentString:self.detailModel.uuid sectionType:@"其它" sectionType:@"ic_device_screen"];
    }
    else
    {
        
    }
    
    return cell;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
 
    if (indexPath.row==0) {
        return [self getCellHeight:self.detailModel.device];
    }
    else if (indexPath.row==1)
    {
        return [self getCellHeight:self.detailModel.cpu];
    }
    else if (indexPath.row==2)
    {
        return [self getCellHeight:self.detailModel.storage];
    }
    else if (indexPath.row==3)
    {
        return [self getCellHeight:self.detailModel.network];
    }
    else if (indexPath.row==4)
    {
        return [self getCellHeight:self.detailModel.battery];
    }
    else if (indexPath.row==5)
    {
        return [self getCellHeight:self.detailModel.screen];
    }
    else if (indexPath.row==6)
    {
        return [self getCellHeight:self.detailModel.common];
    }
    else
    {
        return [self getCellHeight:self.detailModel.uuid];
    }
}
-(CGFloat)getCellHeight:(NSString*)str
{
    CGFloat h=200.0;
    CGSize maxSize = CGSizeMake(screenWidth-layoutWidth(80), CGFLOAT_MAX);
    CGRect labelRect = [[NSString stringWithFormat:@"%@",str] boundingRectWithSize:maxSize
                                                 options:NSStringDrawingUsesLineFragmentOrigin
                                              attributes:@{NSFontAttributeName: [UIFont systemFontOfSize:layoutFont(24)]}
                                                 context:nil];
    h=labelRect.size.height+layoutWidth(120);
    return h;
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
