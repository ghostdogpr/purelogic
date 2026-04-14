package purelogic

import scala.util.boundary
import scala.util.boundary.break

/**
  * Short-circuits a computation with an error of type `E`.
  *
  * Unlike throwing exceptions, the error type is tracked in the function signature and must be handled explicitly.
  *
  * @tparam E
  *   the type of error
  */
trait Abort[-E] extends scala.caps.ExclusiveCapability {

  /**
    * Aborts the computation with the given error.
    */
  def fail(e: E): Nothing

  /**
    * Fails if the condition is not met.
    */
  def ensure(condition: Boolean, error: => E): Unit =
    if (!condition) fail(error)

  /**
    * Fails if the condition is met.
    */
  def ensureNot(condition: Boolean, error: => E): Unit =
    if (condition) fail(error)

  /**
    * Extracts the value from an `Option`, or fails with the given error.
    */
  def extractOption[A](option: Option[A], error: => E): A =
    option.getOrElse(fail(error))

  /**
    * Extracts the `Right` value from an `Either`, or fails with the `Left`.
    */
  def extractEither[A](either: Either[E, A]): A =
    either.fold(fail, identity)
}

object Abort {

  /**
    * Provides an `Abort[E]` and runs the body, returning `Right(result)` on success or `Left(error)` on failure.
    */
  def apply[E, A](body: Abort[E] ?=> A): Either[E, A] =
    boundary[Either[E, A]] {
      val abort = new Abort[E] {
        def fail(e: E): Nothing = break(Left(e))
      }
      Right(body(using abort))
    }

  /**
    * Default `Abort[Nothing]` instance that can never fail.
    */
  given [E <: Nothing]: Abort[E] = new Abort[E] {
    def fail(e: E): Nothing = e
  }

  /**
    * Aborts the computation with the given error.
    */
  inline def fail[E](e: E)(using abort: Abort[E]): Nothing = abort.fail(e)

  /**
    * Fails if the condition is not met.
    */
  inline def ensure[E](condition: Boolean, error: => E)(using abort: Abort[E]): Unit = abort.ensure(condition, error)

  /**
    * Fails if the condition is met.
    */
  inline def ensureNot[E](condition: Boolean, error: => E)(using abort: Abort[E]): Unit = abort.ensureNot(condition, error)

  /**
    * Extracts the value from an `Option`, or fails with the given error.
    */
  inline def extractOption[E, A](option: Option[A], error: => E)(using abort: Abort[E]): A = abort.extractOption(option, error)

  /**
    * Extracts the `Right` value from an `Either`, or fails with the `Left`.
    */
  inline def extractEither[E, A](either: Either[E, A])(using abort: Abort[E]): A = abort.extractEither(either)

  /**
    * Extracts the value from a `Try`, or fails with the `Throwable`. Requires `Abort[Throwable]`.
    */
  def extractTry[A](t: scala.util.Try[A])(using abort: Abort[Throwable]): A =
    t.fold(abort.fail, identity)

  /**
    * Runs a block and catches any `Throwable`, converting it to an `Abort` failure. Requires `Abort[Throwable]`.
    */
  def attempt[A](f: => A)(using abort: Abort[Throwable]): A =
    try f
    catch { case e: Throwable => abort.fail(e) }

  /**
    * Catches all errors and handles them with a function. Rolls back state and writes to the point before the failed
    * block.
    */
  def recover[W, S, E, A](f: Abort[E] ?=> A)(handler: E => A)(using Writer[W], State[S]): A =
    doRecover(resetLog = true)(f)(handler)

  /**
    * Like `recover`, but keeps the writes from the failed block instead of rolling them back.
    */
  def recoverKeepLog[W, S, E, A](f: Abort[E] ?=> A)(handler: E => A)(using Writer[W], State[S]): A =
    doRecover(resetLog = false)(f)(handler)

  /**
    * Catches only errors matched by a partial function. Unmatched errors are re-raised. Rolls back state and writes to
    * the point before the failed block.
    */
  def recoverSome[W, S, E, A](f: Abort[E] ?=> A)(handler: PartialFunction[E, A])(using Writer[W], State[S], Abort[E]): A =
    doRecoverSome(resetLog = true)(f)(handler)

  /**
    * Like `recoverSome`, but keeps the writes from the failed block instead of rolling them back.
    */
  def recoverSomeKeepLog[W, S, E, A](f: Abort[E] ?=> A)(handler: PartialFunction[E, A])(using Writer[W], State[S], Abort[E]): A =
    doRecoverSome(resetLog = false)(f)(handler)

  private def doRecover[W, S, E, A](resetLog: Boolean)(f: Abort[E] ?=> A)(handler: E => A)(using w: Writer[W], s: State[S]): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    val result = boundary[Either[E, A]] {
      val abort = new Abort[E] {
        def fail(e: E): Nothing = {
          s.set(stateSnapshot)
          if (resetLog) w.rollback(logSnapshot)
          break(Left(e))
        }
      }
      Right(f(using abort))
    }

    result match {
      case Right(value) => value
      case Left(e)      => handler(e)
    }
  }

  private def doRecoverSome[W, S, E, A](
    resetLog: Boolean
  )(f: Abort[E] ?=> A)(handler: PartialFunction[E, A])(using w: Writer[W], s: State[S], abort: Abort[E]): A = {
    val stateSnapshot = s.get
    val logSnapshot   = w.snapshot

    val result = boundary[Either[E, A]] {
      val a = new Abort[E] {
        def fail(e: E): Nothing = {
          s.set(stateSnapshot)
          if (resetLog) w.rollback(logSnapshot)
          break(Left(e))
        }
      }
      Right(f(using a))
    }

    result match {
      case Right(value) => value
      case Left(e)      =>
        handler.lift(e) match {
          case Some(value) => value
          case None        => abort.fail(e)
        }
    }
  }
}
