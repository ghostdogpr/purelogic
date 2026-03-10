package purelogic

import zio.test.*

object PureLogicSpec extends ZIOSpecDefault {

  // --- Domain types for the test ---
  case class Config(enterpriseDiscount: Double, startupDiscount: Double)
  case class Customer(name: String, tier: Tier)
  enum Tier {
    case Enterprise, Startup, Free
  }

  case class AppState(customers: Map[Int, Customer], processed: Set[Int]) {
    def addProcessed(id: Int): AppState = copy(processed = processed + id)
  }

  case class AuditEntry(msg: String)

  enum OrderError {
    case EmptyOrder
    case NotEligible
    case CustomerNotFound(id: Int)
  }

  case class Order(id: Int, customerId: Int, items: List[String], total: Double)
  case class Price(value: Double)

  // --- The logic under test (direct style!) ---
  def processOrder(order: Order)(using Reader[Config], Writer[AuditEntry], State[AppState], Abort[OrderError]) = {
    val config   = read
    ensure(order.items.nonEmpty, OrderError.EmptyOrder)
    val customer = ensureWith(
      get.customers.get(order.customerId),
      OrderError.CustomerNotFound(order.customerId)
    )
    write(AuditEntry(s"Processing order ${order.id} for ${customer.name}"))
    val discount = customer.tier match {
      case Tier.Enterprise => config.enterpriseDiscount
      case Tier.Startup    => config.startupDiscount
      case Tier.Free       => fail(OrderError.NotEligible)
    }
    modify(_.addProcessed(order.id))
    Price(order.total * (1 - discount))
  }

  // --- Test fixtures ---
  val config       = Config(enterpriseDiscount = 0.2, startupDiscount = 0.1)
  val initialState = AppState(
    customers = Map(
      1 -> Customer("Acme Corp", Tier.Enterprise),
      2 -> Customer("StartupCo", Tier.Startup),
      3 -> Customer("FreeUser", Tier.Free)
    ),
    processed = Set.empty
  )

  def spec = suite("PureLogic")(
    suite("Logic.run — happy path")(
      test("enterprise order") {
        val order          = Order(101, customerId = 1, items = List("widget"), total = 100.0)
        val (logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Right((initialState.addProcessed(101), Price(80.0))),
          logs.size == 1,
          logs.head.msg.contains("Acme Corp")
        )
      },
      test("startup order") {
        val order          = Order(102, customerId = 2, items = List("gadget"), total = 200.0)
        val (logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Right((initialState.addProcessed(102), Price(180.0)))
        )
      }
    ),
    suite("Logic.run — error paths")(
      test("empty order fails") {
        val order          = Order(103, customerId = 1, items = Nil, total = 50.0)
        val (logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Left(OrderError.EmptyOrder),
          logs.isEmpty
        )
      },
      test("free tier not eligible") {
        val order          = Order(104, customerId = 3, items = List("item"), total = 100.0)
        val (logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Left(OrderError.NotEligible),
          logs.size == 1
        )
      },
      test("customer not found") {
        val order       = Order(105, customerId = 999, items = List("item"), total = 100.0)
        val (_, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(result == Left(OrderError.CustomerNotFound(999)))
      }
    ),
    suite("Error recovery")(
      test("recover rolls back state and logs") {
        val (logs, result) =
          Logic.run(0, ()) {
            set(10)
            write("before")
            val recovered = recover {
              set(99)
              write("inside")
              fail("oops")
            }(_ => -1)
            recovered
          }
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before")
        )
      },
      test("recover keeps state/logs when no error") {
        val (logs, result) =
          Logic.run(0, ()) {
            val v = recover {
              set(42)
              write("kept")
              100
            }(_ => -1)
            v
          }
        assertTrue(
          result == Right((42, 100)),
          logs == Vector("kept")
        )
      },
      test("recoverKeepLog rolls back state only") {
        val (logs, result) =
          Logic.run(0, ()) {
            set(10)
            write("before")
            val recovered = recoverKeepLog {
              set(99)
              write("inside")
              fail("oops")
            }(_ => -1)
            recovered
          }
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before", "inside")
        )
      }
    ),
    suite("Composition")(
      test("functions compose naturally through context propagation") {
        def step1(using State[Int], Writer[String]) = {
          modify(_ + 1)
          write("step1")
        }

        def step2(using State[Int], Writer[String], Abort[String]) = {
          val v = get
          ensure(v > 0, "must be positive")
          write("step2")
          v * 10
        }

        val (logs, result) =
          Logic.run(0, ()) {
            step1
            step2
          }
        assertTrue(
          result == Right((1, 10)),
          logs == Vector("step1", "step2")
        )
      }
    )
  )
}
