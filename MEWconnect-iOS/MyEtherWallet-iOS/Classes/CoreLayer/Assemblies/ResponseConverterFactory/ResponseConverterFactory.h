//
//  ResponseConverterFactory.h
//  MyEtherWallet-iOS
//
//  Created by Mikhail Nikanorov on 21/05/2018.
//  Copyright © 2018 MyEtherWallet, Inc. All rights reserved.
//

@import Foundation;

@protocol ResponseConverter;

@protocol ResponseConverterFactory <NSObject>
- (id<ResponseConverter>)converterWithType:(NSNumber *)type;
@end

