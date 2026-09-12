//
//  CTLoginViewController.m
//  agent-ios
//
//  Created by yaoqi.li on 2023/10/13.
//

#import "CTLoginViewController.h"
#import "CTProbe.h"

@interface CTLoginViewController () <UITextFieldDelegate>

@property (nonatomic, strong) UITextField *usernameField;
@property (nonatomic, strong) UITextField *passwordField;
@property (nonatomic, strong) UIButton *loginButton;

@end

@implementation CTLoginViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.navigationItem.title=@"模拟登录";
    // 创建用户名输入框
    self.usernameField = [[UITextField alloc] initWithFrame:CGRectMake(screenWidth/2-140, 100, 280, 30)];
    self.usernameField.placeholder = @"用户名";
    self.usernameField.borderStyle = UITextBorderStyleRoundedRect;
    self.usernameField.delegate = self;
    [self.view addSubview:self.usernameField];
    
    // 创建密码输入框
    self.passwordField = [[UITextField alloc] initWithFrame:CGRectMake(screenWidth/2-140, 150, 280, 30)];
    self.passwordField.placeholder = @"密码";
    self.passwordField.secureTextEntry = YES;
    self.passwordField.borderStyle = UITextBorderStyleRoundedRect;
    self.passwordField.delegate = self;
    [self.view addSubview:self.passwordField];
    
    // 创建登录按钮
    self.loginButton = [UIButton buttonWithType:UIButtonTypeRoundedRect];
    self.loginButton.frame = CGRectMake(screenWidth/2-140, 200, 280, 40);
    [self.loginButton setTitle:@"登录" forState:UIControlStateNormal];
    [self.loginButton addTarget:self action:@selector(loginButtonTapped) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.loginButton];
}

- (void)loginButtonTapped {
    NSString *username = self.usernameField.text;
    NSString *password = self.passwordField.text;
    
    // 例如，简单的本地验证示例
    if (username.length == 0 || password.length == 0) {
        // 创建 UIAlertController
        UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"警告" message:@"请填写用户名和密码" preferredStyle:UIAlertControllerStyleAlert];
        // 添加按钮
        UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
            // 当用户点击 "确定" 按钮时执行的操作
            NSLog(@"用户确定");
        }];
        [alertController addAction:okAction];
        // 显示警报框
        [self presentViewController:alertController animated:YES completion:nil];
    } else {
        NSMutableDictionary *data = [NSMutableDictionary new];
        [data setValue:username forKey:@"username"];
        [data setValue:password forKey:@"password"];
        [[CTProbe sharedInstance] userData:username withUserData:data];
        NSLog(@"发送用户信息111");
    }
}

#pragma mark - UITextFieldDelegate

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    if (textField == self.usernameField) {
        [self.passwordField becomeFirstResponder];
    } else if (textField == self.passwordField) {
        [textField resignFirstResponder];
    }
    return YES;
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
