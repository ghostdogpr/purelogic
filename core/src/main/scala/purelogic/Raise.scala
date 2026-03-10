package purelogic

import scala.util.boundary
import scala.util.boundary.break

trait Raise[-E] {
  def raise(e: E): Nothing
  def ensure(condition: Boolean, error: => E): Unit    = if (!condition) raise(error)
  def ensureWith[A](option: Option[A], error: => E): A = option.getOrElse(raise(error))
}

object Raise {
  def apply[E, A](body: Raise[E] ?=> A): Either[E, A] = {
    val a = boundary[Either[E, A]] {
      given Raise[E] = new Raise[E] {
        def raise(e: E): Nothing = break(Left(e))
      }
      Right(body)
    }
    a
  }

  /**
    * Catch errors and handle them, eliminating the error type.
    */
  def catchError[E, A](f: Raise[E] ?=> A)(handler: E => A): A =
    boundary[A] {
      given Raise[E] = new Raise[E] {
        def raise(e: E): Nothing = break(handler(e))
      }
      f
    }

  /**
    * Recover with optional state/log rollback.
    */
  def recover[E, S, W, A](using s: State[S], w: Writer[W])(resetLog: Boolean = true, resetState: Boolean = true)(
    f: Raise[E] ?=> A
  )(handler: E => A): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    boundary[A] {
      given Raise[E] = new Raise[E] {
        def raise(e: E): Nothing = {
          if (resetState) s.set(stateSnapshot)
          if (resetLog) w.rollback(logSnapshot)
          break(handler(e))
        }
      }
      f
    }
  }
}
