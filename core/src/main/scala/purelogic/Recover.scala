package purelogic

import scala.util.boundary
import scala.util.boundary.break

object Recover {

  /**
    * Catch errors and handle them, eliminating the error type.
    */
  def catchError[E, A](f: Raise[E] ?=> A)(handler: E => A): A =
    boundary[A] {
      given Raise[E] = Raise.make(e => break(handler(e)))
      f
    }

  /**
    * Catch errors into Either.
    */
  def catchEither[E, A](f: Raise[E] ?=> A): Either[E, A] =
    boundary[Either[E, A]] {
      given Raise[E] = Raise.make(e => break(Left(e)))
      Right(f)
    }

  /**
    * Recover with optional state/log rollback.
    */
  def recover[E, S, W, A](resetLog: Boolean = true, resetState: Boolean = true)(
    f: Raise[E] ?=> A
  )(handler: E => A)(using s: State[S], w: Writer[W]): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    boundary[A] {
      given Raise[E] = Raise.make { e =>
        if (resetState) s.set(stateSnapshot)
        if (resetLog) w.rollback(logSnapshot)
        break(handler(e))
      }
      f
    }
  }
}
