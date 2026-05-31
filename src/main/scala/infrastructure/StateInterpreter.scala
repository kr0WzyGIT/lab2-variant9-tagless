package infrastructure

import cats.effect.{Ref, Sync}
import domain.*
import domain.algebra.WarehouseStateAlg

/** Интерпретация State-алгебры через Ref[F, WarehouseState]. */
final class StateInterpreter[F[_]: Sync](stateRef: Ref[F, WarehouseState]) extends WarehouseStateAlg[F]:

  override def getState: F[WarehouseState] =
    stateRef.get

  override def receiveShipment(items: List[(Product, Int)]): F[Unit] =
    stateRef.update { st =>
      val updated = items.foldLeft(st.inventory) { case (inv, (product, delta)) =>
        val current = inv.get(product.id).map(_.quantity).getOrElse(0)
        inv.updated(product.id, InventoryItem(product, current + delta))
      }
      st.copy(inventory = updated)
    }

  override def reserveItems(order: Order): F[Either[String, Map[String, Int]]] =
    stateRef.modify { st =>
      val canReserve =
        order.items.forall(it => st.inventory.get(it.productId).exists(_.quantity >= it.quantity))
      if !canReserve then
        (st, Left("Недостаточно товара на складе"))
      else
        val updatedInventory = order.items.foldLeft(st.inventory) { (inv, item) =>
          val old = inv(item.productId)
          inv.updated(item.productId, old.copy(quantity = old.quantity - item.quantity))
        }
        val reserved = order.items.map(i => i.productId -> i.quantity).toMap
        (st.copy(inventory = updatedInventory), Right(reserved))
    }

  override def packOrder(packed: PackedOrder): F[Either[String, PackedOrder]] =
    stateRef.modify { st =>
      if st.packedOrders.contains(packed.orderId) then
        (st, Left("Заказ уже упакован"))
      else
        (st.copy(packedOrders = st.packedOrders.updated(packed.orderId, packed)), Right(packed))
    }

  override def shipOrder(orderId: String): F[Either[String, ShippedOrder]] =
    stateRef.modify { st =>
      st.packedOrders.get(orderId) match
        case None =>
          (st, Left("Заказ не найден среди упакованных"))
        case Some(packed) =>
          val shipped = ShippedOrder(packed.orderId, packed.packageType, packed.shippingCost)
          val next = st.copy(
            packedOrders = st.packedOrders - orderId,
            shippedOrders = st.shippedOrders.updated(orderId, shipped)
          )
          (next, Right(shipped))
    }

object StateInterpreter:
  def apply[F[_]: Sync](stateRef: Ref[F, WarehouseState]): StateInterpreter[F] =
    new StateInterpreter(stateRef)
