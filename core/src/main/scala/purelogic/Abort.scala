package purelogic

import scala.util.boundary
import scala.util.boundary.break
import scala.util.control.NonFatal

/**
  * Short-circuits a computation with an error of type `E`.
  *
  * Unlike throwing exceptions, the error type is tracked in the function signature and must be handled explicitly.
  *
  * @tparam E
  *   the type of error
  */
trait Abort[-E] extends scala.caps.Control {

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
    * Runs a block and catches non-fatal throwables, converting them to an `Abort` failure. Fatal throwables
    * (`VirtualMachineError`, `InterruptedException`, `ControlThrowable`, etc.) and `boundary.Break` propagate
    * unchanged. Requires `Abort[Throwable]`.
    */
  def attempt[A](f: => A)(using abort: Abort[Throwable]): A =
    try f
    catch {
      case b: boundary.Break[?] => throw b
      case NonFatal(e)          => abort.fail(e)
    }

  /**
    * Catches all errors and handles them with a function. Rolls back state and writes to the point before the failed
    * block. In an event-sourced program, rolls back the state and the events.
    */
  def recover[E, A](f: Abort[E] ?=> A)(handler: E => A)(using Rollback): A =
    doRecover(keepLog = false)(f)(handler)

  /**
    * Like `recover`, but keeps the writes from the failed block instead of rolling them back. In an event-sourced
    * program, keeps the events from the failed block and the state they produced.
    */
  def recoverKeepLog[E, A](f: Abort[E] ?=> A)(handler: E => A)(using Rollback): A =
    doRecover(keepLog = true)(f)(handler)

  /**
    * Catches only errors matched by a partial function. When an error is matched, rolls back state and writes to the
    * point before the failed block. The handler, including any pattern guards, is evaluated once, on the restored state
    * and writes. Unmatched errors are re-raised without rollback, leaving state and writes intact for the outer scope to
    * observe or handle. In an event-sourced program, the events are rolled back together with the state.
    */
  def recoverSome[E, A](f: Abort[E] ?=> A)(handler: PartialFunction[E, A]^)(using Rollback, Abort[E]): A =
    doRecoverSome(keepLog = false)(f)(handler)

  /**
    * Like `recoverSome`, but keeps the writes from the matched block instead of rolling them back. Unmatched errors are
    * re-raised without rollback (same as `recoverSome`). In an event-sourced program, keeps the events from the matched
    * block and the state they produced.
    */
  def recoverSomeKeepLog[E, A](f: Abort[E] ?=> A)(handler: PartialFunction[E, A]^)(using Rollback, Abort[E]): A =
    doRecoverSome(keepLog = true)(f)(handler)

  private def doRecover[E, A](keepLog: Boolean)(f: Abort[E] ?=> A)(handler: E => A)(using rollback: Rollback): A = {
    val restore = rollback.checkpoint
    Abort(f) match {
      case Right(value) => value
      case Left(e)      =>
        restore(keepLog)
        handler(e)
    }
  }

  private def doRecoverSome[E, A](
    keepLog: Boolean
  )(f: Abort[E] ?=> A)(handler: PartialFunction[E, A]^)(using rollback: Rollback, abort: Abort[E]): A = {
    val restore = rollback.checkpoint
    Abort(f) match {
      case Right(value) => value
      case Left(e)      =>
        val restoreFailed = rollback.checkpoint
        restore(keepLog)
        handler.applyOrElse(
          e,
          (unmatched: E) => {
            restoreFailed(false)
            abort.fail(unmatched)
          }
        )
    }
  }
}
