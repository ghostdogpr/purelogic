package purelogic

/**
  * Convenience functions for running programs that use all 4 capabilities.
  */
object Logic {

  /**
    * Runs a program with all 4 capabilities (`Reader`, `Writer`, `State`, `Abort`) and returns the accumulated writes
    * and the result.
    *
    * Equivalent to `Reader(reader)(Writer(Abort(State(state)(f))))`.
    */
  def run[R, W, S, E, A](state: S, reader: R)(f: Logic[R, W, S, E, A]): (Vector[W], Either[E, (S, A)]) =
    Reader(reader)(Writer(Abort(State(state)(f))))

  /**
    * Runs a program that uses `Reader`, `Writer`, and `State` but cannot fail. Avoids the `Either` wrapper in the
    * result.
    */
  def runInfallible[R, W, S, A](state: S, reader: R)(f: (Reader[R], Writer[W], State[S]) ?=> A): (Vector[W], S, A) = {
    val (log, (newState, a)) = Reader(reader)(Writer(State(state)(f)))
    (log, newState, a)
  }

  /**
    * Runs a sub-program in isolation with the given mock state and reader, without impacting the outer state or writes.
    * Errors are propagated to the outer program via `Abort`.
    */
  def simulateWith[W, S, R, E, A](mockState: S, mockEnv: R)(f: Logic[R, W, S, E, A])(using Abort[E]): A = {
    val (_, either) = run(mockState, mockEnv)(f)
    either match {
      case Left(cause)       => fail(cause)
      case Right((_, value)) => value
    }
  }

  /**
    * Runs a sub-program in isolation, reusing the outer `Reader` value.
    */
  def simulateWith[W, S, R, E, A](mockState: S)(f: Logic[R, W, S, E, A])(using Reader[R], Abort[E]): A =
    simulateWith(mockState, read)(f)

  /**
    * Runs a sub-program in isolation, reusing the outer `Reader` and `StateReader` values.
    */
  def simulate[W, S, R, E, A](f: Logic[R, W, S, E, A])(using Reader[R], StateReader[S], Abort[E]): A =
    simulateWith(get, read)(f)
}
