import Shared
import StoreKit

/// iOS twin of PlayBillingGateway, over StoreKit 2. The shared
/// RemoveAdsManager makes every decision; this only asks the App Store.
/// StoreKit verifies transactions itself, so an owned product is simply one
/// with a verified current entitlement — there is no acknowledge step.
final class StoreService: NSObject, StoreBridge {
    private var storefront: String?
    private var updates: Task<Void, Never>?

    /// At launch: read the storefront (a region clue) and listen for
    /// transactions finished outside the app (Ask to Buy, a refund).
    func start() {
        Task { storefront = await Storefront.current?.countryCode }
        updates = Task.detached {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result { await transaction.finish() }
            }
        }
    }

    func storefrontCountryCode() -> String? { storefront }

    func offer(productId: String, completion: @escaping (ProductOffer?) -> Void) {
        Task {
            let product = try? await Product.products(for: [productId]).first
            await MainActor.run {
                completion(product.map { ProductOffer(productId: $0.id, formattedPrice: $0.displayPrice) })
            }
        }
    }

    func ownedProducts(completion: @escaping ([String]?) -> Void) {
        Task {
            var owned: [String] = []
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result, transaction.revocationDate == nil {
                    owned.append(transaction.productID)
                }
            }
            await MainActor.run { completion(owned) }
        }
    }

    func purchase(productId: String, completion: @escaping (StorePurchase) -> Void) {
        Task {
            let outcome: StorePurchase
            do {
                guard let product = try await Product.products(for: [productId]).first else {
                    await MainActor.run { completion(.failed) }
                    return
                }
                switch try await product.purchase() {
                case .success(.verified(let transaction)):
                    await transaction.finish()
                    outcome = .purchased
                case .success(.unverified):
                    outcome = .failed
                case .pending:
                    outcome = .pending
                case .userCancelled:
                    outcome = .cancelled
                @unknown default:
                    outcome = .failed
                }
            } catch {
                outcome = .failed
            }
            await MainActor.run { completion(outcome) }
        }
    }
}
