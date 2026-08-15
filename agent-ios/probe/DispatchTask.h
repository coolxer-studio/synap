//
//  DispatchTask.h
//  probe
//
//  Created by yaoqi.li on 2023/5/24.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^PeriodicTaskBlock)(void);

@interface DispatchTask : NSObject

- (instancetype)initWithQueueName:(NSString *)queueName
                         interval:(NSTimeInterval)interval
                      cancelTime:(NSTimeInterval)cancelTime
                      taskBlock:(PeriodicTaskBlock)taskBlock;

- (instancetype)initWithQueueName:(NSString *)queueName
                      taskBlock:(PeriodicTaskBlock)taskBlock;


- (void)start;

@end

NS_ASSUME_NONNULL_END
