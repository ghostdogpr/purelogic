package purelogic

export Abort.*
export Reader.*
export State.*
export Writer.*

extension [A](opt: Option[A]) {
  inline def orFail[E](error: => E)(using Abort[E]): A =
    extractOption(opt, error)
}

extension [E, A](either: Either[E, A]) {
  inline def orFail(using Abort[E]): A =
    extractEither(either)
}

extension [A](t: scala.util.Try[A]) {
  inline def orFail(using Abort[Throwable]): A =
    extractTry(t)
}
