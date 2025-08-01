//
//  RangeChartView.swift
//  GraphTest
//
//  Created by Andrei Salavei on 3/11/19.
//  Copyright © 2019 Andrei Salavei. All rights reserved.
//
import UIKit
import WalletContext


private enum Constants {
    static let cropIndicatorLineWidth: CGFloat = 1
    static let markerSelectionRange: CGFloat = 50
    static let defaultMinimumRangeDistance: CGFloat = 0.05
    static let titntAreaWidth: CGFloat = 10
    static let horizontalContentMargin: CGFloat = 16
    static let cornerRadius: CGFloat = 5
}

public class RangeChartView: UIControl, WThemedView, UIGestureRecognizerDelegate {
    private enum Marker {
        case lower
        case upper
        case center
    }
    public var lowerBound: CGFloat = 0 {
        didSet {
            setNeedsLayout()
        }
    }
    public var upperBound: CGFloat = 1 {
        didSet {
            setNeedsLayout()
        }
    }
    public var selectionColor: UIColor = .blue
    public var defaultColor: UIColor = .lightGray
    
    public var minimumRangeDistance: CGFloat = Constants.defaultMinimumRangeDistance
    
    private let lowerBoundTintView = UIView()
    private let upperBoundTintView = UIView()
    private let cropFrameView = UIImageView()
    
    private var selectedMarker: Marker?
    private var selectedMarkerHorizontalOffet: CGFloat = 0
    private var isBoundCropHighlighted: Bool = false
    private var isRangePagingEnabled: Bool = false

    public let chartView = WLineChartView()
    public let imageView = UIImageView()
    
    private static let rangeViewTintColor = UIColor {
        $0.userInterfaceStyle != .dark
            ? UIColor(red: 239/255.0, green: 239/255.0, blue: 244/255.0, alpha: 0.5)
            : UIColor(red: 24/255.0, green: 34/255.0, blue: 45/255.0, alpha: 0.5)
    }

    
    override public init(frame: CGRect) {
        super.init(frame: frame)
        
        layoutMargins = UIEdgeInsets(top: Constants.cropIndicatorLineWidth,
                                     left: Constants.horizontalContentMargin,
                                     bottom: Constants.cropIndicatorLineWidth,
                                     right: Constants.horizontalContentMargin)
        
        self.setup()
    }
    
    func setup() {
        isMultipleTouchEnabled = false
        
        chartView.backgroundColor = .clear
        
        let gestureRecognizer = UIPanGestureRecognizer()
        gestureRecognizer.delegate = self
        gestureRecognizer.cancelsTouchesInView = false
        addGestureRecognizer(gestureRecognizer)
        
        addSubview(chartView)
        addSubview(imageView)
        addSubview(lowerBoundTintView)
        addSubview(upperBoundTintView)
        addSubview(cropFrameView)
        cropFrameView.isUserInteractionEnabled = false
        cropFrameView.image = .airBundle("ChartRangeSelectionFrame")
        
        chartView.isHidden = true // always displayed as image
        imageView.contentMode = .center
        
        chartView.isUserInteractionEnabled = false
        lowerBoundTintView.isUserInteractionEnabled = false
        upperBoundTintView.isUserInteractionEnabled = false
        
        chartView.layer.cornerRadius = 5
        imageView.layer.cornerRadius = 5
        upperBoundTintView.layer.cornerRadius = 5
        lowerBoundTintView.layer.cornerRadius = 5
        
        chartView.layer.masksToBounds = true
        upperBoundTintView.layer.masksToBounds = true
        lowerBoundTintView.layer.masksToBounds = true
        
        updateTheme()
        
        layoutViews()
    }
    
    public func updateTheme() {
        self.lowerBoundTintView.backgroundColor = Self.rangeViewTintColor
        self.upperBoundTintView.backgroundColor = Self.rangeViewTintColor
    }
    
    public override func awakeFromNib() {
        super.awakeFromNib()
        
        self.setup()
    }
    
    public var rangeDidChangeClosure: ((ClosedRange<CGFloat>) -> Void)?
    public var touchedOutsideClosure: (() -> Void)?

    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }
    
    public func setRangePaging(enabled: Bool, minimumSize: CGFloat) {
        isRangePagingEnabled = enabled
        minimumRangeDistance = minimumSize
    }
    
    public func setRange(_ range: ClosedRange<CGFloat>, animated: Bool) {
        let closure = {
            self.lowerBound = range.lowerBound
            self.upperBound = range.upperBound
            self.layoutIfNeeded()
        }
        
        if animated {
            UIView.animate(withDuration: 0.25, animations: closure)
        } else {
            closure()
        }
    }
    
    public override func layoutSubviews() {
        super.layoutSubviews()
        
        layoutViews()
    }
    
    public override var isEnabled: Bool {
        get {
            return super.isEnabled
        }
        set {
            if newValue == false {
                selectedMarker = nil
            }
            super.isEnabled = newValue
        }
    }
    
    // MARK: - Touches
    
    public override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard isEnabled else { return }
        guard let point = touches.first?.location(in: self) else { return }
        
        if abs(locationInView(for: upperBound) - point.x + Constants.markerSelectionRange / 2) < Constants.markerSelectionRange {
            selectedMarker = .upper
            selectedMarkerHorizontalOffet = point.x - locationInView(for: upperBound)
            isBoundCropHighlighted = true
        } else if abs(locationInView(for: lowerBound) - point.x - Constants.markerSelectionRange / 2) < Constants.markerSelectionRange {
            selectedMarker = .lower
            selectedMarkerHorizontalOffet = point.x - locationInView(for: lowerBound)
            isBoundCropHighlighted = true
        } else if point.x > locationInView(for: lowerBound) && point.x < locationInView(for: upperBound) {
            selectedMarker = .center
            selectedMarkerHorizontalOffet = point.x - locationInView(for: lowerBound)
            isBoundCropHighlighted = true
        } else {
            selectedMarker = nil
            return
        }
        
        sendActions(for: .touchDown)
    }
    
    public override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard isEnabled else { return }
        guard let selectedMarker = selectedMarker else { return }
        guard let point = touches.first?.location(in: self) else { return }
        
        let horizontalPosition = point.x - selectedMarkerHorizontalOffet
        let fraction = fractionFor(offsetX: horizontalPosition)
        updateMarkerOffset(selectedMarker, fraction: fraction)
        
        sendActions(for: .valueChanged)
    }
    
    public override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard isEnabled else { return }
        guard let selectedMarker = selectedMarker else {
            touchedOutsideClosure?()
            return
        }
        guard let point = touches.first?.location(in: self) else { return }
        
        let horizontalPosition = point.x - selectedMarkerHorizontalOffet
        let fraction = fractionFor(offsetX: horizontalPosition)
        updateMarkerOffset(selectedMarker, fraction: fraction)
        
        self.selectedMarker = nil
        self.isBoundCropHighlighted = false
        if bounds.contains(point) {
            sendActions(for: .touchUpInside)
        } else {
            sendActions(for: .touchUpOutside)
        }
        rangeDidChangeClosure?(lowerBound...upperBound)
    }
    
    public override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        self.selectedMarker = nil
        self.isBoundCropHighlighted = false
        sendActions(for: .touchCancel)
    }
    
    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }
}

private extension RangeChartView {
    var contentFrame: CGRect {
        return CGRect(x: layoutMargins.right,
                      y: layoutMargins.top,
                      width: (bounds.width - layoutMargins.right - layoutMargins.left),
                      height: bounds.height - layoutMargins.top - layoutMargins.bottom)
    }
    
    func locationInView(for fraction: CGFloat) -> CGFloat {
        return contentFrame.minX + contentFrame.width * fraction
    }
    
    func locationInView(for fraction: Double) -> CGFloat {
        return locationInView(for: CGFloat(fraction))
    }
    
    func fractionFor(offsetX: CGFloat) -> CGFloat {
        guard contentFrame.width > 0 else {
            return 0
        }
        
        return max(0, min(CGFloat((offsetX - contentFrame.minX ) / contentFrame.width), 1))
    }
    
    private func updateMarkerOffset(_ marker: Marker, fraction: CGFloat, notifyDelegate: Bool = true) {
        let fractionToCount: CGFloat
        if isRangePagingEnabled {
            guard let minValue = stride(from: CGFloat(0.0), through: CGFloat(1.0), by: minimumRangeDistance).min(by: { abs($0 - fraction) < abs($1 - fraction) }) else { return }
            fractionToCount = minValue
        } else {
            fractionToCount = fraction
        }

        switch marker {
        case .lower:
            lowerBound = min(fractionToCount, upperBound - minimumRangeDistance)
        case .upper:
            upperBound = max(fractionToCount, lowerBound + minimumRangeDistance)
        case .center:
            let distance = upperBound - lowerBound
            lowerBound = max(0, min(fractionToCount, 1 - distance))
            upperBound = lowerBound + distance
        }
        if notifyDelegate {
            rangeDidChangeClosure?(lowerBound...upperBound)
        }
        UIView.animate(withDuration: isRangePagingEnabled ? 0.1 : 0) {
            self.layoutIfNeeded()
        }
    }
    
    // MARK: - Layout
    
    func layoutViews() {
        cropFrameView.frame = CGRect(x: locationInView(for: lowerBound),
                                     y: contentFrame.minY - Constants.cropIndicatorLineWidth,
                                     width: locationInView(for: upperBound) - locationInView(for: lowerBound),
                                     height: contentFrame.height + Constants.cropIndicatorLineWidth * 2)
        
        let chartFrame = contentFrame.insetBy(dx: 0, dy: -8)
        if chartView.frame != chartFrame {
            chartView.frame = chartFrame
            imageView.frame = chartFrame
        }
        
        lowerBoundTintView.frame = CGRect(x: contentFrame.minX,
                                          y: contentFrame.minY,
                                          width: max(0, locationInView(for: lowerBound) - contentFrame.minX + Constants.titntAreaWidth),
                                          height: contentFrame.height)
        
        upperBoundTintView.frame = CGRect(x: locationInView(for: upperBound) - Constants.titntAreaWidth,
                                          y: contentFrame.minY,
                                          width: max(0, contentFrame.maxX - locationInView(for: upperBound) + Constants.titntAreaWidth),
                                          height: contentFrame.height)
    }
}
