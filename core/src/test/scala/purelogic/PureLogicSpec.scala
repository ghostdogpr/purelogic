package purelogic

import scala.util.{Failure, Success}

import purelogic.EventSourcing.*
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

  test("Writer: capture forwards the writes of a failing block, which recoverKeepLog keeps and recover rolls back") {
    val kept = Logic.run[Unit, String, Int, String, String](0, ()) {
      recoverKeepLog {
        write("before")
        val _ = capture {
          write("inside")
          fail("boom")
        }
        "completed"
      }(e => s"recovered $e")
    }
    assertEquals(kept, (Vector("before", "inside"), Right((0, "recovered boom"))))

    val rolledBack = Logic.run[Unit, String, Int, String, String](0, ()) {
      write("outer")
      recover {
        val _ = capture {
          write("inside")
          fail("boom")
        }
        "completed"
      }(e => s"recovered $e")
    }
    assertEquals(rolledBack, (Vector("outer"), Right((0, "recovered boom"))))

    val thrown = Writer[String, String] {
      try {
        val _ = capture {
          write("inside")
          throw new RuntimeException("boom")
        }
        "completed"
      } catch {
        case e: RuntimeException => e.getMessage
      }
    }
    assertEquals(thrown, (Vector("inside"), "boom"))
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

  test("Abort: attempt does not hijack a break targeting a different outer Abort") {
    val result: Either[String, Either[Throwable, Int]] =
      Abort[String, Either[Throwable, Int]] {
        Abort[Throwable, Int] {
          attempt {
            fail[String]("outer error")
          }
        }
      }
    assertEquals(result, Left("outer error"))
  }

  test("Abort: attempt does not catch InterruptedException") {
    val ex                = new InterruptedException("interrupt")
    var caught: Throwable = null
    try Abort[Throwable, Int](attempt(throw ex))
    catch { case t: Throwable => caught = t }
    assert(caught eq ex)
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

  test("Recovery: recover restores pre-snapshot writes even after clear inside the block") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("before")
        recover {
          clear
          write("inside")
          fail("oops")
        }(_ => -1)
      }
    assertEquals(result, Right((0, -1)))
    assertEquals(logs, Vector("before"))
  }

  test("Recovery: recover works without State or Writer in scope") {
    val result = Abort {
      recover {
        fail("boom")
      }(_ => "recovered")
    }
    assertEquals(result, Right("recovered"))
  }

  test("Recovery: recover in a helper with a State and a StateWriter of another type rolls back the State") {
    val other: StateWriter[String]                          = new StateWriter[String] {
      def set(s: String): Unit = ()
    }
    def helper(using State[Int], StateWriter[String]): Unit =
      recover {
        set(9)
        set("other")
        fail("oops")
      }(_ => ())
    val (_, result)                                         =
      Logic.run(0, ()) {
        set(1)
        helper(using summon[State[Int]], other)
      }
    assertEquals(result, Right((1, ())))
  }

  test("Recovery: recover does not compile with two States") {
    val errors = compileErrors("""
      def helper(using State[Int], State[String]): Unit =
        recover {
          fail("oops")
        }(_ => ())
    """)
    assert(errors.contains("match type purelogic.State"), errors)
  }

  test("Recovery: recover with nested Writers of different types restores the State and the innermost Writer") {
    val (outer, (inner, (state, _))) =
      Writer[String, (Vector[Int], (Int, Unit))] {
        Writer[Int, (Int, Unit)] {
          State(0) {
            write("outer before")
            write(1)
            recover {
              write("outer inside")
              write(2)
              set(5)
              fail("oops")
            }(_ => ())
          }
        }
      }
    assertEquals(outer, Vector("outer before", "outer inside"))
    assertEquals(inner, Vector(1))
    assertEquals(state, 0)
  }

  test("Recovery: recover does not compile with two Writers of different types at the same level") {
    val errors = compileErrors("""
      def helper(using Writer[String], Writer[Int], State[Int]): Unit =
        recover {
          fail("oops")
        }(_ => ())
    """)
    assert(errors.contains("match type purelogic.Writer"), errors)
  }

  test("Recovery: recover does not compile with only a StateWriter") {
    val errors = compileErrors("""
      def helper(using StateWriter[Int]): Unit =
        recover {
          fail("oops")
        }(_ => ())
    """)
    assert(errors.contains("a StateWriter alone cannot be saved"), errors)
  }

  test("Recovery: recover does not compile with a Writer and only a StateWriter") {
    val errors = compileErrors("""
      def helper(using Writer[String], StateWriter[Int]): Unit =
        recover {
          fail("oops")
        }(_ => ())
    """)
    assert(errors.contains("a StateWriter alone cannot be saved"), errors)
  }

  test("Recovery: recover's state restore wins over a nested State.local finally") {
    val (_, result) =
      Logic.run(0, ()) {
        recover {
          set(10)
          localState((_: Int) => 20) {
            set(99)
            fail[String]("oops")
          }
        }(_ => ())
        get
      }
    assertEquals(result, Right((0, 0)))
  }

  test("Recovery: recoverSome handler observes the restored state") {
    val (logs, result) =
      Logic.run(0, ()) {
        set(10)
        write("before")
        recoverSome {
          set(99)
          write("inside")
          fail("handled")
        } { case "handled" => get }
      }
    assertEquals(result, Right((10, 10)))
    assertEquals(logs, Vector("before"))
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

  test("Recovery: recoverSome re-fails without rollback when the error is not handled") {
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
    assertEquals(logs, Vector("before", "inside"))
  }

  test("Recovery: recoverSome guards are evaluated once, on the restored state") {
    val (logs, result) =
      Logic.run(0, ()) {
        recoverSome[String, Int] {
          try fail("boom")
          finally set(1)
        } {
          case "boom" if get[Int] == 0 => 42
        }
      }
    assertEquals(result, Right((0, 42)))
    assertEquals(logs, Vector.empty)
  }

  test("Recovery: recoverSome guard that does not match leaves state and writes as they were at the failure") {
    val (logs, result) =
      Logic.run(0, ()) {
        val inner = Abort[String, Int] {
          recoverSome[String, Int] {
            set(5)
            write("inside")
            fail("boom")
          } {
            case "boom" if get[Int] == 99 => 42
          }
        }
        (inner, get)
      }
    assertEquals(result, Right((5, (Left("boom"), 5))))
    assertEquals(logs, Vector("inside"))
  }

  test("Recovery: recoverSomeKeepLog discards state and writes made by a guard that does not match") {
    val (logs, result) =
      Logic.run(0, ()) {
        val inner = Abort[String, Int] {
          recoverSomeKeepLog[String, Int] {
            set(5)
            write("body")
            fail("boom")
          } {
            case "boom" if { set(99); write("guard"); false } => 42
          }
        }
        (inner, get)
      }
    assertEquals(result, Right((5, (Left("boom"), 5))))
    assertEquals(logs, Vector("body"))
  }

  test("Capture checking: lambdas passed to update, modify and read may use other capabilities") {
    val (logs, result) =
      Logic.run(1, 10) {
        update(_ + read)
        val doubled = modify[Int, Int](s => (s * 2, s))
        write(read(_ + doubled))
        get
      }
    assertEquals(result, Right((11, 11)))
    assertEquals(logs, Vector(32))
  }

  test("Capture checking: writeAll accepts an iterator that captures a capability") {
    val (logs, result) =
      Logic.run((), 5) {
        writeAll(Iterator.tabulate(2)(i => i + read))
        writeAll(List(0, 1).iterator.map(_ * read))
      }
    assertEquals(result, Right(((), ())))
    assertEquals(logs, Vector(5, 6, 0, 5))
  }

  test("Capture checking: lambdas passed to updateAndGet, getAndUpdate, get, local and focus may use other capabilities") {
    val (_, result) =
      Logic.run(1, 10) {
        val updated  = updateAndGet(_ + read)
        val previous = getAndUpdate(_ + read)
        val sum      = get(_ + read)
        val shifted  = local(_ + get)(read)
        val scaled   = focus(_ * get)(read)
        (updated, previous, sum, shifted, scaled)
      }
    assertEquals(result, Right((21, (11, 11, 31, 31, 210))))
  }

  test("Capture checking: replayEvents accepts a collection that captures a capability") {
    case class Added(amount: Int)

    given Transition[Added, Int, Nothing] with {
      def run(ev: Added) = update(_ + ev.amount)
    }

    val (events, finalState, _) = Logic.runEventSourcingInfallible(0, 5) {
      replayEvents(List(1, 2).view.map(amount => Added(amount + read)))
    }
    assertEquals(finalState, 13)
    assertEquals(events, Vector())
  }

  test("Writer: rollback restores the log to an earlier snapshot, keeping pre-snapshot writes") {
    val (logs, _) =
      Logic.run((), ()) {
        write("before")
        val snap = snapshot
        write("inside")
        rollback(snap)
      }
    assertEquals(logs, Vector("before"))
  }

  test("Writer: snapshot/rollback express discard-on-success, keep-on-escape recovery") {
    def attempt(err: Option[String])(using Writer[String], State[Unit], Abort[String]): Boolean = {
      val snap   = snapshot
      val result = recoverSome {
        write("inside")
        err.foreach(fail)
        true
      } { case "handled" => false }
      rollback(snap) // only reached when the block succeeds or the error is handled
      result
    }

    assertEquals(Logic.run((), ())(attempt(None)), (Vector.empty[String], Right(((), true))))
    assertEquals(Logic.run((), ())(attempt(Some("handled"))), (Vector.empty[String], Right(((), false))))
    assertEquals(Logic.run((), ())(attempt(Some("committed"))), (Vector("inside"), Left("committed")))
  }

  test("Recovery: recoverSome unhandled error reaches outer scope with writes intact") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("before")
        recoverKeepLog {
          recoverSome {
            write("inside")
            fail[String]("unhandled")
          } { case "handled" => -1 }
        } { err => write(s"caught: $err"); -1 }
      }
    assertEquals(result, Right((0, -1)))
    assertEquals(logs, Vector("before", "inside", "caught: unhandled"))
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

  test("Recovery: nested recover blocks each roll back to their own entry point") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("before")
        recover {
          write("outer")
          recover {
            write("inner")
            fail("inner-err")
          }(e => write(s"handled $e"))
          write("outer again")
        }(_ => ())
        recover {
          write("dropped outer")
          recover(write("dropped inner"))(_ => ())
          fail("outer-err")
        }(e => write(s"handled $e"))
      }
    assertEquals(result, Right((0, ())))
    assertEquals(logs, Vector("before", "outer", "handled inner-err", "outer again", "handled outer-err"))
  }

  test("Recovery: a failed outer block undoes a clear made by an inner block that succeeded") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("a")
        recover {
          write("b")
          recover(clear)(_ => ())
          write("c")
          fail("outer-err")
        }(_ => ())
        write("d")
      }
    assertEquals(result, Right((0, ())))
    assertEquals(logs, Vector("a", "d"))
  }

  test("Recovery: clear after a recover block also removes the writes made before it") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("a")
        recover(write("b"))(_ => ())
        clear
        write("c")
      }
    assertEquals(result, Right((0, ())))
    assertEquals(logs, Vector("c"))
  }

  test("Recovery: recover inside an iterator passed to writeAll keeps the writes in evaluation order") {
    val (logs, result) =
      Logic.run(0, ()) {
        write("a")
        writeAll(Iterator("x", "y").map { s =>
          recover(write(s"before $s"))(_ => ())
          recover {
            write(s"dropped $s")
            fail("boom")
          }(_ => ())
          s
        })
        write("b")
      }
    assertEquals(result, Right((0, ())))
    assertEquals(logs, Vector("a", "before x", "x", "before y", "y", "b"))
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

  // ---------------------------------------------------------------------------
  // EventSourcing
  // ---------------------------------------------------------------------------

  test("EventSourcing: writeEvent applies transitions and runEventSourcing returns events, final state, and result") {
    case class Counter(value: Int)
    enum CounterEvent { case Incremented; case Decremented }
    import CounterEvent.*

    given Transition[CounterEvent, Counter, Nothing] with {
      def run(ev: CounterEvent) = ev match {
        case Incremented => update(c => c.copy(value = c.value + 1))
        case Decremented => update(c => c.copy(value = c.value - 1))
      }
    }

    val result = Logic.runEventSourcing(Counter(0), ()) {
      writeEvent(Incremented)
      writeEvent(Incremented)
      writeEvent(Decremented)
      get
    }
    assertEquals(result, Right((Vector(Incremented, Incremented, Decremented), Counter(1), Counter(1))))
  }

  test("EventSourcing: writeEvent aborts when transition fails") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      writeEvent(Withdrawn(30))
      writeEvent(Withdrawn(30))
    }
    assertEquals(result, Left("insufficient funds"))
  }

  test("EventSourcing: replayEvents rebuilds state without emitting events") {
    case class Counter(value: Int)
    enum CounterEvent { case Incremented; case Decremented }
    import CounterEvent.*

    given Transition[CounterEvent, Counter, Nothing] with {
      def run(ev: CounterEvent) = ev match {
        case Incremented => update(c => c.copy(value = c.value + 1))
        case Decremented => update(c => c.copy(value = c.value - 1))
      }
    }

    val history = Vector(Incremented, Incremented, Decremented)

    val (events, finalState, _) = Logic.runEventSourcingInfallible(Counter(0), ()) {
      replayEvents(history)
      get
    }
    assertEquals(finalState, Counter(1))
    assertEquals(events, Vector())
  }

  test("EventSourcing: replayEvents followed by writeEvent only records new events") {
    case class Counter(value: Int)
    enum CounterEvent { case Incremented; case Decremented }
    import CounterEvent.*

    given Transition[CounterEvent, Counter, Nothing] with {
      def run(ev: CounterEvent) = ev match {
        case Incremented => update(c => c.copy(value = c.value + 1))
        case Decremented => update(c => c.copy(value = c.value - 1))
      }
    }

    val history = Vector(Incremented, Incremented)

    val (events, finalState, _) = Logic.runEventSourcingInfallible(Counter(0), ()) {
      replayEvents(history)
      writeEvent(Decremented)
      get
    }
    assertEquals(finalState, Counter(1))
    assertEquals(events, Vector(Decremented))
  }

  test("EventSourcing: replayEvents aborts on invalid event") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val badHistory = Vector(Withdrawn(30), Withdrawn(30))

    val result = Logic.runEventSourcing(Account(50), ()) {
      replayEvents(badHistory)
    }
    assertEquals(result, Left("insufficient funds"))
  }

  test("EventSourcing: individual transitions per subtype via Ev1 <: Ev bound") {
    sealed trait AppEvent
    object AppEvent {
      case class Created(name: String) extends AppEvent
      case class Deleted(name: String) extends AppEvent
    }
    case class Items(names: List[String])
    import AppEvent.*

    given Transition[Created, Items, Nothing] with {
      def run(ev: Created) = update(s => Items(s.names :+ ev.name))
    }

    given Transition[Deleted, Items, Nothing] with {
      def run(ev: Deleted) = update(s => Items(s.names.filterNot(_ == ev.name)))
    }

    val (events, finalState, _) = Logic.runEventSourcingInfallible(Items(Nil), ()) {
      writeEvent(Created("a"))
      writeEvent(Created("b"))
      writeEvent(Deleted("a"))
      get
    }
    assertEquals(finalState, Items(List("b")))
    assertEquals(events, Vector(Created("a"), Created("b"), Deleted("a")))
  }

  test("EventSourcing: recover rolls back state and events") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      recover {
        writeEvent(Withdrawn(30))
        writeEvent(Withdrawn(30))
      }(_ => ())
    }
    assertEquals(result, Right((Vector(), Account(50), ())))
  }

  test("EventSourcing: recoverKeepLog keeps the events and the state they produced") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      recoverKeepLog {
        writeEvent(Withdrawn(30))
        writeEvent(Withdrawn(30))
      }(_ => ())
    }
    assertEquals(result, Right((Vector(Withdrawn(30)), Account(20), ())))
  }

  test("EventSourcing: recoverSome re-raises unmatched errors with state and events as they were at the failure") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      val inner = Abort[String, Unit] {
        recoverSome {
          writeEvent(Withdrawn(30))
          writeEvent(Withdrawn(30))
        } { case "other" => () }
      }
      (inner, get)
    }
    assertEquals(result, Right((Vector(Withdrawn(30)), Account(20), (Left("insufficient funds"), Account(20)))))
  }

  test("EventSourcing: recover rolls back a transition that updates the state and then fails") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          update(a => a.copy(balance = a.balance - amount))
          ensure(get[Account].balance >= 0, "insufficient funds")
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      writeEvent(Withdrawn(20))
      recover(writeEvent(Withdrawn(80)))(_ => ())
    }
    assertEquals(result, Right((Vector(Withdrawn(20)), Account(30), ())))
  }

  test("EventSourcing: recoverSome runs the handler on the restored state and rolls back the events") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      recoverSome {
        writeEvent(Withdrawn(30))
        writeEvent(Withdrawn(30))
        0
      } { case "insufficient funds" if get[Account].balance == 50 => get[Account].balance }
    }
    assertEquals(result, Right((Vector(), Account(50), 50)))
  }

  test("EventSourcing: the KeepLog variants keep the state of the recorded events when a transition updates and then fails") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          update(a => a.copy(balance = a.balance - amount))
          ensure(get[Account].balance >= 0, "insufficient funds")
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      recoverKeepLog {
        writeEvent(Withdrawn(20))
        writeEvent(Withdrawn(80))
      }(_ => ())
      recoverSomeKeepLog {
        writeEvent(Withdrawn(10))
        writeEvent(Withdrawn(80))
        0
      } { case "insufficient funds" => get[Account].balance }
    }
    assertEquals(result, Right((Vector(Withdrawn(20), Withdrawn(10)), Account(20), 20)))
  }

  test("EventSourcing: recoverKeepLog after EventSourcing.apply inside Logic.run keeps the state of the kept events") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val (events, result) = Logic.run(Account(50), ()) {
      EventSourcing[AccountEvent, Account, String, Unit] {
        recoverKeepLog {
          writeEvent(Withdrawn(30))
          writeEvent(Withdrawn(30))
        }(_ => ())
      }
    }
    assertEquals(events, Vector(Withdrawn(30)))
    assertEquals(result, Right((Account(20), ())))
  }

  test("Recovery: recover inside capture rolls back the writes made to the captured writer") {
    val (logs, result) = Logic.run[Unit, String, Int, String, Vector[String]](0, ()) {
      val (inner, _) = capture {
        write("before")
        recover {
          write("inside")
          fail("boom")
        }(_ => ())
      }
      inner
    }
    assertEquals(result, Right((0, Vector("before"))))
    assertEquals(logs, Vector("before"))
  }

  test("Recovery: recover inside a nested Writer rolls back the nested writer") {
    val (_, result) = Logic.run[Unit, String, Int, String, Vector[String]](0, ()) {
      val (inner, _) = Writer[String, Unit] {
        recover {
          write("inside")
          fail("boom")
        }(_ => ())
      }
      inner
    }
    assertEquals(result, Right((0, Vector())))
  }

  test("Recovery: recover inside a nested State rolls back the nested state") {
    val (_, result) = Logic.run[Unit, String, Int, String, (Long, Unit)](0, ()) {
      State(1L) {
        recover {
          set(9L)
          fail("boom")
        }(_ => ())
      }
    }
    assertEquals(result, Right((0, (1L, ()))))
  }

  test("EventSourcing: recover inside a Logic program run from an event-sourced program rolls back its state and writes") {
    val result = Logic.runEventSourcing[Unit, Int, Int, String, (Vector[String], Either[String, (Long, Long)])](5, ()) {
      Logic.run(0L, ()) {
        recover {
          set(9L)
          write("inside")
          fail("boom")
        }(_ => get[Long])
      }
    }
    assertEquals(result, Right((Vector(), 5, (Vector(), Right((0L, 0L))))))
  }

  test("EventSourcing: recover inside Logic.simulate called from an event-sourced program rolls back its state and writes") {
    case class Account(balance: Int)

    val result = Logic.runEventSourcing[Unit, Int, Account, String, (Account, Boolean)](Account(50), ()) {
      Logic.simulate[String, Account, Unit, String, (Account, Boolean)] {
        val before = snapshot
        recover {
          set(Account(0))
          write("inside")
          fail("boom")
        }(_ => ())
        (get, snapshot == before)
      }
    }
    assertEquals(result, Right((Vector(), Account(50), (Account(50), true))))
  }

  test("EventSourcing: recover rolls back a Writer in scope next to the EventSourcing") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val (audit, result) = Writer[String, Either[String, (Vector[AccountEvent], Account, Unit)]] {
      Logic.runEventSourcing(Account(50), ()) {
        recover {
          write("audit: trying")
          writeEvent(Withdrawn(30))
          writeEvent(Withdrawn(30))
        }(_ => ())
      }
    }
    assertEquals(audit, Vector())
    assertEquals(result, Right((Vector(), Account(50), ())))
  }

  test("EventSourcing: the KeepLog variants keep the state of the kept events when a focused State aliases it") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val (events, result) = Logic.run[Unit, AccountEvent, Account, String, Int](Account(50), ()) {
      EventSourcing[AccountEvent, Account, String, Int] {
        focusState[Account, Int, Int](_.balance)((a, b) => a.copy(balance = b)) {
          recoverKeepLog {
            writeEvent(Withdrawn(10))
            writeEvent(Withdrawn(80))
          }(_ => ())
          recoverSomeKeepLog {
            writeEvent(Withdrawn(10))
            writeEvent(Withdrawn(80))
            0
          } { case "insufficient funds" => get[Int] }
        }
      }
    }
    assertEquals(events, Vector(Withdrawn(10), Withdrawn(10)))
    assertEquals(result, Right((Account(30), 30)))
  }

  test("EventSourcing: recoverKeepLog restores a separate State in scope and keeps the writes, events and their state") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          ensure(get[Account].balance >= amount, "insufficient funds")
          update(a => a.copy(balance = a.balance - amount))
      }
    }

    val (logs, result) = Logic.run[Unit, String, Int, String, Either[String, (Vector[AccountEvent], Account, Unit)]](0, ()) {
      Logic.runEventSourcing(Account(50), ()) {
        recoverKeepLog {
          set(7)
          write("inside")
          writeEvent(Withdrawn(30))
          writeEvent(Withdrawn(30))
        }(_ => ())
      }
    }
    assertEquals(logs, Vector("inside"))
    assertEquals(result, Right((0, Right((Vector(Withdrawn(30)), Account(20), ())))))
  }

  test("EventSourcing: writeEvent restores the state when the transition throws") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          update(a => a.copy(balance = a.balance - amount))
          if (get[Account].balance < 0) throw new IllegalStateException("negative balance")
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      writeEvent(Withdrawn(20))
      try writeEvent(Withdrawn(80))
      catch { case _: IllegalStateException => () }
    }
    assertEquals(result, Right((Vector(Withdrawn(20)), Account(30), ())))
  }

  test("EventSourcing: replayEvents restores the state when a transition fails") {
    case class Account(balance: Int)
    enum AccountEvent { case Withdrawn(amount: Int) }
    import AccountEvent.*

    given Transition[AccountEvent, Account, String] with {
      def run(ev: AccountEvent) = ev match {
        case Withdrawn(amount) =>
          update(a => a.copy(balance = a.balance - amount))
          ensure(get[Account].balance >= 0, "insufficient funds")
      }
    }

    val result = Logic.runEventSourcing(Account(50), ()) {
      recoverKeepLog(replayEvents(Vector(Withdrawn(20), Withdrawn(80))))(_ => ())
    }
    assertEquals(result, Right((Vector(), Account(30), ())))
  }

  test("EventSourcing: clear does not compile in an event-sourced program") {
    val errors = compileErrors("""
      Logic.runEventSourcing[Unit, String, Int, String, Unit](0, ()) {
        clear
      }
    """)
    assert(errors.contains("No given instance of type purelogic.Writer"), errors)
  }

  test("EventSourcing: capture does not compile in an event-sourced program") {
    val errors = compileErrors("""
      Logic.runEventSourcing[Unit, String, Int, String, Unit](0, ()) {
        capture(())
      }
    """)
    assert(errors.contains("No given instance of type purelogic.Writer"), errors)
  }

  test("EventSourcing: snapshot and rollback do not compile in an event-sourced program") {
    val snapshotErrors = compileErrors("""
      Logic.runEventSourcing[Unit, String, Int, String, Unit](0, ()) {
        snapshot[Nothing]
      }
    """)
    assert(snapshotErrors.contains("parameter writer of method snapshot"), snapshotErrors)
    val rollbackErrors = compileErrors("""
      Logic.runEventSourcing[Unit, String, Int, String, Unit](0, ()) {
        rollback[Nothing](???)
      }
    """)
    assert(rollbackErrors.contains("parameter writer of method rollback"), rollbackErrors)
  }

  test("Reader: read does not compile without a Reader") {
    val errors = compileErrors("""
      Abort[String, Unit] {
        read[Unit]
      }
    """)
    assert(errors.contains("No given instance of type purelogic.Reader"), errors)
  }
}
