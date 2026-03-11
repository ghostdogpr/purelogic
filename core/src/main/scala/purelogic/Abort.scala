package purelogic

import scala.util.{boundary, Try}
import scala.util.boundary.break

trait Abort[-E] {
  def fail(e: E): Nothing

  def ensure(condition: Boolean, error: => E): Unit =
    if (!condition) fail(error)

  def ensureNot(condition: Boolean, error: => E): Unit =
    if (condition) fail(error)

  def extractOption[A](option: Option[A], error: => E): A =
    option.getOrElse(fail(error))

  def extractEither[A](either: Either[E, A]): A =
    either.fold(fail, identity)
}

object Abort {
  def apply[E, A](body: Abort[E] ?=> A): Either[E, A] =
    boundary[Either[E, A]] {
      given Abort[E] = new Abort[E] {
        def fail(e: E): Nothing = break(Left(e))
      }
      Right(body)
    }

  given Abort[Nothing] = new Abort[Nothing] {
    def fail(e: Nothing): Nothing = e
  }

  def recover[W, S, E, A](using w: Writer[W], s: State[S])(resetLog: Boolean = true)(f: Abort[E] ?=> A)(handler: E => A): A = {
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

  def fail[E](using abort: Abort[E])(e: E): Nothing                                 = abort.fail(e)
  def ensure[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit       = abort.ensure(condition, error)
  def ensureNot[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit    = abort.ensureNot(condition, error)
  def extractOption[E, A](using abort: Abort[E])(option: Option[A], error: => E): A = abort.extractOption(option, error)
  def extractEither[E, A](using abort: Abort[E])(either: Either[E, A]): A           = abort.extractEither(either)

  def extractTry[A](t: Try[A])(using abort: Abort[Throwable]): A =
    t.fold(abort.fail, identity)

  def attempt[A](f: => A)(using abort: Abort[Throwable]): A =
    try f
    catch { case e: Throwable => abort.fail(e) }
}
