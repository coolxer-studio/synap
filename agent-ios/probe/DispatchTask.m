//
//  DispatchTask.m
//  probe
//
//  Created by yaoqi.li on 2023/5/24.
//

#import "DispatchTask.h"

@interface DispatchTask ()

@property (nonatomic, copy) NSString *queueName;
@property (nonatomic, assign) NSTimeInterval interval;
@property (nonatomic, assign) NSTimeInterval cancelTime;
@property (nonatomic, copy) PeriodicTaskBlock taskBlock;
@property (nonatomic, strong) dispatch_source_t timer;

@end

@implementation DispatchTask

- (instancetype)initWithQueueName:(NSString *)queueName
                         interval:(NSTimeInterval)interval
                      cancelTime:(NSTimeInterval)cancelTime
                      taskBlock:(PeriodicTaskBlock)taskBlock {
    self = [super init];
    if (self) {
        _queueName = [queueName copy];
        _interval = interval;
        _cancelTime = cancelTime;
        _taskBlock = [taskBlock copy];
    }
    return self;
}

- (instancetype)initWithQueueName:(NSString *)queueName
                      taskBlock:(PeriodicTaskBlock)taskBlock {
    self = [super init];
    if (self) {
        _queueName = [queueName copy];
        _taskBlock = [taskBlock copy];
    }
    return self;
}

- (void)start {
    // 创建一个串行队列
    dispatch_queue_t serialQueue = dispatch_queue_create([self.queueName UTF8String], DISPATCH_QUEUE_SERIAL);
    if(_interval == 0 && _cancelTime == 0){
        // 异步单次执行
        dispatch_async(serialQueue,_taskBlock);
    }else{
        // 异步定时任务
        // 创建一个定时器
        self.timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, serialQueue);
        
        // 设置定时器的触发时间、间隔和精度
        dispatch_time_t start = dispatch_time(DISPATCH_TIME_NOW, 0);
        uint64_t intervalInNanoseconds = (uint64_t)(self.interval * NSEC_PER_SEC);
        uint64_t leewayInNanoseconds = (uint64_t)(0.1 * NSEC_PER_SEC);
        dispatch_source_set_timer(self.timer, start, intervalInNanoseconds, leewayInNanoseconds);
        
        // 设置定时器的处理回调
        dispatch_source_set_event_handler(self.timer, ^{
            // 在这里执行周期任务的代码块
            if (self.taskBlock) {
                self.taskBlock();
            }
        });
        
        // 启动定时器
        dispatch_resume(self.timer);
        
        // 一段时间后，取消定时器
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(self.cancelTime * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            dispatch_source_cancel(self.timer);
        });
    }
}

@end
