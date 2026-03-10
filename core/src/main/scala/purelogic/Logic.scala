package purelogic

object Logic {

  def run[R, W, S, E, A](state: S, reader: R)(f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A): (Vector[W], Either[E, (S, A)]) =
    Reader(reader)(Writer(Raise(State(state)(f))))

  def runInfallible[R, W, S, A](state: S, reader: R)(f: (Reader[R], Writer[W], State[S]) ?=> A): (Vector[W], S, A) = {
    val (log, (newState, a)) = Reader(reader)(Writer(State(state)(f)))
    (log, newState, a)
  }

  def simulateWith[W, S, R, E, A](using Raise[E])(mockState: S, mockEnv: R)(f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A): A = {
    val (_, either) = run(mockState, mockEnv)(f)
    either match {
      case Left(cause)       => raise(cause)
      case Right((_, value)) => value
    }
  }

  def simulate[W, S, R, E, A](using State[S], Reader[R], Raise[E])(f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A): A =
    simulateWith(get, ask)(f)
}
