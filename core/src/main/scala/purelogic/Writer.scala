package purelogic

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.mutable.ArrayBuffer

trait Writer[-W] {
  def write(w: W): Unit
  def writeAll(elems: IterableOnce[W]): Unit
  def clear: Unit
  // Internal — used by recover
  private[purelogic] def snapshot: Int
  private[purelogic] def rollback(to: Int): Unit

  def capture[A](body: Writer[W] ?=> A): (Vector[W @uncheckedVariance], A) = {
    val (logs, result) = Writer(body)
    writeAll(logs)
    (logs, result)
  }
}

object Writer {
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

  given Writer[Nothing] = new Writer[Nothing] {
    def write(w: Nothing): Unit                      = ()
    def writeAll(elems: IterableOnce[Nothing]): Unit = ()
    def clear: Unit                                  = ()
    private[purelogic] def snapshot: Int             = 0
    private[purelogic] def rollback(to: Int): Unit   = ()
  }

  inline def write[W](w: W)(using writer: Writer[W]): Unit                                 = writer.write(w)
  inline def writeAll[W](elems: IterableOnce[W])(using writer: Writer[W]): Unit            = writer.writeAll(elems)
  inline def clear[W](using writer: Writer[W]): Unit                                       = writer.clear
  inline def capture[W, A](body: Writer[W] ?=> A)(using writer: Writer[W]): (Vector[W], A) = writer.capture(body)
}
