//
//  SettingsViewControllerTests.m
//  MyEtherWallet-iOS
//
//  Created by Mikhail Nikanorov on 28/04/2018.
//  Copyright © 2018 MyEtherWallet, Inc. All rights reserved.
//

@import XCTest;
@import OCMock;

#import "SettingsViewController.h"

#import "SettingsViewOutput.h"

@interface SettingsViewControllerTests : XCTestCase

@property (nonatomic, strong) SettingsViewController *controller;

@property (nonatomic, strong) id mockOutput;

@end

@implementation SettingsViewControllerTests

#pragma mark - Config the environment

- (void)setUp {
    [super setUp];

    self.controller = [[SettingsViewController alloc] init];

    self.mockOutput = OCMProtocolMock(@protocol(SettingsViewOutput));

    self.controller.output = self.mockOutput;
}

- (void)tearDown {
    self.controller = nil;

    self.mockOutput = nil;

    [super tearDown];
}

#pragma mark - Lifecycle tests

- (void)testThatControllerNotifiesPresenterOnDidLoad {
	// given

	// when
	[self.controller viewDidLoad];

	// then
	OCMVerify([self.mockOutput didTriggerViewReadyEvent]);
}

#pragma mark - UI tests

#pragma mark - SettingsViewInput tests

@end
