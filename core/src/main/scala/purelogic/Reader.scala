package purelogic

/**
  * Read-only access to a value of type `R`.
  *
  * Typically used for configuration, environment, or any context that your logic needs to read but never modify.
  *
  * @tparam R
  *   the type of the value to read
  */
trait Reader[+R] extends scala.caps.SharedCapability {

  /**
    * Returns the current reader value.
    */
  def read: R

  /**
    * Applies a projection function to the reader value and returns the result.
    */
  def read[A](f: R -> A): A = f(read)

  /**
    * Runs a block with a modified reader value. The original value is restored after the block completes.
    */
  def local[A, R1 >: R](f: R1 -> R1)(body: Reader[R1] ?=> A): A = Reader(f(read))(body)

  /**
    * Runs a block with a narrowed reader derived by applying `f` to the current value.
    */
  def focus[A, B, R1 >: R](f: R1 -> A)(body: Reader[A] ?=> B): B = Reader(f(read))(body)
}

object Reader {

  /**
    * Provides a `Reader[R]` with the given value and runs the body, returning the result directly.
    */
  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    val reader = new Reader[R] {
      def read: R = value
    }
    body(using reader)
  }

  /**
    * Default `Reader[Unit]` instance that always returns `()`.
    */
  given [R <: Unit]: Reader[R] = new Reader[R] {
    def read: R = ().asInstanceOf[R]
  }

  /**
    * Returns the current reader value.
    */
  inline def read[R](using r: Reader[R]): R = r.read

  /**
    * Applies a projection function to the reader value and returns the result.
    */
  inline def read[R, A](using r: Reader[R])(f: R -> A): A = r.read(f)

  /**
    * Runs a block with a modified reader value. The original value is restored after the block completes.
    */
  inline def local[R, A](using r: Reader[R])(f: R -> R)(body: Reader[R] ?=> A): A = r.local(f)(body)

  /**
    * Runs a block with a narrowed reader derived by applying `f` to the current value.
    */
  inline def focus[R, A, B](using r: Reader[R])(f: R -> A)(body: Reader[A] ?=> B): B = r.focus(f)(body)
}
