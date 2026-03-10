package purelogic

import scala.util.boundary
import scala.util.boundary.break

trait Raise[-E] {
  def raise(e: E): Nothing
}

object Raise {
  def raise[E](using r: Raise[E])(e: E): Nothing = r.raise(e)

  def ensure[E](using Raise[E])(condition: Boolean, error: => E): Unit =
    if (!condition) Raise.raise(error)

  def ensureWith[E, A](using Raise[E])(option: Option[A], error: => E): A =
    option.getOrElse(Raise.raise(error))

  def apply[E, A](body: Raise[E] ?=> A): Either[E, A] = {
    val a = boundary[Either[E, A]] {
      given Raise[E] = new Raise[E] {
        def raise(e: E): Nothing = break(Left(e))
      }
      Right(body)
    }
    a
  }
}
