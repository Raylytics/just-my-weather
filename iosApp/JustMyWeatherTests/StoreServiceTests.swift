import Shared
import StoreKitTest
import XCTest
@testable import JustMyWeather

/// The id the shared code asks for, as the iOS gateway sends it: this is
/// what proves Products.storekit and AppStoreProducts agree.
private let removeAds = AppStoreProducts.shared.appStoreId(sharedId: "remove_ads")

/// Remove Ads against the local StoreKit configuration (Products.storekit):
/// the offer, a purchase, ownership, and a restore after the store forgets
/// nothing — with no App Store Connect record and no network.
final class StoreServiceTests: XCTestCase {
    private var session: SKTestSession!
    private let store = StoreService()

    override func setUpWithError() throws {
        session = try SKTestSession(configurationFileNamed: "Products")
        session.resetToDefaultState()
        session.disableDialogs = true
        session.clearTransactions()
    }

    func testTheOfferIsTheNinetyNineCentProduct() {
        let offer = expectation(description: "offer")
        store.offer(productId: removeAds) { product in
            XCTAssertEqual(product?.productId, removeAds)
            XCTAssertEqual(product?.formattedPrice, "$0.99")
            offer.fulfill()
        }
        wait(for: [offer], timeout: 10)
    }

    func testABoughtProductIsOwned() {
        let bought = expectation(description: "purchase")
        store.purchase(productId: removeAds) { result in
            XCTAssertEqual(result, StorePurchase.purchased)
            bought.fulfill()
        }
        wait(for: [bought], timeout: 10)

        let owned = expectation(description: "owned")
        store.ownedProducts { products in
            XCTAssertEqual(products, [removeAds])
            owned.fulfill()
        }
        wait(for: [owned], timeout: 10)
    }

    func testNothingBoughtIsNothingOwned() {
        let owned = expectation(description: "owned")
        store.ownedProducts { products in
            XCTAssertEqual(products, [])
            owned.fulfill()
        }
        wait(for: [owned], timeout: 10)
    }

    func testAnUnknownProductHasNoOffer() {
        let offer = expectation(description: "offer")
        store.offer(productId: "no_such_product") { product in
            XCTAssertNil(product)
            offer.fulfill()
        }
        wait(for: [offer], timeout: 10)
    }
}
