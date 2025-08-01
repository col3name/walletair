
import Foundation
import OrderedCollections

public extension Array {
    
    func dictionaryByKey<Key: Hashable>(_ keyPath: KeyPath<Element, Key>) -> [Key: Element] {
        var dict: [Key: Element] = [:]
        for value in self {
            dict[value[keyPath: keyPath]] = value
        }
        return dict
    }

    func orderedDictionaryByKey<Key: Hashable>(_ keyPath: KeyPath<Element, Key>) -> OrderedDictionary<Key, Element> {
        var dict: OrderedDictionary<Key, Element> = [:]
        for value in self {
            dict[value[keyPath: keyPath]] = value
        }
        return dict
    }
    
    func first<T: Equatable>(whereEqual keyPath: KeyPath<Element, T>, _ value: T) -> Element? {
        first { $0[keyPath: keyPath] == value }
    }
}

public extension Array where Element: Identifiable {
    
    func first(id: Element.ID) -> Element? {
        first { $0.id == id }
    }
}
