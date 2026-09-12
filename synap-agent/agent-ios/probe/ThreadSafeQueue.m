//
//  ThreadSafeQueue.m
//  probe
//
//  Created by yaoqi.li on 2023/5/24.
//

#import "ThreadSafeQueue.h"

@implementation ThreadSafeQueue {
    NSMutableArray *_queue;
    dispatch_queue_t _queueAccessQueue;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _queue = [[NSMutableArray alloc] init];
        _queueAccessQueue = dispatch_queue_create("com.coolxer.queueAccess", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

- (void)enqueue:(id)object {
    dispatch_sync(_queueAccessQueue, ^{
        [_queue addObject:object];
    });
}

- (id)dequeue {
    __block id object;
    dispatch_sync(_queueAccessQueue, ^{
        if (_queue.count > 0) {
            object = [_queue firstObject];
            [_queue removeObjectAtIndex:0];
        }
    });
    return object;
}

- (NSUInteger)count {
    __block NSUInteger count;
    dispatch_sync(_queueAccessQueue, ^{
        count = _queue.count;
    });
    return count;
}

- (void)clear {
    dispatch_sync(_queueAccessQueue, ^{
        [_queue removeAllObjects];
    });
}

- (NSArray *)dequeueAll {
    __block NSArray *allObjects;
    dispatch_sync(_queueAccessQueue, ^{
        allObjects = [_queue copy];
        [_queue removeAllObjects];
    });
    return allObjects;
}

@end
