//
//  ThreadSafeQueue.h
//  probe
//
//  Created by yaoqi.li on 2023/5/24.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface ThreadSafeQueue<ObjectType> : NSObject

- (void)enqueue:(ObjectType)object;
- (ObjectType)dequeue;
- (NSUInteger)count;
- (void)clear;
- (NSArray<ObjectType> *)dequeueAll;

@end

NS_ASSUME_NONNULL_END
