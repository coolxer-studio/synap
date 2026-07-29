//
//  CTExtension.m
//  probe-ios
//
//  Created by Young on 2023/10/17.
//

#import "CTExtension.h"
#import <objc/runtime.h>
@implementation CTExtension
+(CTExtension*)shareExtension
{
    static CTExtension* shareExtension;
    if (!shareExtension) {
        shareExtension=[[CTExtension alloc] init];
    }
    return shareExtension;

}
// TODO: 模型转化方法仍需优化，后续需要支持多层自动转化
- (id)ctObjectWithKeyValues:(NSDictionary *)dataDictionary toModel:(id)modelClass  {
    if (dataDictionary&&![dataDictionary isKindOfClass:[NSNull class]]) {
        unsigned int propertyCount;
        id model = [[modelClass alloc] init];
        objc_property_t *properties = class_copyPropertyList([model class], &propertyCount);
        
        for (unsigned int i = 0; i < propertyCount; i++) {
            objc_property_t property = properties[i];
            const char *propertyName = property_getName(property);
            NSString *key = [NSString stringWithUTF8String:propertyName];
            
            id value = dataDictionary[key];
            
            if (value) {
                [model setValue:value forKey:key];
            }
        }
        
        free(properties);
        return model;
    }
    return nil;
}
- (NSArray *)convertDictionaryArray:(NSArray<NSDictionary *> *)dictionaryArray toModel:(Class)modelClass {
    NSMutableArray *modelArray = [NSMutableArray array];
    
    for (NSDictionary *dictionary in dictionaryArray) {
        id model = [self convertDictionary:dictionary toModel:modelClass];
        [modelArray addObject:model];
    }
    
    return [modelArray copy];
}

- (id)convertDictionary:(NSDictionary *)dictionary toModel:(Class)modelClass {
    id model = [[modelClass alloc] init];
    
    unsigned int propertyCount;
    objc_property_t *properties = class_copyPropertyList(modelClass, &propertyCount);
    
    for (unsigned int i = 0; i < propertyCount; i++) {
        objc_property_t property = properties[i];
        const char *propertyName = property_getName(property);
        NSString *key = [NSString stringWithUTF8String:propertyName];
        
        id value = dictionary[key];
        
        if (value) {
            if ([value isKindOfClass:[NSDictionary class]]) {
                // 如果值是字典，递归调用 convertDictionary 方法
                Class propertyClass = [self classForPropertyName:key inModel:modelClass];
                value = [self convertDictionary:value toModel:propertyClass];
            }
            [model setValue:value forKey:key];
        }
    }
    
    free(properties);
    
    return model;
}

- (Class)classForPropertyName:(NSString *)propertyName inModel:(Class)modelClass {
    NSString *propertyAttributes = [NSString stringWithCString:property_getAttributes(class_getProperty(modelClass, [propertyName UTF8String])) encoding:NSUTF8StringEncoding];
    NSArray *attributes = [propertyAttributes componentsSeparatedByString:@","];
    
    for (NSString *attribute in attributes) {
        if ([attribute hasPrefix:@"T@"]) {
            NSString *className = [attribute substringWithRange:NSMakeRange(3, [attribute length] - 4)];
            return NSClassFromString(className);
        }
    }
    
    return nil;
}

@end
