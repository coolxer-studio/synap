//
//  ViewController.m
//  illegal-state-ios
//
//  Created by yaoqi.li on 2023/3/2.
//

#import "CrashViewController.h"
#import <Foundation/Foundation.h>
//target名称包含的"-"需要改成"_"，如果还找不到可以直接搜索：cd ~/Library/Developer/Xcode/DerivedData find * -iname '*Swift.h'
#import "agent_ios-Swift.h"
#import "CCrash.h"

@interface CrashViewController ()

@end

@implementation CrashViewController
- (IBAction)buttonObjcCrash:(id)sender {
    NSString *test;
    NSMutableDictionary *dic = [NSMutableDictionary new];
    [dic setObject:test forKey:@"value-for-nil"];
}
- (IBAction)buttonSwiftCrash:(id)sender {
    CrashSwift *cs = [CrashSwift new];
    [cs crash];
}
- (IBAction)buttonCCrash:(id)sender {
    crash();
}

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
}

@end
