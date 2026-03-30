package purelogic

import scala.util.{Failure, Success}

import purelogic.syntax.*

class PureLogicSpec extends munit.FunSuite {
  inline def fail[E](e: E)(using abort: Abort[E]): Nothing = abort.fail(e)

  case class Config(discount: Double)
  case class AppState(items: List[String])
  case class Log(msg: String)
  enum AppError { case NotFound(key: String); case Invalid }

  def addItem(item: String)(using Reader[Config], Writer[Log], State[AppState], Abort[AppError]) = {
    ensure(item.nonEmpty, AppError.Invalid)
    val discount = read(_.discount)
    update(s => s.copy(items = s.items :+ item))
    write(Log(s"Added $item with discount $discount"))
  }

  def lookupItem(idx: Int)(using State[AppState], Abort[AppError]) = {
    val items = get(_.items)
    extractOption(items.lift(idx), AppError.NotFound(s"index $idx"))
  }

  // ---------------------------------------------------------------------------
  // Reader
  // ---------------------------------------------------------------------------

  test("Reader: read returns the environment") {
    val result = Reader(42)(read)
    assertEquals(result, 42)
  }

  test("Reader: read projects the environment") {
    val result = Reader("hello")(read(_.length))
    assertEquals(result, 5)
  }

  test("Reader: local provides a modified environment for the block") {
    val (inner, outer) = Reader(10) {
      val inner = local(_ + 5) {
        read
      }
      val outer = read
      (inner, outer)
    }
    assertEquals(inner, 15)
    assertEquals(outer, 10)
  }

  test("Reader: focus narrows the environment for the block") {
    val (inner, outer) = Reader(("hello", 42)) {
      val inner = focus(_._2) {
        read
      }
      val outer = read
      (inner, outer)
    }
    assertEquals(inner, 42)
    assertEquals(outer, ("hello", 42))
  }

  // ---------------------------------------------------------------------------
  // StateReader
  // ---------------------------------------------------------------------------

  test("StateReader: StateReader is covariant") {
    val (_, result) = State(List(1, 2, 3)) {
      val reader: StateReader[Iterable[Int]] = summon[State[List[Int]]]
      reader.get(_.size)
    }
    assertEquals(result, 3)
  }

  test("StateReader: function requiring only StateReader works with State") {
    def readOnly(using StateReader[Int]): Int = get + 1
    val (finalState, result)                  = State(10)(readOnly)
    assertEquals(result, 11)
    assertEquals(finalState, 10)
  }

  // ---------------------------------------------------------------------------
  // StateWriter
  // ---------------------------------------------------------------------------

  test("StateWriter: StateWriter is contravariant") {
    def acceptsWriter(w: StateWriter[Int]): Unit = w.set(42)
    val (finalState, _)                          = State(0) {
      val state: State[Int] = summon[State[Int]]
      acceptsWriter(state)
    }
    assertEquals(finalState, 42)
  }

  test("StateWriter: function requiring only StateWriter works with State") {
    def writeOnly(using StateWriter[Int]): Unit = set(99)
    val (finalState, _)                         = State(0)(writeOnly)
    assertEquals(finalState, 99)
  }

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  test("State: get returns the current state") {
    val (_, result) = State(10)(get)
    assertEquals(result, 10)
  }

  test("State: get projects the state") {
    val (_, result) = State(List(1, 2, 3))(get(_.size))
    assertEquals(result, 3)
  }

  test("State: set replaces the state") {
    val (finalState, _) = State(0)(set(42))
    assertEquals(finalState, 42)
  }

  test("State: update transforms the state") {
    val (finalState, _) = State(10)(update(_ + 5))
    assertEquals(finalState, 15)
  }

  test("State: modify transforms state and returns a value") {
    val (finalState, result) = State(10)(modify(s => (s * 2, s + 1)))
    assertEquals(result, 20)
    assertEquals(finalState, 11)
  }

  test("State: updateAndGet transforms and returns new state") {
    val (_, result) = State(10)(updateAndGet(_ + 5))
    assertEquals(result, 15)
  }

  test("State: getAndSet returns old state and replaces") {
    val (finalState, old) = State(10)(getAndSet(42))
    assertEquals(old, 10)
    assertEquals(finalState, 42)
  }

  test("State: getAndUpdate returns old state and transforms") {
    val (finalState, old) = State(10)(getAndUpdate(_ + 5))
    assertEquals(old, 10)
    assertEquals(finalState, 15)
  }

  test("State: local provides a modified state and restores it after the block") {
    val (finalState, (inner, outer)) =
      State(10) {
        val inner = localState(_ + 5) {
          get
        }
        val outer = get
        (inner, outer)
      }
    assertEquals(inner, 15)
    assertEquals(outer, 10)
    assertEquals(finalState, 10)
  }

  test("State: focus updates a sub-state") {
    case class AppState(counter: Int, name: String)
    val (finalState, inner) =
      State(AppState(1, "initial")) {
        focusState(_.counter)((s, v) => s.copy(counter = v)) {
          update(_ + 5)
          get
        }
      }
    assertEquals(inner, 6)
    assertEquals(finalState, AppState(6, "initial"))
  }

  // ---------------------------------------------------------------------------
  // Writer
  // ---------------------------------------------------------------------------

  test("Writer: write appends a single entry") {
    val (logs, _) = Writer(write("hello"))
    assertEquals(logs, Vector("hello"))
  }

  test("Writer: writeAll appends multiple entries") {
    val (logs, _) = Writer(writeAll(List("a", "b", "c")))
    assertEquals(logs, Vector("a", "b", "c"))
  }

  test("Writer: clear removes all entries") {
    val (logs, _) = Writer {
      write("before")
      clear
      write("after")
    }
    assertEquals(logs, Vector("after"))
  }

  test("Writer: capture returns the logs from a block and keeps them") {
    val (logs, result) = Writer {
      val (inner, value) = capture {
        write("a")
        write("b")
        42
      }
      write("c")
      (inner, value)
    }
    assertEquals(logs, Vector("a", "b", "c"))
    assertEquals(result, (Vector("a", "b"), 42))
  }

  // ---------------------------------------------------------------------------
  // Abort
  // ---------------------------------------------------------------------------

  test("Abort: fail short-circuits with an error") {
    val result: Either[String, Nothing] = Abort(fail("boom"))
    assertEquals(result, Left("boom"))
  }

  test("Abort: ensure passes when condition is true") {
    val result = Abort(ensure(true, "fail"))
    assertEquals(result, Right(()))
  }

  test("Abort: ensure fails when condition is false") {
    val result = Abort(ensure(false, "fail"))
    assertEquals(result, Left("fail"))
  }

  test("Abort: ensureNot passes when condition is false") {
    val result = Abort(ensureNot(false, "fail"))
    assertEquals(result, Right(()))
  }

  test("Abort: ensureNot fails when condition is true") {
    val result = Abort(ensureNot(true, "fail"))
    assertEquals(result, Left("fail"))
  }

  test("Abort: extractOption extracts Some") {
    val result = Abort(extractOption(Some(42), "missing"))
    assertEquals(result, Right(42))
  }

  test("Abort: extractOption fails on None") {
    val result = Abort(extractOption(None, "missing"))
    assertEquals(result, Left("missing"))
  }

  test("Abort: extractEither extracts Right") {
    val result = Abort(extractEither(Right(42)))
    assertEquals(result, Right(42))
  }

  test("Abort: extractEither fails on Left") {
    val result = Abort(extractEither(Left("err")))
    assertEquals(result, Left("err"))
  }

  test("Abort: extractTry extracts Success") {
    val result = Abort(extractTry(Success(42)))
    assertEquals(result, Right(42))
  }

  test("Abort: extractTry fails on Failure") {
    val ex     = new RuntimeException("boom")
    val result = Abort(extractTry(Failure(ex)))
    assertEquals(result, Left(ex))
  }

  test("Abort: attempt catches exceptions") {
    val result = Abort(attempt(throw new RuntimeException("boom")))
    assert(result.isLeft)
  }

  test("Abort: attempt passes through normal values") {
    val result = Abort(attempt(42))
    assertEquals(result, Right(42))
  }

  // ---------------------------------------------------------------------------
  // orFail extensions
  // ---------------------------------------------------------------------------

  test("orFail: Some.orFail returns the value") {
    val result = Abort(Some(42).orFail("missing"))
    assertEquals(result, Right(42))
  }

  test("orFail: None.orFail fails with the error") {
    val result = Abort(None.orFail("missing"))
    assertEquals(result, Left("missing"))
  }

  test("orFail: Right.orFail returns the value") {
    val result = Abort(Right(42).orFail)
    assertEquals(result, Right(42))
  }

  test("orFail: Left.orFail fails with the error") {
    val result = Abort(Left("err").orFail)
    assertEquals(result, Left("err"))
  }

  test("orFail: Success.orFail returns the value") {
    val result = Abort(Success(42).orFail)
    assertEquals(result, Right(42))
  }

  test("orFail: Failure.orFail fails with the exception") {
    val ex     = new RuntimeException("boom")
    val result = Abort(Failure(ex).orFail)
    assertEquals(result, Left(ex))
  }

  // ---------------------------------------------------------------------------
  // Recovery
  // ---------------------------------------------------------------------------

  test("Recovery: recover rolls back state and logs on error") {
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
    assertEquals(result, Right((10, -1)))
    assertEquals(logs, Vector("before"))
  }

  test("Recovery: recover keeps state and logs when no error") {
    val (logs, result) =
      Logic.run(0, ()) {
        recover {
          set(42)
          write("kept")
          100
        }(_ => -1)
      }
    assertEquals(result, Right((42, 100)))
    assertEquals(logs, Vector("kept"))
  }

  test("Recovery: recoverKeepLog rolls back state but keeps logs") {
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
    assertEquals(result, Right((10, -1)))
    assertEquals(logs, Vector("before", "inside"))
  }

  test("Recovery: recover can be used without State or Writer") {
    val result = Abort {
      recover {
        fail("boom")
      }(_ => "recovered")
    }
    assertEquals(result, Right("recovered"))
  }

  test("Recovery: recoverSome handles matching errors and rolls back state and logs") {
    val (logs, result) =
      Logic.run(0, ()) {
        set(10)
        write("before")
        recoverSome {
          set(99)
          write("inside")
          fail("handled")
        } { case "handled" => -1 }
      }
    assertEquals(result, Right((10, -1)))
    assertEquals(logs, Vector("before"))
  }

  test("Recovery: recoverSome re-fails when the error is not handled") {
    val (logs, result) =
      Logic.run(0, ()) {
        set(10)
        write("before")
        recoverSome {
          set(99)
          write("inside")
          fail("unhandled")
        } { case "handled" => -1 }
      }
    assertEquals(result, Left("unhandled"))
    assertEquals(logs, Vector("before"))
  }

  test("Recovery: recoverSomeKeepLog handles matching errors and keeps logs") {
    val (logs, result) =
      Logic.run(0, ()) {
        set(10)
        write("before")
        recoverSomeKeepLog {
          set(99)
          write("inside")
          fail("handled")
        } { case "handled" => -1 }
      }
    assertEquals(result, Right((10, -1)))
    assertEquals(logs, Vector("before", "inside"))
  }

  test("Recovery: recoverSomeKeepLog re-fails when the error is not handled and keeps logs") {
    val (logs, result) =
      Logic.run(0, ()) {
        set(10)
        write("before")
        recoverSomeKeepLog {
          set(99)
          write("inside")
          fail("unhandled")
        } { case "handled" => -1 }
      }
    assertEquals(result, Left("unhandled"))
    assertEquals(logs, Vector("before", "inside"))
  }

  // ---------------------------------------------------------------------------
  // Logic.run
  // ---------------------------------------------------------------------------

  test("Logic.run: happy path threads state, logs, and reader") {
    val (logs, result) =
      Logic.run(AppState(Nil), Config(0.1)) {
        addItem("widget")
        addItem("gadget")
        lookupItem(0)
      }
    assertEquals(result, Right((AppState(List("widget", "gadget")), "widget")))
    assertEquals(logs.size, 2)
    assert(logs.head.msg.contains("widget"))
  }

  test("Logic.run: error short-circuits and preserves logs up to the failure") {
    val (logs, result) =
      Logic.run(AppState(Nil), Config(0.1)) {
        addItem("widget")
        addItem("")
      }
    assertEquals(result, Left(AppError.Invalid))
    assertEquals(logs.size, 1)
  }

  test("Logic.run: runInfallible works without Abort") {
    val (logs, finalState, result) =
      Logic.runInfallible(AppState(Nil), Config(0.1)) {
        update(s => s.copy(items = s.items :+ "item"))
        write(Log("done"))
        get(_.items.size)
      }
    assertEquals(result, 1)
    assertEquals(finalState, AppState(List("item")))
    assertEquals(logs.size, 1)
  }

  test("Logic.run: simulateWith re-raises errors from inner run") {
    val result = Abort {
      Logic.simulateWith(AppState(Nil), Config(0.1)) {
        addItem("")
      }
    }
    assertEquals(result, Left(AppError.Invalid))
  }

  test("Logic.run: simulateWith does not leak state or logs to outer context") {
    val (logs, result) =
      Logic.run(AppState(List("outer")), Config(0.1)) {
        write(Log("outer-log"))
        Logic.simulateWith(AppState(Nil), Config(0.2)) {
          addItem("inner")
        }
        val outerState = get
        outerState
      }
    assertEquals(result, Right((AppState(List("outer")), AppState(List("outer")))))
    assertEquals(logs, Vector(Log("outer-log")))
  }

  test("Logic.run: simulateWith(mockState) reuses the outer Reader") {
    val (_, result) =
      Logic.run(AppState(Nil), Config(0.5)) {
        val inner = Logic.simulateWith(AppState(List("mock"))) {
          addItem("simulated")
          get
        }
        inner
      }
    assertEquals(result, Right((AppState(Nil), AppState(List("mock", "simulated")))))
  }

  test("Logic.run: simulate works with only StateReader in scope") {
    def readOnlySimulate(using Reader[Config], StateReader[AppState], Abort[AppError]): Int =
      Logic.simulate {
        addItem("simulated")
        get(_.items.size)
      }

    val (logs, result) =
      Logic.run(AppState(List("original")), Config(0.1)) {
        val count      = readOnlySimulate
        val outerState = get
        (count, outerState)
      }
    assertEquals(result, Right((AppState(List("original")), (2, AppState(List("original"))))))
    assert(logs.isEmpty)
  }

  // ---------------------------------------------------------------------------
  // Composition
  // ---------------------------------------------------------------------------

  test("Composition: functions compose naturally through context propagation") {
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
    assertEquals(result, Right((1, 10)))
    assertEquals(logs, Vector("step1", "step2"))
  }

  test("Composition: nested recover isolates inner failures") {
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
    assertEquals(result, Right((0, 42)))
    assertEquals(logs, Vector("outer", "recovered: inner-err"))
  }
}
