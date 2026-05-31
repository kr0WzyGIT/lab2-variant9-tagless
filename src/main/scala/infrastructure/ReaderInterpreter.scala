package infrastructure

import cats.Applicative
import cats.syntax.functor.*
import domain.*
import domain.algebra.WarehouseReaderAlg

/** Интерпретация Reader-алгебры: конфигурация зашита в интерпретатор, результат в F. */
final class ReaderInterpreter[F[_]: Applicative](config: WarehouseConfig) extends WarehouseReaderAlg[F]:

  override def freeShipping(totalPrice: BigDecimal): F[Boolean] =
    Applicative[F].pure(totalPrice >= config.freeShippingFrom)

  override def packageType(weight: Double): F[String] =
    Applicative[F].pure(
      config.packagingRules
        .sortBy(_.maxWeight)
        .find(weight <= _.maxWeight)
        .map(_.packageName)
        .getOrElse("Крупная усиленная")
    )

  override def shippingCost(weight: Double, totalPrice: BigDecimal): F[BigDecimal] =
    freeShipping(totalPrice).map { isFree =>
      if isFree then BigDecimal(0)
      else
        config.shippingRates
          .sortBy(_.maxWeight)
          .find(weight <= _.maxWeight)
          .map(_.cost)
          .getOrElse(BigDecimal(900))
    }

  override def canAssemble(order: Order, inventory: Map[String, InventoryItem]): F[Boolean] =
    val hasStock = order.items.forall { item =>
      inventory.get(item.productId).exists(_.quantity >= item.quantity)
    }
    val totalWeight = order.items.foldLeft(0.0) { (acc, item) =>
      val oneWeight = inventory.get(item.productId).map(_.product.unitWeight).getOrElse(0.0)
      acc + oneWeight * item.quantity
    }
    Applicative[F].pure(hasStock && totalWeight <= config.maxParcelWeight)

object ReaderInterpreter:
  def apply[F[_]: Applicative](config: WarehouseConfig): ReaderInterpreter[F] =
    new ReaderInterpreter(config)
