package purelogic

import scala.util.boundary.{break, Label}

sealed trait Raise[-E] {
  def raise(e: E): Nothing
}

object Raise {
  def raise[E](using r: Raise[E])(e: E): Nothing = r.raise(e)

  def ensure[E](using Raise[E])(condition: Boolean, error: => E): Unit =
    if (!condition) Raise.raise(error)

  def ensureWith[E, A](using Raise[E])(option: Option[A], error: => E): A =
    option.getOrElse(Raise.raise(error))

  private[purelogic] def make[E](handler: E => Nothing): Raise[E] =
    new Raise[E] {
      def raise(e: E): Nothing = handler(e)
    }

  private[purelogic] def makeRaise[E](using label: Label[Left[E, Nothing]]): Raise[E] =
    make(e => break(Left(e)))
}
