package purelogic

import scala.util.{Failure, Success}

import zio.test.*

object PureLogicSpec extends ZIOSpecDefault {

  case class Config(discount: Double)
  case class AppState(items: List[String])
  case class Log(msg: String)
  enum AppError { case NotFound(key: String); case Invalid }

  def addItem(item: String)(using Reader[Config], Writer[Log], State[AppState], Abort[AppError]) = {
    ensure(item.nonEmpty, AppError.Invalid)
    val discount = readWith(_.discount)
    update(s => s.copy(items = s.items :+ item))
    write(Log(s"Added $item with discount $discount"))
  }

  def lookupItem(idx: Int)(using State[AppState], Abort[AppError]) = {
    val items = getWith(_.items)
    extractOption(items.lift(idx), AppError.NotFound(s"index $idx"))
  }

  def spec = suite("PureLogic")(
    // ---------------------------------------------------------------------------
    // Reader
    // ---------------------------------------------------------------------------
    suite("Reader")(
      test("read returns the environment") {
        val result = Reader(42)(read)
        assertTrue(result == 42)
      },
      test("readWith projects the environment") {
        val result = Reader("hello")(readWith(_.length))
        assertTrue(result == 5)
      }
    ),
    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------
    suite("State")(
      test("get returns the current state") {
        val (_, result) = State(10)(get)
        assertTrue(result == 10)
      },
      test("getWith projects the state") {
        val (_, result) = State(List(1, 2, 3))(getWith(_.size))
        assertTrue(result == 3)
      },
      test("set replaces the state") {
        val (finalState, _) = State(0)(set(42))
        assertTrue(finalState == 42)
      },
      test("update transforms the state") {
        val (finalState, _) = State(10)(update(_ + 5))
        assertTrue(finalState == 15)
      },
      test("modify transforms state and returns a value") {
        val (finalState, result) = State(10)(modify(s => (s * 2, s + 1)))
        assertTrue(result == 20, finalState == 11)
      },
      test("updateAndGet transforms and returns new state") {
        val (_, result) = State(10)(updateAndGet(_ + 5))
        assertTrue(result == 15)
      },
      test("getAndSet returns old state and replaces") {
        val (finalState, old) = State(10)(getAndSet(42))
        assertTrue(old == 10, finalState == 42)
      },
      test("getAndUpdate returns old state and transforms") {
        val (finalState, old) = State(10)(getAndUpdate(_ + 5))
        assertTrue(old == 10, finalState == 15)
      }
    ),
    // ---------------------------------------------------------------------------
    // Writer
    // ---------------------------------------------------------------------------
    suite("Writer")(
      test("write appends a single entry") {
        val (logs, _) = Writer(write("hello"))
        assertTrue(logs == Vector("hello"))
      },
      test("writeAll appends multiple entries") {
        val (logs, _) = Writer(writeAll(List("a", "b", "c")))
        assertTrue(logs == Vector("a", "b", "c"))
      },
      test("clear removes all entries") {
        val (logs, _) = Writer {
          write("before")
          clear
          write("after")
        }
        assertTrue(logs == Vector("after"))
      }
    ),
    // ---------------------------------------------------------------------------
    // Abort
    // ---------------------------------------------------------------------------
    suite("Abort")(
      test("fail short-circuits with an error") {
        val result = Abort(fail("boom"))
        assertTrue(result == Left("boom"))
      },
      test("ensure passes when condition is true") {
        val result = Abort(ensure(true, "fail"))
        assertTrue(result == Right(()))
      },
      test("ensure fails when condition is false") {
        val result = Abort(ensure(false, "fail"))
        assertTrue(result == Left("fail"))
      },
      test("ensureNot passes when condition is false") {
        val result = Abort(ensureNot(false, "fail"))
        assertTrue(result == Right(()))
      },
      test("ensureNot fails when condition is true") {
        val result = Abort(ensureNot(true, "fail"))
        assertTrue(result == Left("fail"))
      },
      test("extractOption extracts Some") {
        val result = Abort(extractOption(Some(42), "missing"))
        assertTrue(result == Right(42))
      },
      test("extractOption fails on None") {
        val result = Abort(extractOption(None, "missing"))
        assertTrue(result == Left("missing"))
      },
      test("extractEither extracts Right") {
        val result = Abort(extractEither(Right(42)))
        assertTrue(result == Right(42))
      },
      test("extractEither fails on Left") {
        val result = Abort(extractEither(Left("err")))
        assertTrue(result == Left("err"))
      },
      test("extractTry extracts Success") {
        val result = Abort(extractTry(Success(42)))
        assertTrue(result == Right(42))
      },
      test("extractTry fails on Failure") {
        val ex     = new RuntimeException("boom")
        val result = Abort(extractTry(Failure(ex)))
        assertTrue(result == Left(ex))
      },
      test("attempt catches exceptions") {
        val result = Abort(attempt(throw new RuntimeException("boom")))
        assertTrue(result.isLeft)
      },
      test("attempt passes through normal values") {
        val result = Abort(attempt(42))
        assertTrue(result == Right(42))
      }
    ),
    // ---------------------------------------------------------------------------
    // Recovery
    // ---------------------------------------------------------------------------
    suite("Recovery")(
      test("recover rolls back state and logs on error") {
        val (logs, result) =
          Logic.run(0, ()) {
            set(10)
            write("before")
            recover {
              set(99)
              write("inside")
              fail("oops")
            }(_ => -1)
          }
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before")
        )
      },
      test("recover keeps state and logs when no error") {
        val (logs, result) =
          Logic.run(0, ()) {
            recover {
              set(42)
              write("kept")
              100
            }(_ => -1)
          }
        assertTrue(
          result == Right((42, 100)),
          logs == Vector("kept")
        )
      },
      test("recoverKeepLog rolls back state but keeps logs") {
        val (logs, result) =
          Logic.run(0, ()) {
            set(10)
            write("before")
            recoverKeepLog {
              set(99)
              write("inside")
              fail("oops")
            }(_ => -1)
          }
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before", "inside")
        )
      }
    ),
    // ---------------------------------------------------------------------------
    // Logic.run
    // ---------------------------------------------------------------------------
    suite("Logic.run")(
      test("happy path threads state, logs, and reader") {
        val (logs, result) =
          Logic.run(AppState(Nil), Config(0.1)) {
            addItem("widget")
            addItem("gadget")
            lookupItem(0)
          }
        assertTrue(
          result == Right((AppState(List("widget", "gadget")), "widget")),
          logs.size == 2,
          logs.head.msg.contains("widget")
        )
      },
      test("error short-circuits and preserves logs up to the failure") {
        val (logs, result) =
          Logic.run(AppState(Nil), Config(0.1)) {
            addItem("widget")
            addItem("")
          }
        assertTrue(
          result == Left(AppError.Invalid),
          logs.size == 1
        )
      },
      test("runInfallible works without Abort") {
        val (logs, finalState, result) =
          Logic.runInfallible(AppState(Nil), Config(0.1)) {
            update(s => s.copy(items = s.items :+ "item"))
            write(Log("done"))
            getWith(_.items.size)
          }
        assertTrue(
          result == 1,
          finalState == AppState(List("item")),
          logs.size == 1
        )
      },
      test("simulateWith re-raises errors from inner run") {
        val result = Abort {
          Logic.simulateWith(AppState(Nil), Config(0.1)) {
            addItem("")
          }
        }
        assertTrue(result == Left(AppError.Invalid))
      },
      test("simulateWith does not leak state or logs to outer context") {
        val (logs, result) =
          Logic.run(AppState(List("outer")), Config(0.1)) {
            write(Log("outer-log"))
            Logic.simulateWith(AppState(Nil), Config(0.2)) {
              addItem("inner")
            }
            val outerState = get
            outerState
          }
        assertTrue(
          result == Right((AppState(List("outer")), AppState(List("outer")))),
          logs == Vector(Log("outer-log"))
        )
      }
    ),
    // ---------------------------------------------------------------------------
    // Composition
    // ---------------------------------------------------------------------------
    suite("Composition")(
      test("functions compose naturally through context propagation") {
        def step1(using State[Int], Writer[String]) = {
          update(_ + 1)
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
      },
      test("nested recover isolates inner failures") {
        val (logs, result) =
          Logic.run(0, ()) {
            write("outer")
            val inner = recover {
              write("inner")
              fail("inner-err")
            }(e => s"recovered: $e")
            write(inner)
            42
          }
        assertTrue(
          result == Right((0, 42)),
          logs == Vector("outer", "recovered: inner-err")
        )
      }
    )
  )
}
