package purelogic

object Logic {

  def run[R, W, S, E, A](state: S, reader: R)(f: Logic[R, W, S, E, A]): (Vector[W], Either[E, (S, A)]) =
    Reader(reader)(Writer(Abort(State(state)(f))))

  def runInfallible[R, W, S, A](state: S, reader: R)(f: (Reader[R], Writer[W], State[S]) ?=> A): (Vector[W], S, A) = {
    val (log, (newState, a)) = Reader(reader)(Writer(State(state)(f)))
    (log, newState, a)
  }

  def simulateWith[W, S, R, E, A](mockState: S, mockEnv: R)(f: Logic[R, W, S, E, A])(using Abort[E]): A = {
    val (_, either) = run(mockState, mockEnv)(f)
    either match {
      case Left(cause)       => fail(cause)
      case Right((_, value)) => value
    }
  }

  def simulate[W, S, R, E, A](f: Logic[R, W, S, E, A])(using Reader[R], State[S], Abort[E]): A =
    simulateWith(get, read)(f)
}
