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
  def processOrder(order: Order)(using Reader[Config], Writer[AuditEntry], State[AppState], Raise[OrderError]) = {
    val config   = ask
    ensure(order.items.nonEmpty, OrderError.EmptyOrder)
    val customer = ensureWith(
      get.customers.get(order.customerId),
      OrderError.CustomerNotFound(order.customerId)
    )
    tell(AuditEntry(s"Processing order ${order.id} for ${customer.name}"))
    val discount = customer.tier match {
      case Tier.Enterprise => config.enterpriseDiscount
      case Tier.Startup    => config.startupDiscount
      case Tier.Free       => raise(OrderError.NotEligible)
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
        val order                      = Order(101, customerId = 1, items = List("widget"), total = 100.0)
        val (finalState, logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Right(Price(80.0)),
          finalState.processed.contains(101),
          logs.size == 1,
          logs.head.msg.contains("Acme Corp")
        )
      },
      test("startup order") {
        val order                      = Order(102, customerId = 2, items = List("gadget"), total = 200.0)
        val (finalState, logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Right(Price(180.0)),
          finalState.processed.contains(102)
        )
      }
    ),
    suite("Logic.run — error paths")(
      test("empty order fails") {
        val order                      = Order(103, customerId = 1, items = Nil, total = 50.0)
        val (finalState, logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Left(OrderError.EmptyOrder),
          finalState.processed.isEmpty,
          logs.isEmpty
        )
      },
      test("free tier not eligible") {
        val order                      = Order(104, customerId = 3, items = List("item"), total = 100.0)
        val (finalState, logs, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(
          result == Left(OrderError.NotEligible),
          logs.size == 1
        )
      },
      test("customer not found") {
        val order          = Order(105, customerId = 999, items = List("item"), total = 100.0)
        val (_, _, result) =
          Logic.run(initialState, config) {
            processOrder(order)
          }
        assertTrue(result == Left(OrderError.CustomerNotFound(999)))
      }
    ),
    suite("Partial capabilities")(
      test("runReader — ask") {
        val result = Logic.runReader(config) {
          ask.enterpriseDiscount
        }
        assertTrue(result == 0.2)
      },
      test("runReader — inquire") {
        val result = Logic.runReader(config) {
          inquire(_.enterpriseDiscount)
        }
        assertTrue(result == 0.2)
      },
      test("runState — get") {
        val (_, result) = Logic.runState(initialState) {
          get.customers.size
        }
        assertTrue(result == 3)
      },
      test("runState — set") {
        val (finalState, _) = Logic.runState(0) {
          set(42)
        }
        assertTrue(finalState == 42)
      },
      test("runState — modify") {
        val (finalState, result) = Logic.runState(0) {
          modify(_ + 1)
          modify(_ + 1)
          get
        }
        assertTrue(result == 2, finalState == 2)
      },
      test("runState — inspect") {
        val (_, result) = Logic.runState(initialState) {
          inspect(_.customers.size)
        }
        assertTrue(result == 3)
      },
      test("runWriter — tell") {
        val (logs, _) = Logic.runWriter {
          tell("hello")
          tell("world")
        }
        assertTrue(logs == Vector("hello", "world"))
      },
      test("runEither — raise") {
        val ok  = Logic.runEither(42)
        val err = Logic.runEither(raise[String]("boom"))
        assertTrue(ok == Right(42), err == Left("boom"))
      },
      test("runEither — ensure") {
        val ok  = Logic.runEither(ensure(true, "fail"))
        val err = Logic.runEither(ensure(false, "fail"))
        assertTrue(ok == Right(()), err == Left("fail"))
      },
      test("runEither — ensureWith") {
        val ok  = Logic.runEither(ensureWith(Some(42), "fail"))
        val err = Logic.runEither(ensureWith(None, "fail"))
        assertTrue(ok == Right(42), err == Left("fail"))
      }
    ),
    suite("Error recovery")(
      test("catchError eliminates error type") {
        val result = Recover.catchError[String, Int] {
          raise("fail")
        }(e => e.length)
        assertTrue(result == 4)
      },
      test("catchEither wraps in Either") {
        val ok  = Recover.catchEither[String, Int](42)
        val err = Recover.catchEither[String, Int](raise("no"))
        assertTrue(ok == Right(42), err == Left("no"))
      },
      test("recover rolls back state and logs") {
        val (finalState, logs, result) =
          Logic.run(0, ()) {
            set(10)
            tell("before")
            val recovered = Recover.recover[String, Int, String, Int]() {
              set(99)
              tell("inside")
              raise("oops")
            }(_ => -1)
            recovered
          }
        assertTrue(
          result == Right(-1),
          finalState == 10,
          logs == Vector("before")
        )
      },
      test("recover keeps state/logs when no error") {
        val (finalState, logs, result) =
          Logic.run(0, ()) {
            val v = Recover.recover[String, Int, String, Int]() {
              set(42)
              tell("kept")
              100
            }(_ => -1)
            v
          }
        assertTrue(
          result == Right(100),
          finalState == 42,
          logs == Vector("kept")
        )
      }
    ),
    suite("Composition")(
      test("functions compose naturally through context propagation") {
        def step1(using State[Int], Writer[String]) = {
          modify(_ + 1)
          tell("step1")
        }

        def step2(using State[Int], Writer[String], Raise[String]) = {
          val v = get
          ensure(v > 0, "must be positive")
          tell("step2")
          v * 10
        }

        val (finalState, logs, result) =
          Logic.run(0, ()) {
            step1
            step2
          }
        assertTrue(
          result == Right(10),
          finalState == 1,
          logs == Vector("step1", "step2")
        )
      }
    )
  )
}
