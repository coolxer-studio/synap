//
//  SwiftCrash.swift
//  collectorlog-demo
//
//  Created by yaoqi.li on 2022/12/25.
//

import Foundation

@objc class CrashSwift : NSObject{
    @objc func crash(){
        var text:String!
        print(text!)
        print("test crash");
    }
}
