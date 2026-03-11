package purelogic

import scala.annotation.unchecked.uncheckedVariance
import scala.caps.SharedCapability
import scala.collection.mutable.ArrayBuffer

/**
  * Accumulates values of type `W` during a computation.
  *
  * Useful for collecting events, audit logs, diagnostics, or any output that builds up as your logic runs.
  *
  * @tparam W
  *   the type of values to accumulate
  */
trait Writer[-W] extends SharedCapability {

  /**
    * Appends a single value to the log.
    */
  def write(w: W): Unit

  /**
    * Appends multiple values to the log at once.
    */
  def writeAll(elems: IterableOnce[W]): Unit

  /**
    * Clears all accumulated values.
    */
  def clear: Unit

  // Internal — used by recover
  private[purelogic] def snapshot: Int
  private[purelogic] def rollback(to: Int): Unit

  /**
    * Runs a block in a nested scope, returning both the captured writes and the result. The captured writes are also
    * forwarded to the outer writer.
    */
  def capture[A](body: Writer[W] ?=> A): (Vector[W @uncheckedVariance], A) = {
    val (logs, result) = Writer(body)
    writeAll(logs)
    (logs, result)
  }
}

object Writer {

  /**
    * Provides a `Writer[W]` and runs the body, returning a tuple of the accumulated values and the result.
    */
  def apply[W, A](body: Writer[W] ?=> A): (Vector[W], A) = {
    val buffer = ArrayBuffer[W]()
    val writer = new Writer[W] {
      def write(w: W): Unit                          = buffer.addOne(w)
      def writeAll(elems: IterableOnce[W]): Unit     = buffer.addAll(elems)
      def clear: Unit                                = buffer.clear()
      private[purelogic] def snapshot: Int           = buffer.length
      private[purelogic] def rollback(to: Int): Unit = buffer.dropRightInPlace(buffer.length - to)
    }
    val result = body(using writer)
    (buffer.toVector, result)
  }

  /**
    * Default `Writer[Nothing]` instance that discards all writes.
    */
  given Writer[Nothing] = new Writer[Nothing] {
    def write(w: Nothing): Unit                      = ()
    def writeAll(elems: IterableOnce[Nothing]): Unit = ()
    def clear: Unit                                  = ()
    private[purelogic] def snapshot: Int             = 0
    private[purelogic] def rollback(to: Int): Unit   = ()
  }

  /**
    * Appends a single value to the log.
    */
  inline def write[W](w: W)(using writer: Writer[W]): Unit = writer.write(w)

  /**
    * Appends multiple values to the log at once.
    */
  inline def writeAll[W](elems: IterableOnce[W])(using writer: Writer[W]): Unit = writer.writeAll(elems)

  /**
    * Clears all accumulated values.
    */
  inline def clear[W](using writer: Writer[W]): Unit = writer.clear

  /**
    * Runs a block in a nested scope, returning both the captured writes and the result. The captured writes are also
    * forwarded to the outer writer.
    */
  inline def capture[W, A](body: Writer[W] ?=> A)(using writer: Writer[W]): (Vector[W], A) = writer.capture(body)
}
