package purelogic

trait Reader[+R] {
  def read: R
  def read[A](f: R => A): A = f(read)
}

object Reader {
  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def read: R = value
    }
    body
  }

  given Reader[Unit] = new Reader[Unit] {
    def read: Unit = ()
  }

  inline def read[R](using r: Reader[R]): R               = r.read
  inline def read[R, A](using r: Reader[R])(f: R => A): A = r.read(f)
}
