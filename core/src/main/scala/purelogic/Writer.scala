package purelogic

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.mutable.ArrayBuffer

/**
  * Accumulates values of type `W` during a computation.
  *
  * Useful for collecting events, audit logs, diagnostics, or any output that builds up as your logic runs.
  *
  * @tparam W
  *   the type of values to accumulate
  */
trait Writer[-W] extends scala.caps.ExclusiveCapability {

  /**
    * Appends a single value to the log.
    */
  def write(w: W): Unit

  /**
    * Appends multiple values to the log at once.
    */
  def writeAll(elems: IterableOnce[W]^): Unit

  /**
    * Clears all accumulated values.
    */
  def clear: Unit

  /**
    * An opaque handle to the log's contents at a point in time, produced by [[snapshot]] and consumed by [[rollback]].
    */
  type Snapshot

  /**
    * Captures the current contents of the log so they can be restored later with [[rollback]]. This is the primitive
    * that `recover` builds on; use it to implement custom recovery that resets writes to an earlier checkpoint.
    */
  def snapshot: Snapshot

  /**
    * Restores the log to the contents captured by an earlier [[snapshot]], discarding any writes made since. Values
    * accumulated before the snapshot are preserved.
    */
  def rollback(to: Snapshot): Unit

  /**
    * Runs a block in a nested scope, returning both the captured writes and the result. The captured writes are also
    * forwarded to the outer writer. If the body aborts or throws, the captured writes are discarded; only writes
    * produced by a nested `recover` (or similar handler) inside the body that completes normally reach the outer
    * writer.
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
      def write(w: W): Unit                       = buffer.addOne(w)
      def writeAll(elems: IterableOnce[W]^): Unit = buffer.addAll(elems)
      def clear: Unit                             = buffer.clear()
      type Snapshot = Vector[W]
      def snapshot: Vector[W]           = buffer.toVector
      def rollback(to: Vector[W]): Unit = {
        buffer.clear()
        buffer.addAll(to)
      }
    }
    val result = body(using writer)
    (buffer.toVector, result)
  }

  /**
    * Default `Writer[Nothing]` instance that discards all writes.
    */
  given [W <: Nothing]: Writer[W] = new Writer[W] {
    def write(w: W): Unit                       = ()
    def writeAll(elems: IterableOnce[W]^): Unit = ()
    def clear: Unit                             = ()
    type Snapshot = Unit
    def snapshot: Unit           = ()
    def rollback(to: Unit): Unit = ()
  }

  /**
    * Appends a single value to the log.
    */
  inline def write[W](w: W)(using writer: Writer[W]): Unit = writer.write(w)

  /**
    * Appends multiple values to the log at once.
    */
  inline def writeAll[W](elems: IterableOnce[W]^)(using writer: Writer[W]): Unit = writer.writeAll(elems)

  /**
    * Clears all accumulated values.
    */
  inline def clear[W](using writer: Writer[W]): Unit = writer.clear

  /**
    * Runs a block in a nested scope, returning both the captured writes and the result. The captured writes are also
    * forwarded to the outer writer.
    */
  inline def capture[W, A](body: Writer[W] ?=> A)(using writer: Writer[W]): (Vector[W], A) = writer.capture(body)

  /**
    * Captures the current contents of the log so they can be restored later with [[rollback]].
    */
  inline def snapshot[W](using writer: Writer[W]): writer.Snapshot = writer.snapshot

  /**
    * Restores the log to the contents captured by an earlier [[snapshot]], discarding any writes made since.
    */
  inline def rollback[W](using writer: Writer[W])(to: writer.Snapshot): Unit = writer.rollback(to)
}
