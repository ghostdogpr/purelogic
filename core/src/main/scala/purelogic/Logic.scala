package purelogic

object Logic {

  /**
    * Run a computation with all four capabilities.
    */
  def run[R, W, S, E, A](state: S, reader: R)(
    f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A
  ): (S, Vector[W], Either[E, A]) = {
    val (log, (newState, res)) = Reader(reader)(Writer(State(state)(Raise(f))))
    (newState, log, res)
  }

  def simulateWith[W, S, R, E, A](using Raise[E])(mockState: S, mockEnv: R)(f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A): A = {
    val (_, _, either) = run(mockState, mockEnv)(f)
    either match {
      case Left(cause)  => raise(cause)
      case Right(value) => value
    }
  }

  def simulate[W, S, R, E, A](using State[S], Reader[R], Raise[E])(f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A): A =
    simulateWith(get, ask)(f)

  /**
    * Run with only Reader.
    */
  def runReader[R, A](reader: R)(f: Reader[R] ?=> A): A =
    Reader(reader)(f)

  /**
    * Run with only State.
    */
  def runState[S, A](state: S)(f: State[S] ?=> A): (S, A) =
    State(state)(f)

  /**
    * Run with only Writer.
    */
  def runWriter[W, A](f: Writer[W] ?=> A): (Vector[W], A) =
    Writer(f)

  /**
    * Run with only Raise (returns Either).
    */
  def runEither[E, A](f: Raise[E] ?=> A): Either[E, A] =
    Raise(f)
}
