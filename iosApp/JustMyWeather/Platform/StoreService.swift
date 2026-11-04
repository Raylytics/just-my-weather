import Shared
import StoreKit

/// iOS twin of PlayBillingGateway, over StoreKit 2. The shared
/// RemoveAdsManager makes every decision; this only asks the App Store.
/// StoreKit verifies transactions itself, so an owned product is simply one
/// with a verified current entitlement — there is no acknowledge step.
final class StoreService: NSObject, StoreBridge {
    private var storefront: String?
    private var updates: Task<Void, Never>?
    private var updateHandler: ((String, KotlinBoolean) -> Void)?

    /// At launch: read the storefront (a region clue) and listen for
    /// transactions finished outside the app (Ask to Buy, a refund).
    func start() {
        Task { storefront = await Storefront.current?.countryCode }
        updates = Task.detached { [weak self] in
            for await result in Transaction.updates {
                guard case .verified(let transaction) = result else { continue }
                await transaction.finish()
                let revoked = transaction.revocationDate != nil
                await MainActor.run {
                    self?.updateHandler?(transaction.productID, KotlinBoolean(value: revoked))
                }
            }
        }
    }

    func storefrontCountryCode() -> String? { storefront }

    func onTransactionUpdate(handler: @escaping (String, KotlinBoolean) -> Void) {
        updateHandler = handler
    }

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
            let owned = await currentlyOwned()
            await MainActor.run { completion(owned) }
        }
    }

    /// The Restore purchases button. `AppStore.sync()` is StoreKit 2's restore:
    /// it fetches the account's transactions from the App Store (and may ask
    /// the user to sign in), which a plain entitlement read does not. App
    /// Review requires a button that makes this call (guideline 3.1.1; the
    /// 0.4.0 rejection). A sync the user cancels, or one the store refuses,
    /// throws: that is "no answer", not "owns nothing".
    func restore(completion: @escaping ([String]?) -> Void) {
        Task {
            do {
                try await AppStore.sync()
            } catch {
                await MainActor.run { completion(nil) }
                return
            }
            let owned = await currentlyOwned()
            await MainActor.run { completion(owned) }
        }
    }

    /// Every verified, unrevoked current entitlement's product id.
    private func currentlyOwned() async -> [String] {
        var owned: [String] = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.revocationDate == nil {
                owned.append(transaction.productID)
            }
        }
        return owned
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
