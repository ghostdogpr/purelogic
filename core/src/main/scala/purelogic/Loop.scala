package purelogic

/**
  * Structured looping with explicit control flow via return values.
  *
  * The body must return either `done(a)` to exit the loop with a result, or `continue` / `continue(a)` to proceed to
  * the next iteration. Every code path must explicitly signal one or the other.
  */
object Loop {

  sealed trait Action[+A]
  private case object Continue                  extends Action[Nothing]
  private case class ContinueWith[+A](value: A) extends Action[A]
  private case class Done[+A](value: A)         extends Action[A]

  /**
    * Signals that the loop should exit with the given value.
    */
  def done[A](a: A): Action[A] = Done(a)

  /**
    * Signals that the loop should proceed to the next iteration.
    */
  def continue: Action[Nothing] = Continue

  /**
    * Signals that the loop should proceed to the next iteration with the given accumulator value. Used with `iterate`
    * and `fold`.
    */
  def continue[A](a: A): Action[A] = ContinueWith(a)

  /**
    * Runs a loop that repeats the body until `done` is returned.
    */
  def apply[A](body: => Action[A]): A = {
    var finished  = false
    var result: A = null.asInstanceOf[A]
    while (!finished)
      body match {
        case Done(a)                    => result = a; finished = true
        case Continue | ContinueWith(_) => ()
      }
    result
  }

  /**
    * Runs a loop with an accumulator. The body receives the current value and returns `done(a)` to exit or
    * `continue(nextValue)` to iterate.
    */
  def iterate[A](initial: A)(body: A => Action[A]): A = {
    var acc      = initial
    var finished = false
    while (!finished)
      body(acc) match {
        case Done(a)         => acc = a; finished = true
        case ContinueWith(a) => acc = a
        case Continue        => ()
      }
    acc
  }

  /**
    * Iterates over elements. The body returns `done(())` to exit early or `continue` to proceed.
    */
  def foreach[A](elems: IterableOnce[A])(body: A => Action[Unit]): Unit = {
    val it       = elems.iterator
    var finished = false
    while (!finished && it.hasNext)
      body(it.next()) match {
        case Done(_)                    => finished = true
        case Continue | ContinueWith(_) => ()
      }
  }

  /**
    * Folds over elements with an accumulator. The body returns `done(s)` to exit early or `continue(nextAcc)` to
    * proceed.
    */
  def fold[S, A](elems: IterableOnce[A])(initial: S)(body: (S, A) => Action[S]): S = {
    val it       = elems.iterator
    var acc      = initial
    var finished = false
    while (!finished && it.hasNext)
      body(acc, it.next()) match {
        case Done(s)         => acc = s; finished = true
        case ContinueWith(s) => acc = s
        case Continue        => ()
      }
    acc
  }
}
