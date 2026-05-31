package domain

import cats.Monad
import cats.syntax.all.*
import domain.algebra.{WarehouseLoggerAlg, WarehouseReaderAlg, WarehouseStateAlg}

/** Предметная логика склада целиком в F[_] через tagless-алгебры. */
final class WarehouseService[F[_]: Monad](
    reader: WarehouseReaderAlg[F],
    logger: WarehouseLoggerAlg[F],
    state: WarehouseStateAlg[F]
):
  private def calculateTotals(
      order: Order,
      inventory: Map[String, InventoryItem]
  ): Either[String, (Double, BigDecimal)] =
    order.items.foldLeft[Either[String, (Double, BigDecimal)]](Right((0.0, BigDecimal(0)))) {
      case (accEither, item) =>
        for
          acc <- accEither
          inv <- inventory.get(item.productId).toRight(s"Неизвестный товар: ${item.productId}")
          (w, p) = acc
        yield (
          w + inv.product.unitWeight * item.quantity,
          p + inv.product.unitPrice * item.quantity
        )
    }

  def processOrder(order: Order): F[Either[String, PackedOrder]] =
    for
      _ <- logger.info(s"Начинаем обработку заказа ${order.id}")
      st <- state.getState
      result <- Monad[F].pure(calculateTotals(order, st.inventory)).flatMap {
        case Left(err) =>
          logger.info(s"Проверка не пройдена: $err").as(Left(err))
        case Right((weight, totalPrice)) =>
          reader.canAssemble(order, st.inventory).flatMap {
            case false =>
              logger
                .info("Заказ не проходит проверку Reader (вес/остатки)")
                .as(Left("Заказ не проходит проверку Reader (вес/остатки)"))
            case true =>
              processAfterValidation(order, weight, totalPrice)
          }
      }
    yield result

  private def processAfterValidation(
      order: Order,
      weight: Double,
      totalPrice: BigDecimal
  ): F[Either[String, PackedOrder]] =
    for
      reserved <- state.reserveItems(order)
      result <- reserved match
        case Left(err) =>
          logger.info(s"Резервирование отклонено: $err").as(Left(err))
        case Right(items) =>
          for
            _ <- logger.info(s"Резервирование успешно: $items")
            _ <- logger.info(s"Расчет веса: $weight")
            pkg <- reader.packageType(weight)
            _ <- logger.info(s"Выбор упаковки: $pkg")
            delivery <- reader.shippingCost(weight, totalPrice)
            _ <- logger.info(s"Стоимость доставки: $delivery")
            packed = PackedOrder(order.id, items, weight, totalPrice, pkg, delivery)
            packedResult <- state.packOrder(packed)
            finalResult <- packedResult match
              case Left(err) =>
                logger.info(s"Упаковка не выполнена: $err").as(Left(err))
              case Right(done) =>
                state.shipOrder(done.orderId).flatMap {
                  case Left(err) =>
                    logger.info(s"Отправка не выполнена: $err").as(Left(err))
                  case Right(_) =>
                    logger.info(s"Заказ ${done.orderId} отправлен").as(Right(done))
                }
          yield finalResult
    yield result

object WarehouseService:
  def apply[F[_]: Monad](using
      reader: WarehouseReaderAlg[F],
      logger: WarehouseLoggerAlg[F],
      state: WarehouseStateAlg[F]
  ): WarehouseService[F] =
    new WarehouseService(reader, logger, state)
