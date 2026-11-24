package purelogictest

import purelogic.*

// The other tests are in package `purelogic`, where package-private givens are visible. These run `recover` from
// outside it, where the build's -Werror turns a use of an inaccessible given into a compile error.
class RecoverOutsidePackageSpec extends munit.FunSuite {
  inline def fail[E](e: E)(using abort: Abort[E]): Nothing = abort.fail(e)

  test("recover resolves outside the purelogic package with any Writer and State in scope") {
    def withWriterAndState(using Writer[String], State[Int]): Unit =
      recover {
        write("both")
        set(1)
        fail("oops")
      }(_ => ())
    def withWriter(using Writer[String]): Unit                     =
      recover {
        write("writer")
        fail("oops")
      }(_ => ())
    def withState(using State[Int]): Unit                          =
      recover {
        set(2)
        fail("oops")
      }(_ => ())
    def withNeither: Unit                                          =
      recover {
        fail("oops")
      }(_ => ())
    val (logs, result)                                             =
      Logic.run(0, ()) {
        write("before")
        withWriterAndState
        withWriter
        withState
        withNeither
      }
    assertEquals(result, Right((0, ())))
    assertEquals(logs, Vector("before"))
  }

  test("recover resolves outside the purelogic package in an event-sourced program") {
    given Transition[Int, Int, String] with {
      def run(ev: Int) = update[Int](_ + ev)
    }
    def withEvents(using EventSourcing[Int, Int, String]): Unit =
      recover {
        writeEvent(1)
        fail("oops")
      }(_ => ())
    def withEventsAndWriter(using EventSourcing[Int, Int, String], Writer[String]): Unit =
      recover {
        writeEvent(2)
        write("inside")
        fail("oops")
      }(_ => ())
    val result                                                                           =
      Logic.runEventSourcing[Unit, Int, Int, String, Vector[String]](0, ()) {
        withEvents
        Writer[String, Unit] {
          withEventsAndWriter
        }._1
      }
    assertEquals(result, Right((Vector.empty, 0, Vector.empty)))
  }
}
