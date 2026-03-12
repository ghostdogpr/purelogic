package purelogic

object syntax {
  extension [A](opt: Option[A]) {
    inline def orFail[E](error: => E)(using Abort[E]): A =
      Abort.extractOption(opt, error)
  }

  extension [E, A](either: Either[E, A]) {
    inline def orFail(using Abort[E]): A =
      Abort.extractEither(either)
  }

  extension [A](t: scala.util.Try[A]) {
    inline def orFail(using Abort[Throwable]): A =
      Abort.extractTry(t)
  }
}
