//
//  CTNormalTools.m
//  probe-ios
//
//  Created by Young on 2023/10/17.
//

#import "CTNormalTools.h"

@implementation CTNormalTools
+(id)jsonStringToDic:(NSString *)jsonString
{
    NSError *error=nil;
    if (jsonString) {
        id result=[NSJSONSerialization JSONObjectWithData:[jsonString dataUsingEncoding:NSUTF8StringEncoding] options:NSJSONReadingMutableLeaves error:&error];
        if (error==nil) {
            return result;
        }
        else{
            NSLog(@"json错误===%@",error);
            return nil;
        }
    }
    return nil;
}

@end
