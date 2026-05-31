package domain.algebra

import domain.*

/** Tagless-алгебра изменения состояния склада. */
trait WarehouseStateAlg[F[_]]:
  def getState: F[WarehouseState]
  def receiveShipment(items: List[(Product, Int)]): F[Unit]
  def reserveItems(order: Order): F[Either[String, Map[String, Int]]]
  def packOrder(packed: PackedOrder): F[Either[String, PackedOrder]]
  def shipOrder(orderId: String): F[Either[String, ShippedOrder]]
