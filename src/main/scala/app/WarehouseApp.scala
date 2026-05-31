package app

import cats.effect.{IO, Ref, Sync}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import domain.*
import domain.algebra.{ConsoleAlg, WarehouseLoggerAlg, WarehouseReaderAlg, WarehouseStateAlg}
import domain.WarehouseService
import infrastructure.*

object WarehouseApp:
  private val keyboard = Product("kb", "Клавиатура", unitWeight = 0.8, unitPrice = BigDecimal(2500))
  private val mouse = Product("ms", "Мышь", unitWeight = 0.2, unitPrice = BigDecimal(1200))
  private val monitor = Product("mn", "Монитор", unitWeight = 4.5, unitPrice = BigDecimal(18000))

  val defaultConfig: WarehouseConfig = WarehouseConfig(
    shippingRates = List(
      ShippingRate(1.0, 200),
      ShippingRate(5.0, 450),
      ShippingRate(15.0, 800)
    ),
    maxParcelWeight = 25.0,
    packagingRules = List(
      PackagingRule(1.0, "Малый пакет"),
      PackagingRule(5.0, "Средняя коробка"),
      PackagingRule(25.0, "Крупная упаковка")
    ),
    freeShippingFrom = BigDecimal(20000)
  )

  val initialState: WarehouseState = WarehouseState(
    inventory = Map(
      keyboard.id -> InventoryItem(keyboard, 20),
      mouse.id -> InventoryItem(mouse, 30),
      monitor.id -> InventoryItem(monitor, 10)
    ),
    packedOrders = Map.empty,
    shippedOrders = Map.empty
  )

  private def parseOrderItems(raw: String): Either[String, List[OrderItem]] =
    val parts = raw.split(",").toList.map(_.trim).filter(_.nonEmpty)
    val parsed = parts.map { token =>
      token.split(":").toList match
        case id :: qty :: Nil =>
          qty.toIntOption match
            case Some(q) if q > 0 => Right(OrderItem(id.trim, q))
            case _                => Left(s"Некорректное количество: $token")
        case _ => Left(s"Некорректный формат позиции: $token")
    }
    parsed.foldRight[Either[String, List[OrderItem]]](Right(Nil)) {
      case (Right(item), Right(acc)) => Right(item :: acc)
      case (Left(err), _)            => Left(err)
      case (_, Left(err))            => Left(err)
    }

  /** Сценарий целиком параметризован F[_]; алгебры приходят через implicit/given. */
  def program[F[_]: Sync](using
      reader: WarehouseReaderAlg[F],
      logger: WarehouseLoggerAlg[F],
      state: WarehouseStateAlg[F],
      console: ConsoleAlg[F]
  ): F[Unit] =
    val service = WarehouseService[F]()
    for
      _ <- console.printLine("Введите id заказа:")
      orderId <- console.readLine
      _ <- console.printLine("Введите позиции в формате id:кол-во,id:кол-во (например kb:1,mn:1)")
      rawItems <- console.readLine
      parsed = parseOrderItems(rawItems)
      _ <- parsed match
        case Left(err) =>
          console.printLine(s"Ошибка ввода: $err")
        case Right(items) =>
          val order = Order(orderId, items)
          for
            result <- service.processOrder(order)
            report = result match
              case Left(err) =>
                s"Заказ не собран: $err"
              case Right(packed) =>
                s"""Заказ собран и отправлен.
                   |Упаковка: ${packed.packageType}
                   |Доставка: ${packed.shippingCost}
                   |Вес: ${packed.totalWeight}
                   |Сумма: ${packed.totalPrice}""".stripMargin
            _ <- console.printLine(report)
          yield ()
    yield ()

  /** Сборка окружения для конкретного F (здесь IO) и запуск сценария. */
  def runWithIO: IO[Unit] =
    for
      stateRef <- Ref.of[IO, WarehouseState](initialState)
      logRef <- Ref.of[IO, Vector[String]](Vector.empty[String])
      given WarehouseReaderAlg[IO] = ReaderInterpreter[IO](defaultConfig)
      given WarehouseLoggerAlg[IO] = LoggerInterpreter[IO](logRef)
      given WarehouseStateAlg[IO] = StateInterpreter[IO](stateRef)
      given ConsoleAlg[IO] = ConsoleInterpreter[IO]
      _ <- program[IO]
      log <- logRef.get
      _ <- ConsoleInterpreter[IO].printLine("--- Журнал операций ---")
      _ <- log.traverse_(ConsoleInterpreter[IO].printLine)
    yield ()

  @main def runWarehouseApp(): Unit =
    runWithIO.unsafeRunSync()
