package purelogic

import scala.util.boundary
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

  inline def fail[E](using abort: Abort[E])(e: E): Nothing                                 = abort.fail(e)
  inline def ensure[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit       = abort.ensure(condition, error)
  inline def ensureNot[E](using abort: Abort[E])(condition: Boolean, error: => E): Unit    = abort.ensureNot(condition, error)
  inline def extractOption[E, A](using abort: Abort[E])(option: Option[A], error: => E): A = abort.extractOption(option, error)
  inline def extractEither[E, A](using abort: Abort[E])(either: Either[E, A]): A           = abort.extractEither(either)

  def extractTry[A](t: scala.util.Try[A])(using abort: Abort[Throwable]): A =
    t.fold(abort.fail, identity)

  def attempt[A](f: => A)(using abort: Abort[Throwable]): A =
    try f
    catch { case e: Throwable => abort.fail(e) }

  def recover[W, S, E, A](using Writer[W], State[S])(f: Abort[E] ?=> A)(handler: E => A): A                                     =
    doRecover(resetLog = true)(f)(handler)
  def recoverKeepLog[W, S, E, A](using Writer[W], State[S])(f: Abort[E] ?=> A)(handler: E => A): A                              =
    doRecover(resetLog = false)(f)(handler)
  def recoverSome[W, S, E, A](using Writer[W], State[S], Abort[E])(f: Abort[E] ?=> A)(handler: PartialFunction[E, A]): A        =
    doRecoverSome(resetLog = true)(f)(handler)
  def recoverSomeKeepLog[W, S, E, A](using Writer[W], State[S], Abort[E])(f: Abort[E] ?=> A)(handler: PartialFunction[E, A]): A =
    doRecoverSome(resetLog = false)(f)(handler)

  private def doRecover[W, S, E, A](using w: Writer[W], s: State[S])(resetLog: Boolean)(f: Abort[E] ?=> A)(handler: E => A): A = {
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

  private def doRecoverSome[W, S, E, A](using w: Writer[W], s: State[S], abort: Abort[E])(resetLog: Boolean)(f: Abort[E] ?=> A)(
    handler: PartialFunction[E, A]
  ): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    boundary[A] {
      given Abort[E] = new Abort[E] {
        def fail(e: E): Nothing = {
          s.set(stateSnapshot)
          if (resetLog) w.rollback(logSnapshot)
          handler.lift(e) match {
            case Some(value) => break(value)
            case None        => abort.fail(e)
          }
        }
      }
      f
    }
  }
}
