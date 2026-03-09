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
  def processOrder(order: Order)(using Reader[Config], Writer[AuditEntry], State[AppState], Raise[OrderError]): Price = {
    val config   = Reader.ask
    Raise.ensure(order.items.nonEmpty, OrderError.EmptyOrder)
    val customer = Raise.ensureWith(
      State.get.customers.get(order.customerId),
      OrderError.CustomerNotFound(order.customerId)
    )
    Writer.tell(AuditEntry(s"Processing order ${order.id} for ${customer.name}"))
    val discount = customer.tier match {
      case Tier.Enterprise => config.enterpriseDiscount
      case Tier.Startup    => config.startupDiscount
      case Tier.Free       => Raise.raise(OrderError.NotEligible)
    }
    State.modify[AppState](_.addProcessed(order.id))
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
          Logic.run[Config, AuditEntry, AppState, OrderError, Price](initialState, config) {
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
          Logic.run[Config, AuditEntry, AppState, OrderError, Price](initialState, config) {
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
          Logic.run[Config, AuditEntry, AppState, OrderError, Price](initialState, config) {
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
          Logic.run[Config, AuditEntry, AppState, OrderError, Price](initialState, config) {
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
          Logic.run[Config, AuditEntry, AppState, OrderError, Price](initialState, config) {
            processOrder(order)
          }
        assertTrue(result == Left(OrderError.CustomerNotFound(999)))
      }
    ),
    suite("Partial capabilities")(
      test("runReader — only Reader") {
        val result = Logic.runReader(config) {
          Reader.ask.enterpriseDiscount
        }
        assertTrue(result == 0.2)
      },
      test("Reader.inquire infers types") {
        val result = Logic.runReader(config) {
          Reader.inquire(_.enterpriseDiscount)
        }
        assertTrue(result == 0.2)
      },
      test("State.inspect infers types") {
        val (_, result) = Logic.runState(initialState) {
          State.inspect(_.customers.size)
        }
        assertTrue(result == 3)
      },
      test("runState — only State") {
        val (finalState, result) = Logic.runState(0) {
          State.modify[Int](_ + 1)
          State.modify[Int](_ + 1)
          State.get
        }
        assertTrue(result == 2, finalState == 2)
      },
      test("runEither — only Raise") {
        val ok  = Logic.runEither[String, Int](42)
        val err = Logic.runEither[String, Int](Raise.raise("boom"))
        assertTrue(ok == Right(42), err == Left("boom"))
      }
    ),
    suite("Error recovery")(
      test("catchError eliminates error type") {
        val result = Recover.catchError[String, Int] {
          Raise.raise("fail")
        }(e => e.length)
        assertTrue(result == 4)
      },
      test("catchEither wraps in Either") {
        val ok  = Recover.catchEither[String, Int](42)
        val err = Recover.catchEither[String, Int](Raise.raise("no"))
        assertTrue(ok == Right(42), err == Left("no"))
      },
      test("recover rolls back state and logs") {
        val (finalState, logs, result) =
          Logic.run[Unit, String, Int, String, Int](0, ()) {
            State.set(10)
            Writer.tell("before")
            val recovered = Recover.recover[String, Int, String, Int]() {
              State.set(99)
              Writer.tell("inside")
              Raise.raise("oops")
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
          Logic.run[Unit, String, Int, String, Int](0, ()) {
            val v = Recover.recover[String, Int, String, Int]() {
              State.set(42)
              Writer.tell("kept")
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
        def step1(using State[Int], Writer[String]): Unit = {
          State.modify[Int](_ + 1)
          Writer.tell("step1")
        }

        def step2(using State[Int], Writer[String], Raise[String]): Int = {
          val v = State.get
          Raise.ensure(v > 0, "must be positive")
          Writer.tell("step2")
          v * 10
        }

        val (finalState, logs, result) =
          Logic.run[Unit, String, Int, String, Int](0, ()) {
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
