//
//  CTToolTableViewCell.m
//  probe-ios
//
//  Created by Young on 2024/2/28.
//

#import "CTToolTableViewCell.h"
@interface CTToolTableViewCell()
/// title内容
@property (nonatomic, strong) UILabel *titleLabel;
/// btn
@property (nonatomic, strong) UIButton *helpBtn;
@end
@implementation CTToolTableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView
{
    // 定义唯一标识
    static NSString *ID = @"toolCell";
    CTToolTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:ID];
    if (!cell) {
        cell = [[CTToolTableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:ID];
        cell.userInteractionEnabled=YES;
        
    }
    return cell;
}
-(instancetype)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier
{
    self=[super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        self.backgroundColor=[UIColor whiteColor];
        //添加cell的子控件
        [self setChildView];
    }
    return self;
}
-(void)setChildView
{
    //设置圆角
    self.layer.cornerRadius=layoutWidth(10);
    self.layer.masksToBounds=YES;
    //添加子控件
    [self.contentView addSubview:self.titleLabel];
    [self.contentView addSubview:self.helpBtn];
    //设置frame
    self.titleLabel.frame=self.contentView.bounds;
    self.helpBtn.frame=CGRectMake(self.contentView.bounds.size.width-layoutWidth(80), layoutWidth(10), layoutWidth(60), layoutWidth(60));
}
//加载数据
-(void)setSourceDic:(NSDictionary *)sourceDic
{
    _sourceDic=sourceDic;
    self.titleLabel.text=sourceDic[@"name"];
}
-(void)helpBtnClicked:(UIButton*)btn
{
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"说明" message:_sourceDic[@"explain"] preferredStyle:UIAlertControllerStyleAlert];
    // 添加按钮，可以根据需求添加多个按钮
    UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"确定"
                                                       style:UIAlertActionStyleDefault
                                                     handler:^(UIAlertAction *action) {
                                                         // 处理确定按钮点击后的操作
                                                     }];
    [alertController addAction:okAction];
    // 获取当前显示的控制器并在其上显示提示框
    UIViewController *topController = [UIApplication sharedApplication].keyWindow.rootViewController;
    while (topController.presentedViewController) {
        topController = topController.presentedViewController;
    }
    [topController presentViewController:alertController animated:YES completion:nil];
}
#pragma mark - lazy懒加载
- (UILabel *)titleLabel {
    if (!_titleLabel) {
        _titleLabel = [[UILabel alloc] init];
        _titleLabel.font = [UIFont boldSystemFontOfSize:layoutFont(24)];
        _titleLabel.textAlignment=NSTextAlignmentCenter;
        _titleLabel.text = @"模拟登录";
        _titleLabel.textColor =RGB(50, 50, 50);
    }
    return _titleLabel;
}
- (UIButton *)helpBtn {
    if (!_helpBtn) {
        _helpBtn = [[UIButton alloc] init];
        _helpBtn.adjustsImageWhenHighlighted = NO;
        [_helpBtn setImage:[UIImage imageNamed:@"ic_tool_help"] forState:UIControlStateNormal];
        [_helpBtn addTarget:self action:@selector(helpBtnClicked:) forControlEvents:UIControlEventTouchUpInside];
    }
    return _helpBtn;
}
-(void)setFrame:(CGRect)frame
{
    frame.origin.x=layoutWidth(50);
    frame.size.height=layoutWidth(80);
    frame.size.width=screenWidth-layoutWidth(100);
    [super setFrame:frame];
}
- (void)awakeFromNib {
    [super awakeFromNib];
    // Initialization code
}

- (void)setSelected:(BOOL)selected animated:(BOOL)animated {
    [super setSelected:selected animated:animated];

    // Configure the view for the selected state
}

@end
