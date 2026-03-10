package purelogic

import scala.collection.mutable.ArrayBuffer

trait Writer[-W] {
  def tell(w: W): Unit
  def clear: Unit

  // Internal — used by recover
  private[purelogic] def snapshot: Int
  private[purelogic] def rollback(to: Int): Unit
}

object Writer {
  def apply[W, A](body: Writer[W] ?=> A): (Vector[W], A) = {
    val buffer      = ArrayBuffer[W]()
    given Writer[W] = new Writer[W] {
      def tell(w: W): Unit                           = buffer += w
      def clear: Unit                                = buffer.clear()
      private[purelogic] def snapshot: Int           = buffer.length
      private[purelogic] def rollback(to: Int): Unit = buffer.dropRightInPlace(buffer.length - to)
    }
    val a           = body
    (buffer.toVector, a)
  }
}
