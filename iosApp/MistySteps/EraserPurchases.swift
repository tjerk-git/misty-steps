import Foundation
import StoreKit

enum BuyResult { case bought, cancelled, unavailable }

/// The magic eraser as a one-time App Store purchase (a Non-Consumable product, `magic_eraser`). Ownership is read from the
/// App Store's entitlements, so it comes back on a new iPhone with the same Apple ID, and is remembered so the tool keeps
/// working offline. Nothing here throws to the screen: when the store cannot be reached the product is simply not for sale
/// right now, and an owned eraser stays owned.
@MainActor
final class EraserPurchases: ObservableObject {
    static let productId = "magic_eraser"
    private static let ownedKey = "eraser_owned"

    /// True once the eraser is yours.
    @Published private(set) var owned = UserDefaults.standard.bool(forKey: EraserPurchases.ownedKey)
    /// The price as the App Store shows it ("EUR 3,99" in the person's own format), once known.
    @Published private(set) var priceText: String?

    private var product: Product?
    private var updates: Task<Void, Never>?

    /// Loads the product and what is owned, and keeps listening for purchases made elsewhere (Ask to Buy, another device).
    func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let t) = result { await t.finish() }
                await self?.refreshOwned()
            }
        }
        Task { await load() }
    }

    func load() async {
        do {
            product = try await Product.products(for: [Self.productId]).first
            priceText = product?.displayPrice
        } catch {
            product = nil
        }
        await refreshOwned()
    }

    /// Reads the entitlements. Only a successful read can take ownership away (a refund); a failed read keeps what we knew.
    private func refreshOwned() async {
        var found = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let t) = result, t.productID == Self.productId, t.revocationDate == nil { found = true }
        }
        setOwned(found)
    }

    private func setOwned(_ value: Bool) {
        owned = value
        UserDefaults.standard.set(value, forKey: Self.ownedKey)
    }

    func buy() async -> BuyResult {
        if product == nil { await load() }
        guard let product else { return .unavailable }
        do {
            switch try await product.purchase() {
            case .success(let result):
                guard case .verified(let t) = result else { return .unavailable }
                await t.finish()
                setOwned(true)
                return .bought
            case .userCancelled, .pending:
                return .cancelled
            @unknown default:
                return .unavailable
            }
        } catch {
            return .unavailable
        }
    }

    /// "Restore purchases": asks the App Store to sync, then reads the entitlements again.
    func restore() async {
        try? await AppStore.sync()
        await refreshOwned()
    }
}
