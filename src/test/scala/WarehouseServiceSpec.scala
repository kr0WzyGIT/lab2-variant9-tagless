import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import domain.*
import domain.algebra.{WarehouseLoggerAlg, WarehouseReaderAlg, WarehouseStateAlg}
import domain.WarehouseService
import infrastructure.*
import org.scalatest.funsuite.AnyFunSuite

class WarehouseServiceSpec extends AnyFunSuite:
  private val keyboard = Product("kb", "Клавиатура", 0.8, BigDecimal(2500))
  private val monitor = Product("mn", "Монитор", 4.5, BigDecimal(18000))

  private val testConfig = WarehouseConfig(
    shippingRates = List(
      ShippingRate(1.0, 200),
      ShippingRate(5.0, 450),
      ShippingRate(15.0, 800)
    ),
    maxParcelWeight = 30.0,
    packagingRules = List(
      PackagingRule(1.0, "Малый пакет"),
      PackagingRule(5.0, "Средняя коробка"),
      PackagingRule(30.0, "Крупная упаковка")
    ),
    freeShippingFrom = BigDecimal(20000)
  )

  private def inventory(kbQty: Int, monQty: Int): WarehouseState =
    WarehouseState(
      inventory = Map(
        keyboard.id -> InventoryItem(keyboard, kbQty),
        monitor.id -> InventoryItem(monitor, monQty)
      ),
      packedOrders = Map.empty,
      shippedOrders = Map.empty
    )

  private def processInIO(
      order: Order,
      state: WarehouseState,
      config: WarehouseConfig = testConfig
  ): Either[String, PackedOrder] =
    (for
      stateRef <- Ref.of[IO, WarehouseState](state)
      logRef <- Ref.of[IO, Vector[String]](Vector.empty[String])
      given WarehouseReaderAlg[IO] = ReaderInterpreter[IO](config)
      given WarehouseLoggerAlg[IO] = LoggerInterpreter[IO](logRef)
      given WarehouseStateAlg[IO] = StateInterpreter[IO](stateRef)
      result <- WarehouseService[IO]().processOrder(order)
    yield result).unsafeRunSync()

  test("Товара хватает -> заказ собирается"):
    val order = Order("ok-1", List(OrderItem("kb", 2)))
    assert(processInIO(order, inventory(10, 2)).isRight)

  test("Товара не хватает -> отказ"):
    val order = Order("fail-1", List(OrderItem("kb", 50)))
    assert(processInIO(order, inventory(10, 2)).isLeft)

  test("Тяжелый заказ -> выбирается крупная упаковка"):
    val order = Order("heavy-1", List(OrderItem("mn", 2)))
    assert(processInIO(order, inventory(2, 5)).exists(_.packageType == "Крупная упаковка"))

  test("Дорогой заказ -> доставка бесплатна"):
    val order = Order("expensive-1", List(OrderItem("mn", 2)))
    assert(processInIO(order, inventory(2, 5)).exists(_.shippingCost == BigDecimal(0)))
