package domain.algebra

import domain.*

/** Tagless-алгебра чтения конфигурации склада. Все операции возвращают F[_]. */
trait WarehouseReaderAlg[F[_]]:
  def canAssemble(order: Order, inventory: Map[String, InventoryItem]): F[Boolean]
  def packageType(weight: Double): F[String]
  def shippingCost(weight: Double, totalPrice: BigDecimal): F[BigDecimal]
  def freeShipping(totalPrice: BigDecimal): F[Boolean]
