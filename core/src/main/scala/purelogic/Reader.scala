package purelogic

trait Reader[+R] {
  def read: R
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

  def read[R](using r: Reader[R]): R               = r.read
  def read[R, A](using r: Reader[R])(f: R => A): A = f(r.read)
}
