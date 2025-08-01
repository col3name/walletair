//
//  WNavigationController.swift
//  UIComponents
//
//  Created by Sina on 6/29/24.
//

import UIKit
import WalletContext

open class WNavigationController: UINavigationController {
    
    private let log = Log("WNavigationController")
    
    open override func viewDidLoad() {
        super.viewDidLoad()
        delegate = self
        setupFullWidthBackGesture()
    }

    private lazy var fullWidthBackGestureRecognizer = UIPanGestureRecognizer()
    
    private func setupFullWidthBackGesture() {
        // The trick here is to wire up our full-width `fullWidthBackGestureRecognizer` to execute the same handler as the system `interactivePopGestureRecognizer`.
        guard let interactivePopGestureRecognizer = interactivePopGestureRecognizer,
              let targets = interactivePopGestureRecognizer.value(forKey: "targets") else {
            return
        }
        fullWidthBackGestureRecognizer.setValue(targets, forKey: "targets")
        fullWidthBackGestureRecognizer.delegate = self
        view.addGestureRecognizer(fullWidthBackGestureRecognizer)
    }
    
    public override func pushViewController(_ viewController: UIViewController, animated: Bool) {
        if viewControllers.count > 0, (viewController as? WViewController)?.hideBottomBar != false {
            viewController.hidesBottomBarWhenPushed = true
        }
        super.pushViewController(viewController, animated: animated)
    }
    
    open override func popViewController(animated: Bool) -> UIViewController? {
        if presentedViewController != nil, !(presentedViewController?.description.contains("MinimizableSheet") == true) {
            log.error("Presenting a modal view controller. Will not pop to prevent freeze")
            return nil
        }
        return super.popViewController(animated: animated)
    }
}

extension WNavigationController: UINavigationControllerDelegate {
    public func navigationController(_ navigationController: UINavigationController,
                                     willShow viewController: UIViewController, animated: Bool) {
        guard let vc = viewController as? WViewController else {return}
        setNavigationBarHidden(vc.hideNavigationBar,
                               animated: animated)
    }
}

extension WNavigationController: UIGestureRecognizerDelegate {
    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        let isSystemSwipeToBackEnabled = interactivePopGestureRecognizer?.isEnabled == true
        let isThereStackedViewControllers = viewControllers.count > 1
        return isSystemSwipeToBackEnabled && isThereStackedViewControllers && presentedViewController == nil
    }
    
    public func fullWidthBackGestureRecognizerRequireToFail(_ otherGestureRecognizer: UIGestureRecognizer) {
        fullWidthBackGestureRecognizer.require(toFail: otherGestureRecognizer)
    }
}
