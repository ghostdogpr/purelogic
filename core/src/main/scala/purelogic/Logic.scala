package purelogic

import scala.util.boundary

object Logic {

  /**
    * Run a computation with all four capabilities.
    */
  def run[R, W, S, E, A](state: S, reader: R)(
    f: (Reader[R], Writer[W], State[S], Raise[E]) ?=> A
  ): (S, Vector[W], Either[E, A]) = {
    val (stateImpl, getState) = State.makeState(state)
    val (writerImpl, getLogs) = Writer.makeWriter[W]

    given Reader[R] = Reader.make(reader)

    given Writer[W] = writerImpl
    given State[S]  = stateImpl

    val result: Either[E, A] = boundary[Either[E, A]] {
      given Raise[E] = Raise.makeRaise[E]
      Right(f)
    }

    (getState(), getLogs(), result)
  }

  /**
    * Run with only Reader.
    */
  def runReader[R, A](reader: R)(
    f: Reader[R] ?=> A
  ): A = {
    given Reader[R] = Reader.make(reader)
    f
  }

  /**
    * Run with only State.
    */
  def runState[S, A](state: S)(
    f: State[S] ?=> A
  ): (S, A) = {
    val (stateImpl, getState) = State.makeState(state)
    given State[S]            = stateImpl
    val a                     = f
    (getState(), a)
  }

  /**
    * Run with only Raise (returns Either).
    */
  def runEither[E, A](
    f: Raise[E] ?=> A
  ): Either[E, A] =
    boundary[Either[E, A]] {
      given Raise[E] = Raise.makeRaise[E]
      Right(f)
    }
}
