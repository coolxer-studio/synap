//
//  CTAnalyzeGifView.m
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import "CTAnalyzeGifView.h"
@interface CTAnalyzeGifView ()
/// 设置gif图imageView
@property (nonatomic, strong) UIImageView *gifImgView;
/// 设置内容label
@property (nonatomic, strong) UILabel *contentLab;
@end
@implementation CTAnalyzeGifView


#pragma mark - 设置数据
-(void)setAnalyzeModel:(CTAnalyzeModel *)analyzeModel
{
    self.contentLab.text=[NSString stringWithFormat:@"%@(%@)",analyzeModel.grade,analyzeModel.score];
}
#pragma mark - 设置view和UI
-(void)setUpSubView
{
    [self addSubview:self.gifImgView];
    [self addSubview:self.contentLab];
    
    self.gifImgView.frame=self.bounds;
    //因为没有UI，先设置大点，而且居中的话视觉不受影响
    self.contentLab.frame=self.bounds;

}
//将gif图每一帧和时长组合成字典。适配IOS13以下。
-(NSMutableDictionary*)getGifImges:(NSString*)gifImgName
{
    if ([gifImgName isEqual:@""]) {
        return nil;
    }
    NSDictionary *gifLoopCount = [NSDictionary dictionaryWithObject:[NSNumber numberWithInt:0] forKey:(NSString *)kCGImagePropertyGIFLoopCount];
     NSDictionary *gifCountDic = [NSDictionary dictionaryWithObject:gifLoopCount forKey:(NSString *)kCGImagePropertyGIFDictionary];
    NSData *gifData = [NSData dataWithContentsOfFile: [[NSBundle mainBundle] pathForResource:gifImgName ofType:@"gif"]];
    CGImageSourceRef imageSource = CGImageSourceCreateWithData((CFDataRef)gifData, (CFDictionaryRef)gifCountDic);
    size_t count = CGImageSourceGetCount(imageSource);
   NSTimeInterval duration = 0;
   NSMutableArray *imageArr = [NSMutableArray arrayWithCapacity:0];
   for (int i = 0; i<count; i++) {
       CGImageRef cgImage = CGImageSourceCreateImageAtIndex(imageSource, i, (CFDictionaryRef)gifCountDic);
       NSTimeInterval frameDuration = [self gifImageDeleyTime:imageSource index:i];
       duration += frameDuration;
       UIImage *imageName =  [UIImage imageWithCGImage:cgImage];
       [imageArr addObject:imageName];
       CGImageRelease(cgImage);
   }
    NSMutableDictionary*dic=[NSMutableDictionary dictionary];
    dic[@"imgArray"]=imageArr;
    dic[@"duration"]=[NSNumber numberWithDouble:duration];
    return dic;
}

    //获取GIF图片每帧的时长
- (NSTimeInterval)gifImageDeleyTime:(CGImageSourceRef)imageSource index:(NSInteger)index {
    NSTimeInterval duration = 0;
    CFDictionaryRef imageProperties = CGImageSourceCopyPropertiesAtIndex(imageSource, index, NULL);
    if (imageProperties) {
        CFDictionaryRef gifProperties;
        BOOL result = CFDictionaryGetValueIfPresent(imageProperties, kCGImagePropertyGIFDictionary, (const void **)&gifProperties);
        if (result) {
            const void *durationValue;
            if (CFDictionaryGetValueIfPresent(gifProperties, kCGImagePropertyGIFUnclampedDelayTime, &durationValue)) {
                duration = [(__bridge NSNumber *)durationValue doubleValue];
                if (duration < 0) {
                    if (CFDictionaryGetValueIfPresent(gifProperties, kCGImagePropertyGIFDelayTime, &durationValue)) {
                        duration = [(__bridge NSNumber *)durationValue doubleValue];
                    }
                }
            }
        }
    }
    
    return duration;
}

#pragma mark - lazy
- (UIImageView *)gifImgView {
    if (!_gifImgView) {
        _gifImgView = [[UIImageView alloc] init];
        // 加载 GIF 图片
        NSMutableDictionary*animationImgsDic=[self getGifImges:@"ic_analyze_background"];
        _gifImgView.animationImages = animationImgsDic[@"imgArray"];
        _gifImgView.animationDuration = [animationImgsDic[@"duration"] doubleValue];
        _gifImgView.animationRepeatCount = 0;
        [_gifImgView startAnimating];
    }
    return _gifImgView;
}
- (UILabel *)contentLab {
    if (!_contentLab) {
        _contentLab = [[UILabel alloc] init];
        _contentLab.font =[UIFont systemFontOfSize:18];
        _contentLab.text = @"111中风险\n（61）";
        _contentLab.numberOfLines=0;
        _contentLab.textColor = RGB(235, 188, 128);
        _contentLab.textAlignment=NSTextAlignmentCenter;
    }
    return _contentLab;
}
/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

@end
