package purelogic

import scala.util.{Failure, Success}

import zio.test.*

import purelogic.syntax.*

object PureLogicSpec extends ZIOSpecDefault {

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

  def spec = suite("PureLogic")(
    // ---------------------------------------------------------------------------
    // Reader
    // ---------------------------------------------------------------------------
    suite("Reader")(
      test("read returns the environment") {
        val result = Reader(42)(read)
        assertTrue(result == 42)
      },
      test("read projects the environment") {
        val result = Reader("hello")(read(_.length))
        assertTrue(result == 5)
      },
      test("local provides a modified environment for the block") {
        val (inner, outer) = Reader(10) {
          val inner = local(_ + 5) {
            read
          }
          val outer = read
          (inner, outer)
        }
        assertTrue(inner == 15, outer == 10)
      },
      test("focus narrows the environment for the block") {
        val (inner, outer) = Reader(("hello", 42)) {
          val inner = focus(_._2) {
            read
          }
          val outer = read
          (inner, outer)
        }
        assertTrue(inner == 42, outer == ("hello", 42))
      }
    ),
    // ---------------------------------------------------------------------------
    // StateReader
    // ---------------------------------------------------------------------------
    suite("StateReader")(
      test("StateReader is covariant") {
        val (_, result) = State(List(1, 2, 3)) {
          val reader: StateReader[Iterable[Int]] = summon[State[List[Int]]]
          reader.get(_.size)
        }
        assertTrue(result == 3)
      },
      test("function requiring only StateReader works with State") {
        def readOnly(using StateReader[Int]): Int = get + 1
        val (finalState, result)                  = State(10)(readOnly)
        assertTrue(result == 11, finalState == 10)
      }
    ),
    // ---------------------------------------------------------------------------
    // StateWriter
    // ---------------------------------------------------------------------------
    suite("StateWriter")(
      test("StateWriter is contravariant") {
        def acceptsWriter(w: StateWriter[Int]): Unit = w.set(42)
        val (finalState, _)                          = State(0) {
          val state: State[Int] = summon[State[Int]]
          acceptsWriter(state) // State[Int] <: StateWriter[Int]
        }
        assertTrue(finalState == 42)
      },
      test("function requiring only StateWriter works with State") {
        def writeOnly(using StateWriter[Int]): Unit = set(99)
        val (finalState, _)                         = State(0)(writeOnly)
        assertTrue(finalState == 99)
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
      test("get projects the state") {
        val (_, result) = State(List(1, 2, 3))(get(_.size))
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
      },
      test("local provides a modified state and restores it after the block") {
        val (finalState, (inner, outer)) =
          State(10) {
            val inner = localState(_ + 5) {
              get
            }
            val outer = get
            (inner, outer)
          }
        assertTrue(inner == 15, outer == 10, finalState == 10)
      },
      test("focus updates a sub-state") {
        case class AppState(counter: Int, name: String)
        val (finalState, inner) =
          State(AppState(1, "initial")) {
            focusState(_.counter)((s, v) => s.copy(counter = v)) {
              update(_ + 5)
              get
            }
          }
        assertTrue(inner == 6, finalState == AppState(6, "initial"))
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
      },
      test("capture returns the logs from a block and keeps them") {
        val (logs, result) = Writer {
          val (inner, value) = capture {
            write("a")
            write("b")
            42
          }
          write("c")
          (inner, value)
        }
        assertTrue(
          logs == Vector("a", "b", "c"),
          result == (Vector("a", "b"), 42)
        )
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
    // orFail extensions
    // ---------------------------------------------------------------------------
    suite("orFail")(
      test("Some.orFail returns the value") {
        val result = Abort(Some(42).orFail("missing"))
        assertTrue(result == Right(42))
      },
      test("None.orFail fails with the error") {
        val result = Abort(None.orFail("missing"))
        assertTrue(result == Left("missing"))
      },
      test("Right.orFail returns the value") {
        val result = Abort(Right(42).orFail)
        assertTrue(result == Right(42))
      },
      test("Left.orFail fails with the error") {
        val result = Abort(Left("err").orFail)
        assertTrue(result == Left("err"))
      },
      test("Success.orFail returns the value") {
        val result = Abort(Success(42).orFail)
        assertTrue(result == Right(42))
      },
      test("Failure.orFail fails with the exception") {
        val ex     = new RuntimeException("boom")
        val result = Abort(Failure(ex).orFail)
        assertTrue(result == Left(ex))
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
      },
      test("recover can be used without State or Writer") {
        val result = Abort {
          recover {
            fail("boom")
          }(_ => "recovered")
        }
        assertTrue(result == Right("recovered"))
      },
      test("recoverSome handles matching errors and rolls back state and logs") {
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
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before")
        )
      },
      test("recoverSome re-fails when the error is not handled") {
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
        assertTrue(
          result == Left("unhandled"),
          logs == Vector("before")
        )
      },
      test("recoverSomeKeepLog handles matching errors and keeps logs") {
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
        assertTrue(
          result == Right((10, -1)),
          logs == Vector("before", "inside")
        )
      },
      test("recoverSomeKeepLog re-fails when the error is not handled and keeps logs") {
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
        assertTrue(
          result == Left("unhandled"),
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
            get(_.items.size)
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
    // Loop
    // ---------------------------------------------------------------------------
    suite("Loop")(
      test("done exits the loop with a value") {
        val result = Loop {
          done(42)
        }
        assertTrue(result == 42)
      },
      test("loop repeats until done is returned") {
        var i      = 0
        val result = Loop {
          i += 1
          if (i == 5) done(i)
          else continue
        }
        assertTrue(result == 5, i == 5)
      },
      test("continue proceeds to the next iteration") {
        var i      = 0
        var sum    = 0
        val result = Loop {
          i += 1
          if (i % 2 == 0) continue
          else {
            sum += i
            if (i >= 9) done(sum)
            else continue
          }
        }
        assertTrue(result == 1 + 3 + 5 + 7 + 9)
      },
      test("loop works with State") {
        val (finalState, result) = State(0) {
          Loop {
            update(_ + 1)
            if (get >= 3) done("finished")
            else continue
          }
        }
        assertTrue(result == "finished", finalState == 3)
      },
      test("loop works with Writer") {
        val (logs, result) = Writer {
          Loop {
            write("tick")
            done(99)
          }
        }
        assertTrue(result == 99, logs == Vector("tick"))
      },
      test("loop works with all capabilities") {
        val (logs, result) =
          Logic.run(0, 10) {
            Loop {
              val step = read
              update(_ + step)
              write(s"state=$get")
              if (get >= 30) done(s"done at $get")
              else continue
            }
          }
        assertTrue(
          result == Right((30, "done at 30")),
          logs == Vector("state=10", "state=20", "state=30")
        )
      },
      test("iterate models a tail-recursive function") {
        val (_, result) = Loop.iterate((10, 1)) { case (n, acc) =>
          if (n <= 1) done((n, acc))
          else continue((n - 1, n * acc))
        }
        assertTrue(result == 3628800)
      },
      test("iterate fibonacci") {
        val (_, a, _) = Loop.iterate((10, 0, 1)) { case (n, a, b) =>
          if (n == 0) done((n, a, b))
          else continue((n - 1, b, a + b))
        }
        assertTrue(a == 55)
      },
      test("iterate simple counter") {
        val result = Loop.iterate(0) { n =>
          if (n >= 10) done(n)
          else continue(n + 1)
        }
        assertTrue(result == 10)
      },
      test("iterate with Writer accumulates along the way") {
        val (logs, result) = Writer {
          Loop.iterate(1) { n =>
            write(s"step=$n")
            if (n >= 4) done(n)
            else continue(n + 1)
          }
        }
        assertTrue(
          result == 4,
          logs == Vector("step=1", "step=2", "step=3", "step=4")
        )
      },
      test("iterate with State") {
        val (finalState, result) = State(Vector.empty[Int]) {
          Loop.iterate(1) { n =>
            update[Vector[Int]](_ :+ n)
            if (n >= 5) done(n)
            else continue(n + 1)
          }
        }
        assertTrue(
          result == 5,
          finalState == Vector(1, 2, 3, 4, 5)
        )
      },
      test("foreach iterates over all elements") {
        val (logs, _) = Writer {
          Loop.foreach(List(1, 2, 3)) { n =>
            write(s"item=$n")
            continue
          }
        }
        assertTrue(logs == Vector("item=1", "item=2", "item=3"))
      },
      test("foreach done exits early") {
        var seen = 0
        Loop.foreach(1 to 100) { n =>
          seen = n
          if (n == 5) done(())
          else continue
        }
        assertTrue(seen == 5)
      },
      test("foreach continue skips elements") {
        val (logs, _) = Writer {
          Loop.foreach(1 to 6) { n =>
            if (n % 2 == 0) continue
            else { write(s"odd=$n"); continue }
          }
        }
        assertTrue(logs == Vector("odd=1", "odd=3", "odd=5"))
      },
      test("foreach with State") {
        val (finalState, _) = State(0) {
          Loop.foreach(List(10, 20, 30)) { n =>
            update[Int](_ + n)
            continue
          }
        }
        assertTrue(finalState == 60)
      },
      test("fold sums a list") {
        val result = Loop.fold(List(1, 2, 3, 4, 5))(0)((acc, n) => continue(acc + n))
        assertTrue(result == 15)
      },
      test("fold exits early with done") {
        val result = Loop.fold(List(3, 4, 5, 6, 7))(0) { (acc, n) =>
          val next = acc + n
          if (next > 10) done(acc)
          else continue(next)
        }
        assertTrue(result == 7)
      },
      test("fold continue skips elements") {
        val result = Loop.fold(1 to 10)(0) { (acc, n) =>
          if (n % 2 == 0) continue
          else continue(acc + n)
        }
        assertTrue(result == 25)
      },
      test("fold with Writer") {
        val (logs, result) = Writer {
          Loop.fold(List("a", "b", "c"))("") { (acc, s) =>
            write(s"processing $s")
            continue(if (acc.isEmpty) s else s"$acc,$s")
          }
        }
        assertTrue(
          result == "a,b,c",
          logs == Vector("processing a", "processing b", "processing c")
        )
      },
      test("fold on empty collection returns initial") {
        val result = Loop.fold(List.empty[Int])(42)((acc, _) => continue(acc + 1))
        assertTrue(result == 42)
      },
      test("nested loop (2 levels) inner done does not exit outer") {
        var outerCount = 0
        var innerCount = 0
        val result     = Loop {
          outerCount += 1
          Loop {
            innerCount += 1
            if (innerCount % 3 == 0) done(())
            else continue
          }
          if (outerCount >= 4) done(s"outer=$outerCount inner=$innerCount")
          else continue
        }
        assertTrue(
          result == "outer=4 inner=12",
          outerCount == 4,
          innerCount == 12
        )
      },
      test("nested loop (2 levels) continue only affects its own level") {
        var outerIters = 0
        var innerSkips = 0
        val result     = Loop {
          outerIters += 1
          if (outerIters > 3) done(Vector.empty[Int])
          else {
            var collected = Vector.empty[Int]
            var j         = 0
            Loop {
              j += 1
              if (j > 6) done(())
              else if (j % 2 == 0) { innerSkips += 1; continue }
              else { collected = collected :+ j; continue }
            }
            if (outerIters == 3) done(collected)
            else continue
          }
        }
        assertTrue(
          result == Vector(1, 3, 5),
          outerIters == 3,
          innerSkips == 9
        )
      },
      test("nested loop (2 levels) with shared State and Writer") {
        val (logs, result) =
          Logic.run(0, ()) {
            var i = 0
            Loop {
              i += 1
              write(s"outer=$i")
              var j = 0
              Loop {
                j += 1
                if (j > 3) done(())
                else { update[Int](_ + 1); continue }
              }
              write(s"state=${get[Int]}")
              if (i >= 2) done(get[Int])
              else continue
            }
          }
        assertTrue(
          result == Right((6, 6)),
          logs == Vector("outer=1", "state=3", "outer=2", "state=6")
        )
      },
      test("3-level nested loop accumulates correctly with Writer and State") {
        val (logs, result) =
          Logic.run(0, ()) {
            var i = 0
            Loop {
              i += 1
              if (i > 3) done(get[Int])
              else {
                var j = 0
                Loop {
                  j += 1
                  if (j > i) done(())
                  else {
                    var k = 0
                    Loop {
                      k += 1
                      if (k > 2) done(())
                      else { update[Int](_ + 1); write(s"$i.$j.$k"); continue }
                    }
                    continue
                  }
                }
                continue
              }
            }
          }
        assertTrue(
          logs == Vector(
            "1.1.1",
            "1.1.2",
            "2.1.1",
            "2.1.2",
            "2.2.1",
            "2.2.2",
            "3.1.1",
            "3.1.2",
            "3.2.1",
            "3.2.2",
            "3.3.1",
            "3.3.2"
          ),
          logs.size == 12,
          result == Right((12, 12))
        )
      },
      test("nested loop with Abort short-circuits entire computation") {
        val (logs, result) =
          Logic.run(0, ()) {
            var i = 0
            Loop {
              i += 1
              update[Int](_ + 1)
              val s = get[Int]
              write(s"outer=$s")
              var j = 0
              Loop {
                j += 1
                update[Int](_ + 1)
                if (get[Int] > 100) fail("too high")
                if (j >= 2) done(())
                else continue
              }
              if (i >= 3) done("finished")
              else continue
            }
          }
        assertTrue(
          result == Right((9, "finished")),
          logs == Vector("outer=1", "outer=4", "outer=7")
        )
      },
      test("nested loop with recover rolls back inner failure") {
        val (logs, result) =
          Logic.run(0, ()) {
            var i = 0
            Loop {
              i += 1
              update[Int](_ + 1)
              val s = get[Int]
              write(s"iter=$s")
              recover {
                Loop {
                  update[Int](_ + 10)
                  write(s"inner=${get[Int]}")
                  fail("boom")
                  continue
                }
              }(_ => write(s"recovered at state=${get[Int]}"))
              if (i >= 2) done("ok")
              else continue
            }
          }
        assertTrue(
          result == Right((2, "ok")),
          logs == Vector("iter=1", "recovered at state=1", "iter=2", "recovered at state=2")
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
