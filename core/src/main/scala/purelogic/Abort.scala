package purelogic

import scala.util.{boundary, Try}
import scala.util.boundary.break

trait Abort[-E] {
  def fail(e: E): Nothing
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

  def fail[E](using abort: Abort[E])(e: E): Nothing =
    abort.fail(e)

  def ensure[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit =
    if (!condition) abort.fail(error)

  def ensureNot[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit =
    if (condition) abort.fail(error)

  def extractOption[E, A](using abort: Abort[E])(option: Option[A], error: => E): A =
    option.getOrElse(abort.fail(error))

  def extractEither[E, A](using abort: Abort[E])(either: Either[E, A]): A =
    either.fold(abort.fail, identity)

  def extractTry[A](using abort: Abort[Throwable])(t: Try[A]): A =
    t.fold(abort.fail, identity)

  def attempt[A](using abort: Abort[Throwable])(f: => A): A =
    try f
    catch { case e: Throwable => abort.fail(e) }
}
