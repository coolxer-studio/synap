//
//  CTDeviceTableViewCell.m
//  probe-ios
//
//  Created by Young on 2023/10/18.
//

#import "CTDeviceTableViewCell.h"
@interface CTDeviceTableViewCell()
/// 底层view
@property (nonatomic, strong) UIView *bgView;
/// 标题图标按钮（如果不用点击直接用imageView）
@property (nonatomic, strong) UIButton *titleImgBtn;
/// 标题Label
@property (nonatomic, strong) UILabel *titleLabel;
/// 线段view
@property (nonatomic, strong) UIView *lineView;
/// 承载内容的Label
@property (nonatomic, strong) UILabel *contentLabel;
@end
@implementation CTDeviceTableViewCell
+(instancetype)cellWithTableView:(UITableView *)tableView
{
    // 定义唯一标识
    static NSString *ID = @"deviceCell";
    CTDeviceTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:ID];
    if (!cell) {
        cell = [[CTDeviceTableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:ID];
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
    [self.bgView addSubview:self.contentLabel];
    
    
    self.bgView.frame=CGRectMake(layoutWidth(20), layoutWidth(20), screenWidth-layoutWidth(40), layoutWidth(200));
    self.titleImgBtn.frame=CGRectMake(layoutWidth(20), layoutWidth(10), layoutWidth(60), layoutWidth(60));
    self.titleLabel.frame=CGRectMake(0, 0, self.bgView.frame.size.width, layoutWidth(80));
    self.lineView.frame=CGRectMake(layoutWidth(20), layoutWidth(80), self.bgView.frame.size.width-layoutWidth(40), layoutWidth(2));
    self.contentLabel.frame=CGRectMake(layoutWidth(20), layoutWidth(82), self.bgView.frame.size.width-layoutWidth(40), layoutWidth(20));

}
//后续使用
-(void)setContentModel:(CTDeviceContentModel *)contentModel
{
    [self.titleImgBtn setImage:[UIImage imageNamed:contentModel.sectionIcon] forState:UIControlStateNormal];
    self.titleLabel.text=contentModel.sectionName;
    self.contentLabel.text=contentModel.sectionContent;
}
//当前根据数据结构临时使用
-(void)setContentString:(NSString*)contentStr sectionType:(NSString*)sectionType sectionType:(NSString*)sectionIcon
{
    [self.titleImgBtn setImage:[UIImage imageNamed:sectionIcon] forState:UIControlStateNormal];
    self.titleLabel.text=sectionType;
    
    NSDictionary*dic=(NSDictionary*)contentStr;
    NSMutableString *resultString = [NSMutableString string];

    [dic enumerateKeysAndObjectsUsingBlock:^(id key, id value, BOOL *stop) {
        NSString *keyValueString = [NSString stringWithFormat:@"%@: %@", key, value];
        [resultString appendString:keyValueString];

        // 添加换行符
        [resultString appendString:@"\n"];
    }];
    
    
    self.contentLabel.text=[NSString stringWithFormat:@"%@",resultString];
    [self.contentLabel sizeToFit];
//    // 计算UILabel的文本高度
//    CGSize maxSize = CGSizeMake(self.contentLabel.frame.size.width, CGFLOAT_MAX);
//    CGRect labelRect = [self.contentLabel.text boundingRectWithSize:maxSize
//                                                 options:NSStringDrawingUsesLineFragmentOrigin
//                                              attributes:@{NSFontAttributeName: self.contentLabel.font}
//                                                 context:nil];
    self.contentLabel.frame=CGRectMake(layoutWidth(20), layoutWidth(82), self.bgView.frame.size.width-layoutWidth(40),self.contentLabel.frame.size.height);
    
    self.bgView.frame=CGRectMake(layoutWidth(20), layoutWidth(20), screenWidth-layoutWidth(40), CGRectGetMaxY(self.contentLabel.frame));
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
- (UILabel *)contentLabel {
    if (!_contentLabel) {
        _contentLabel = [[UILabel alloc] init];
        _contentLabel.font = [UIFont boldSystemFontOfSize:layoutFont(24)];
        _contentLabel.text = @"测试数据";
        _contentLabel.textColor = RGB(50, 50, 50);
        _contentLabel.numberOfLines=0;
    }
    return _contentLabel;
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
