//
//  CTExtension.h
//  probe-ios
//
//  Created by Young on 2023/10/17.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface CTExtension : NSObject
+(CTExtension*)shareExtension;
- (id)ctObjectWithKeyValues:(NSDictionary *)dataDictionary toModel:(id)modelClass;
- (id)convertDictionary:(NSDictionary *)dictionary toModel:(Class)modelClass;
- (NSArray *)convertDictionaryArray:(NSArray<NSDictionary *> *)dictionaryArray toModel:(Class)modelClass;
@end

NS_ASSUME_NONNULL_END
