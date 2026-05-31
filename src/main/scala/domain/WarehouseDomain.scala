package domain

final case class Product(id: String, name: String, unitWeight: Double, unitPrice: BigDecimal)
final case class OrderItem(productId: String, quantity: Int)
final case class Order(id: String, items: List[OrderItem])

final case class PackagingRule(maxWeight: Double, packageName: String)
final case class ShippingRate(maxWeight: Double, cost: BigDecimal)
final case class WarehouseConfig(
    shippingRates: List[ShippingRate],
    maxParcelWeight: Double,
    packagingRules: List[PackagingRule],
    freeShippingFrom: BigDecimal
)

final case class InventoryItem(product: Product, quantity: Int)

final case class PackedOrder(
    orderId: String,
    reservedItems: Map[String, Int],
    totalWeight: Double,
    totalPrice: BigDecimal,
    packageType: String,
    shippingCost: BigDecimal
)

final case class ShippedOrder(
    orderId: String,
    packageType: String,
    shippingCost: BigDecimal
)

final case class WarehouseState(
    inventory: Map[String, InventoryItem],
    packedOrders: Map[String, PackedOrder],
    shippedOrders: Map[String, ShippedOrder]
)
