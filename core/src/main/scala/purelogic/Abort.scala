package purelogic

import scala.util.boundary
import scala.util.boundary.break

trait Abort[-E] {
  def fail(e: E): Nothing

  def ensure(condition: Boolean, error: => E): Unit    = if (!condition) fail(error)
  def ensureWith[A](option: Option[A], error: => E): A = option.getOrElse(fail(error))
}

object Abort {
  def apply[E, A](body: Abort[E] ?=> A): Either[E, A] = {
    val a = boundary[Either[E, A]] {
      given Abort[E] = new Abort[E] {
        def fail(e: E): Nothing = break(Left(e))
      }
      Right(body)
    }
    a
  }

  /**
    * Recover with optional state/log rollback.
    */
  def recover[E, S, W, A](using s: State[S], w: Writer[W])(resetLog: Boolean = true)(
    f: Abort[E] ?=> A
  )(handler: E => A): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    boundary[A] {
      given Abort[E] = new Abort[E] {
        def fail(e: E): Nothing = {
          s.set(stateSnapshot)
          if (resetLog) w.rollback(logSnapshot)
          break(handler(e))
        }
      }
      f
    }
  }
}
