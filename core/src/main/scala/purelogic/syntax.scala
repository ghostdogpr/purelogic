package purelogic

/**
  * Extension methods for using `Abort` with standard library types.
  */
object syntax {
  extension [A](opt: Option[A]) {

    /**
      * Extracts the value from this `Option`, or fails with the given error.
      */
    inline def orFail[E](error: => E)(using Abort[E]): A =
      Abort.extractOption(opt, error)
  }

  extension [E, A](either: Either[E, A]) {

    /**
      * Extracts the `Right` value from this `Either`, or fails with the `Left`.
      */
    inline def orFail(using Abort[E]): A =
      Abort.extractEither(either)
  }

  extension [A](t: scala.util.Try[A]) {

    /**
      * Extracts the value from this `Try`, or fails with the `Throwable`. Requires `Abort[Throwable]`.
      */
    inline def orFail(using Abort[Throwable]): A =
      Abort.extractTry(t)
  }

  extension [A](a: Iterable[A]) {

    /**
      * Applies a validation function to each element, accumulating all errors instead of short-circuiting on the first
      * failure. If any elements fail, aborts with a non-empty list of all collected errors.
      */
    inline def validateAll[E](f: A => Abort[::[E]] ?=> Any)(using abort: Abort[::[E]]): Unit =
      Abort.validate(a.map(f).toSeq*)
  }
}
