//
//  CTIdentifyTableViewCell.m
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import "CTIdentifyTableViewCell.h"
@interface CTIdentifyTableViewCell()

/// 底层view
@property (nonatomic, strong) UIView *bgView;
/// 标题图标按钮（如果不用点击直接用imageView）
@property (nonatomic, strong) UIButton *titleImgBtn;
/// 标题Label
@property (nonatomic, strong) UILabel *titleLabel;
/// 线段view
@property (nonatomic, strong) UIView *lineView;
/// 承载风险标记内容的数组(里面最好放btn，为未来点击做伏笔)
@property (nonatomic, strong) NSMutableArray *tagBtnsArray;
@end
@implementation CTIdentifyTableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView
{
    // 定义唯一标识
    static NSString *ID = @"identifyCell";
    CTIdentifyTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:ID];
    if (!cell) {
        cell = [[CTIdentifyTableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:ID];
        cell.userInteractionEnabled=YES;
        
    }
    return cell;
}
-(instancetype)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier
{
    self=[super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        self.backgroundColor=gray_200;
        
        //添加cell的子控件
        [self setChildView];
    }
    return self;
}
-(void)setChildView
{
    [self.contentView addSubview:self.bgView];
    [self.bgView addSubview:self.titleImgBtn];
    [self.bgView addSubview:self.titleLabel];
    [self.bgView addSubview:self.lineView];
    
    self.bgView.frame=CGRectMake(layoutWidth(20), layoutWidth(20), screenWidth-layoutWidth(40), layoutWidth(200));
    self.titleImgBtn.frame=CGRectMake(layoutWidth(20), layoutWidth(10), layoutWidth(60), layoutWidth(60));
    self.titleLabel.frame=CGRectMake(0, 0, self.bgView.frame.size.width, layoutWidth(80));
    self.lineView.frame=CGRectMake(layoutWidth(20), layoutWidth(80), self.bgView.frame.size.width-layoutWidth(40), layoutWidth(2));
}
#pragma mark - 设置模型数据
-(void)setIdentifyDataModel:(CTIdentifyDataModel *)identifyDataModel
{
    _identifyDataModel=identifyDataModel;
    if ([identifyDataModel.type isEqual:@"high"]) {
        [self.titleImgBtn setImage:[UIImage imageNamed:@"ic_identify_high"] forState:UIControlStateNormal];
        self.titleLabel.text=@"高风险标记";
        self.titleLabel.textColor=RGB(255, 72, 77);
    }
    else if ([identifyDataModel.type isEqual:@"medium"])
    {
        [self.titleImgBtn setImage:[UIImage imageNamed:@"ic_identify_middle"] forState:UIControlStateNormal];
        self.titleLabel.text=@"中风险标记";
        self.titleLabel.textColor=RGB(255, 189, 51);
    }
    else if ([identifyDataModel.type isEqual:@"low"])
    {
        [self.titleImgBtn setImage:[UIImage imageNamed:@"ic_identify_low"] forState:UIControlStateNormal];
        self.titleLabel.text=@"低风险标记";
        self.titleLabel.textColor=RGB(4, 183, 135);
    }
    else if ([identifyDataModel.type isEqual:@"other"])
    {
        [self.titleImgBtn setImage:[UIImage imageNamed:@"ic_identify_low"] forState:UIControlStateNormal];
        self.titleLabel.text=@"其他";
        self.titleLabel.textColor=RGB(4, 183, 135);
    }
    else
    {
        [self.titleImgBtn setImage:[UIImage imageNamed:@"ic_identify_low"] forState:UIControlStateNormal];
        self.titleLabel.text=@"一般标记";
        self.titleLabel.textColor=RGB(4, 183, 135);
    }
    for (int i=0; i<self.tagBtnsArray.count; i++) {
        UIButton*btn=self.tagBtnsArray[i];
        [btn removeFromSuperview];
    }
    [self.tagBtnsArray removeAllObjects];
    NSArray*flagArray=identifyDataModel.value_list;
    // 定义按钮的初始坐标和间距
    CGFloat xPosition = layoutWidth(20);
    CGFloat yPosition = layoutWidth(100);
    CGFloat buttonSpacing = layoutWidth(10);
    for (int i=0; i<flagArray.count; i++) {
        NSString*contentStr=identifyDataModel.value_list[i];
        CGSize textSize = [contentStr sizeWithAttributes:@{NSFontAttributeName: [UIFont boldSystemFontOfSize:layoutFont(24)]}];
        UIButton*tagBtn=[[UIButton alloc] init];
        [tagBtn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
        tagBtn.tag=i;
        [tagBtn setTitle:contentStr forState:UIControlStateNormal];
        tagBtn.titleLabel.font=[UIFont boldSystemFontOfSize:layoutFont(24)];
        [tagBtn sizeToFit];
        tagBtn.layer.cornerRadius=layoutWidth(3);
        tagBtn.layer.masksToBounds=YES;
        if ([identifyDataModel.type isEqual:@"high"]) {
            tagBtn.backgroundColor=RGB(255, 72, 77);
        }
        else if ([identifyDataModel.type isEqual:@"medium"])
        {
            tagBtn.backgroundColor=RGB(255, 189, 51);
        }
        else if ([identifyDataModel.type isEqual:@"other"])
        {
            tagBtn.backgroundColor=RGB(4, 183, 135);
        }
        else
        {
            tagBtn.backgroundColor=RGB(4, 183, 135);
        }
        [self.bgView addSubview:tagBtn];
        tagBtn.frame=CGRectMake(xPosition, yPosition, textSize.width + layoutWidth(20), layoutWidth(40));
        // 更新 xPosition
        xPosition += textSize.width + layoutWidth(20) + buttonSpacing;
        // 如果按钮的右侧超出屏幕，换行
        if (xPosition + textSize.width + buttonSpacing > CGRectGetWidth(self.bgView.bounds)) {
            xPosition = layoutWidth(20);
            yPosition += layoutWidth(60); // 假设按钮高度为 30，添加一些额外的间距
        }
        
        [self.tagBtnsArray addObject:tagBtn];
    }
    self.bgView.frame=CGRectMake(layoutWidth(20), layoutWidth(20), screenWidth-layoutWidth(40), yPosition+layoutWidth(60));
}
#pragma mark - 点击事件Action
//点击事件-解释说明风险内容
-(void)helpBtnClicked:(UIButton*)btn
{
    NSString*contentStr=@"";
    if ([_identifyDataModel.type isEqual:@"high"]) {
        contentStr=@"高风险标记需要及时处理";
    }
    else if ([_identifyDataModel.type isEqual:@"medium"])
    {
        contentStr=@"中风险标记需要重点关注";
    }
    else if ([_identifyDataModel.type isEqual:@"low"])
    {
        contentStr=@"低风险标记";
    }
    else if ([_identifyDataModel.type isEqual:@"other"])
    {
        contentStr=@"未分类的标记";
    }
    else
    {
        contentStr=@"常规标记无风险";
    }
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"说明" message:contentStr preferredStyle:UIAlertControllerStyleAlert];
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
#pragma mark - 懒加载
- (UIView *)bgView {
    if (!_bgView) {
        _bgView = [[UIView alloc] init];
        _bgView.layer.cornerRadius=layoutWidth(10);
        _bgView.layer.masksToBounds=YES;
        _bgView.backgroundColor=[UIColor whiteColor];
    }
    return _bgView;
}
- (UIButton *)titleImgBtn {
    if (!_titleImgBtn) {
        _titleImgBtn = [[UIButton alloc] init];
        [_titleImgBtn addTarget:self action:@selector(helpBtnClicked:) forControlEvents:UIControlEventTouchUpInside];
    }
    return _titleImgBtn;
}
- (UILabel *)titleLabel {
    if (!_titleLabel) {
        _titleLabel = [[UILabel alloc] init];
        _titleLabel.font = [UIFont boldSystemFontOfSize:layoutFont(24)];
        _titleLabel.text = @"CPU";
        _titleLabel.textColor = blue_700;
        _titleLabel.textAlignment=NSTextAlignmentCenter;
    }
    return _titleLabel;
}
- (UIView *)lineView {
    if (!_lineView) {
        _lineView = [[UIView alloc] init];
        _lineView.backgroundColor=blue_700;
        _lineView.layer.cornerRadius=layoutWidth(1);
    }
    return _lineView;
}
-(NSMutableArray *)tagBtnsArray
{
    if (!_tagBtnsArray) {
        _tagBtnsArray=[[NSMutableArray alloc] init];
    }
    return _tagBtnsArray;
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
