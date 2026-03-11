package purelogic

import scala.collection.mutable.ArrayBuffer

trait Writer[-W] {
  def write(w: W): Unit
  def writeAll(elems: IterableOnce[W]): Unit
  def clear: Unit

  // Internal — used by recover
  private[purelogic] def snapshot: Int
  private[purelogic] def rollback(to: Int): Unit
}

object Writer {
  def apply[W, A](body: Writer[W] ?=> A): (Vector[W], A) = {
    val buffer      = ArrayBuffer[W]()
    given Writer[W] = new Writer[W] {
      def write(w: W): Unit                          = buffer.addOne(w)
      def writeAll(elems: IterableOnce[W]): Unit     = buffer.addAll(elems)
      def clear: Unit                                = buffer.clear()
      private[purelogic] def snapshot: Int           = buffer.length
      private[purelogic] def rollback(to: Int): Unit = buffer.dropRightInPlace(buffer.length - to)
    }
    val result      = body
    (buffer.toVector, result)
  }

  def write[W](using writer: Writer[W])(w: W): Unit                      = writer.write(w)
  def writeAll[W](using writer: Writer[W])(elems: IterableOnce[W]): Unit = writer.writeAll(elems)
  def clear[W](using writer: Writer[W]): Unit                            = writer.clear
}
