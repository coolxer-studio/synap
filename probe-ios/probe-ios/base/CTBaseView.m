//
//  CTBaseView.m
//  probe-ios
//
//  Created by Young on 2023/12/22.
//

#import "CTBaseView.h"

@implementation CTBaseView
- (instancetype)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (self) {
        [self setUpSubView];
    }
    return self;
}

#pragma mark - UI
- (void)setUpSubView {
    
}
/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

@end
