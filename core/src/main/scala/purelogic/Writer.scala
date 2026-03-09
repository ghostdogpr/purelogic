package purelogic

import scala.collection.mutable.ArrayBuffer

sealed trait Writer[W] {
  def tell(w: W): Unit
  def clear: Unit

  // Internal — used by recover
  private[purelogic] def snapshot: Int
  private[purelogic] def rollback(to: Int): Unit
}

object Writer {
  def tell[W](using wr: Writer[W])(w: W): Unit = wr.tell(w)
  def clear[W](using wr: Writer[W]): Unit      = wr.clear

  private[purelogic] def makeWriter[W]: (Writer[W], () => Vector[W]) = {
    val buffer = ArrayBuffer[W]()
    val writer = new Writer[W] {
      def tell(w: W): Unit                           = buffer += w
      def clear: Unit                                = buffer.clear()
      private[purelogic] def snapshot: Int           = buffer.length
      private[purelogic] def rollback(to: Int): Unit = buffer.dropRightInPlace(buffer.length - to)
    }
    (writer, () => buffer.toVector)
  }
}
